# Deja todo listo para la demostracion:
#   0. MongoDB en localhost:27017 (si no esta arriba, lo levanta como proceso del usuario)
#   1. Servidor GraphQL (Java + Spring Data MongoDB) en el puerto 8081
#   2. Cliente web (React) en http://localhost:5173 y lo abre en el navegador
#   3. App Flutter en el celular conectado por USB (adb reverse + instalar + abrir)
#   4. Ventana con los logs del servidor en vivo (operaciones de la web y la app)
#
# Se puede ejecutar varias veces: lo que ya esta corriendo no se vuelve a lanzar.
# Si se desconecta el cable del celular, basta con volver a ejecutarlo.
# Compatible con Windows PowerShell 5.1 (solo ASCII a proposito, por la codificacion).

$ErrorActionPreference = 'Continue'

$raiz     = Split-Path -Parent $PSScriptRoot
$servidor = Join-Path $raiz 'servidor-libreria-mongo'
$web      = Join-Path $raiz 'cliente-web-react'
$movil    = Join-Path $raiz 'cliente-movil-flutter'
$jar      = Join-Path $servidor 'target\ServidorLibreriaMongo-3.0.0.jar'
$apk      = Join-Path $movil 'build\app\outputs\flutter-apk\app-release.apk'
$paquete  = 'com.libreria.cliente_movil_libreria'
$urlGraphQL = 'http://localhost:8081/graphql'
$urlWeb     = 'http://localhost:5173'

function Paso($texto)  { Write-Host ''; Write-Host "==> $texto" -ForegroundColor Cyan }
function Bien($texto)  { Write-Host "    OK  $texto" -ForegroundColor Green }
function Aviso($texto) { Write-Host "    !!  $texto" -ForegroundColor Yellow }
function Falla($texto) { Write-Host "    XX  $texto" -ForegroundColor Red }

# Prueba directa de conexion al puerto (Get-NetTCPConnection falla en algunos equipos).
# Se prueban IPv4 e IPv6 porque Vite puede escuchar solo en ::1.
function Escuchando($puerto) {
    foreach ($direccion in @('127.0.0.1', '::1')) {
        $familia = if ($direccion -eq '::1') { 'InterNetworkV6' } else { 'InterNetwork' }
        $cliente = New-Object System.Net.Sockets.TcpClient($familia)
        try {
            $intento = $cliente.BeginConnect($direccion, $puerto, $null, $null)
            if ($intento.AsyncWaitHandle.WaitOne(400) -and $cliente.Connected) { return $true }
        } catch { } finally { $cliente.Close() }
    }
    return $false
}

function ServidorResponde {
    try {
        $r = Invoke-RestMethod -Uri $urlGraphQL -Method Post -ContentType 'application/json' `
            -Body '{"query":"{ revisionLibros }"}' -TimeoutSec 3
        return $null -ne $r.data
    } catch { return $false }
}

function Esperar($descripcion, $segundos, [scriptblock]$condicion) {
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        if (& $condicion) { return $true }
        Start-Sleep -Milliseconds 700
    }
    Falla "$descripcion no respondio en $segundos s"
    return $false
}

# --------------------------------------------------------------------------------------------
Paso 'MongoDB (localhost:27017)'

if (Escuchando 27017) {
    Bien 'ya estaba corriendo'
} else {
    $servicio = Get-Service -Name 'MongoDB' -ErrorAction SilentlyContinue
    if ($servicio) {
        try { Start-Service 'MongoDB' -ErrorAction Stop } catch { Aviso 'el servicio MongoDB pide permisos de administrador; se usa un proceso del usuario' }
    }
    if (-not (Esperar 'MongoDB (servicio)' 5 { Escuchando 27017 })) {
        $mongod = (Get-Command mongod -ErrorAction SilentlyContinue).Source
        if (-not $mongod) {
            $mongod = Get-ChildItem 'C:\Program Files\MongoDB\Server\*\bin\mongod.exe' -ErrorAction SilentlyContinue |
                Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
        }
        if (-not $mongod) {
            Falla 'no se encontro mongod.exe: instalar con  winget install MongoDB.Server'
            exit 1
        }
        # Carpeta de datos: LIBRERIA_DATOS_MONGO, la de D:\01-Desarrollo si existe, o %LOCALAPPDATA%.
        $datos = $env:LIBRERIA_DATOS_MONGO
        if (-not $datos) {
            $junto = Join-Path (Split-Path -Parent $raiz) 'Bases-de-Datos\mongo-tercer-prototipo'
            if (Test-Path $junto) { $datos = $junto } else { $datos = Join-Path $env:LOCALAPPDATA 'TercerPrototipo-Libreria\mongo' }
        }
        New-Item -ItemType Directory -Force (Join-Path $datos 'db'), (Join-Path $datos 'log') | Out-Null
        Start-Process -FilePath $mongod -WindowStyle Hidden -ArgumentList @(
            '--dbpath', "`"$(Join-Path $datos 'db')`"", '--logpath', "`"$(Join-Path $datos 'log\mongod.log')`"",
            '--logappend', '--bind_ip', '127.0.0.1', '--port', '27017')
        if (Esperar 'MongoDB' 30 { Escuchando 27017 }) { Bien "listo (datos en $datos)" } else { exit 1 }
    } else {
        Bien 'servicio MongoDB iniciado'
    }
}

# --------------------------------------------------------------------------------------------
Paso 'Servidor GraphQL (Java + MongoDB, puerto 8081)'

$java = 'java'
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($jdk) { $java = Join-Path $jdk.FullName 'bin\java.exe' }
}

if (ServidorResponde) {
    Bien 'ya estaba corriendo'
} else {
    if (-not (Test-Path $jar)) {
        Aviso 'no existe el .jar, compilando el servidor (1-2 min)...'
        Push-Location $servidor
        & .\mvnw.cmd -q -DskipTests package
        Pop-Location
    }
    # chcp 65001 + UTF-8 en Java: los logs con tildes se ven bien en la consola.
    Start-Process cmd -WorkingDirectory $servidor `
        -ArgumentList "/k chcp 65001 >nul && title Servidor GraphQL - puerto 8081 && `"$java`" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -jar `"$jar`""
    if (Esperar 'El servidor' 90 { ServidorResponde }) { Bien "listo en $urlGraphQL" }
}

# --------------------------------------------------------------------------------------------
Paso 'Logs del servidor (en vivo)'

$visorLogs = Join-Path $PSScriptRoot 'ver-logs.ps1'
try {
    Start-Process powershell.exe -WorkingDirectory $servidor -ErrorAction Stop `
        -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$visorLogs`""
    Bien 'ventana Logs Libreria abierta (web y app)'
} catch {
    Aviso "no se pudo abrir el visor de logs: $($_.Exception.Message)"
}

# --------------------------------------------------------------------------------------------
Paso 'MongoDB Compass (ver las colecciones)'

$uriMongo = 'mongodb://localhost:27017/libreria_evaluacion'
$compass = @(
    (Join-Path $env:LOCALAPPDATA 'MongoDBCompass\MongoDBCompass.exe'),
    'C:\Program Files\MongoDB Compass\MongoDBCompass.exe'
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $compass) {
    Aviso 'MongoDB Compass no esta instalado (opcional): winget install MongoDB.Compass.Full'
} elseif (Get-Process MongoDBCompass -ErrorAction SilentlyContinue) {
    Bien 'Compass ya estaba abierto'
} else {
    # Compass recibe la cadena de conexion y se conecta solo a la base de la entrega.
    Start-Process -FilePath $compass -ArgumentList $uriMongo
    Bien "Compass abierto y conectado a $uriMongo"
}

# --------------------------------------------------------------------------------------------
Paso 'Cliente web (React, puerto 5173)'

if (Escuchando 5173) {
    Bien 'ya estaba corriendo'
} else {
    if (-not (Test-Path (Join-Path $web 'node_modules'))) {
        Aviso 'instalando dependencias de npm (solo la primera vez)...'
        Push-Location $web
        & npm install
        Pop-Location
    }
    Start-Process cmd -WorkingDirectory $web -ArgumentList '/k title Cliente web React - puerto 5173 && npm run dev'
    if (Esperar 'El cliente web' 60 { Escuchando 5173 }) { Bien "listo en $urlWeb" }
}
Start-Process $urlWeb

# --------------------------------------------------------------------------------------------
Paso 'Celular por USB (app Flutter)'

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    foreach ($c in @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", 'C:\Android\platform-tools\adb.exe')) {
        if (Test-Path $c) { $adb = $c; break }
    }
}

if (-not $adb) {
    Falla 'no se encontro adb (Android platform-tools)'
} else {
    if (-not (Test-Path $apk)) {
        Aviso 'no existe el APK, compilandolo (unos minutos)...'
        Push-Location $movil
        & flutter build apk --release --dart-define=URL_GRAPHQL=http://localhost:8081/graphql
        Pop-Location
    }
    & $adb start-server 2>$null | Out-Null

    $estadoAnterior = ''
    while ($true) {
        $lineas = & $adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() }
        if ($lineas | Where-Object { $_ -match '\tdevice$' }) { break }

        if ($lineas | Where-Object { $_ -match '\tunauthorized$' }) { $estado = 'sin-permiso' } else { $estado = 'sin-celular' }
        if ($estado -ne $estadoAnterior) {
            if ($estado -eq 'sin-permiso') {
                Aviso 'en el celular acepta "Permitir depuracion USB" (marca "Permitir siempre")'
            } else {
                Aviso 'esperando el celular: conectalo por USB con la depuracion USB activada'
                Aviso '(Ctrl+C para terminar sin el celular; el servidor y la web siguen corriendo)'
            }
            $estadoAnterior = $estado
        }
        Start-Sleep -Seconds 2
    }
    Bien 'celular detectado'

    # El celular llega al servidor del computador por el cable: localhost:8081 del celular -> 8081 del PC
    & $adb reverse tcp:8081 tcp:8081 | Out-Null
    Bien 'adb reverse tcp:8081 -> servidor del computador'

    $salida = & $adb install -r $apk 2>&1 | Out-String
    if ($salida -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Aviso 'habia otra version firmada distinta; se reinstala'
        & $adb uninstall $paquete | Out-Null
        $salida = & $adb install -r $apk 2>&1 | Out-String
    }
    if ($salida -match 'Success') { Bien 'app instalada/actualizada' } else { Falla "no se pudo instalar: $salida" }

    & $adb shell am force-stop $paquete | Out-Null
    & $adb shell am start -n "$paquete/.MainActivity" | Out-Null
    Bien 'app abierta en el celular'
}

# --------------------------------------------------------------------------------------------
Write-Host ''
Write-Host '======================================================================' -ForegroundColor Cyan
Write-Host ' Todo listo para la demostracion' -ForegroundColor Cyan
Write-Host "   Servidor:  $urlGraphQL   (consola: http://localhost:8081/graphiql)"
Write-Host "   Web:       $urlWeb"
Write-Host '   Celular:   app "Libreria" (si se desconecta el cable, ejecutar de nuevo)'
Write-Host '   MongoDB:   localhost:27017, base "libreria_evaluacion" (colecciones libros y ejemplares)'
Write-Host '   Compass:   mongodb://localhost:27017/libreria_evaluacion (libros y ejemplares)'
Write-Host '   Logs:      ventana "Logs Libreria - web y app" (actualizacion en vivo)'
Write-Host ' Para apagar: cerrar las ventanas "Servidor GraphQL" y "Cliente web React".'
Write-Host '======================================================================' -ForegroundColor Cyan
