# Visor independiente: tambien funciona si el servidor ya estaba corriendo.
# Compatible con Windows PowerShell 5.1 y archivos de log UTF-8.
param(
    [string]$ArchivoLog = (Join-Path (Split-Path -Parent $PSScriptRoot) 'servidor-libreria-mongo\logs\servidor.log')
)

$ErrorActionPreference = 'Stop'

# Una sola ventana de logs aunque se vuelva a ejecutar el lanzador.
$ventanaNueva = $false
$bloqueo = [System.Threading.Mutex]::new($true, 'Local\TercerPrototipoLibreriaLogs', [ref]$ventanaNueva)
if (-not $ventanaNueva) {
    $bloqueo.Dispose()
    exit 0
}

try {
    $Host.UI.RawUI.WindowTitle = 'Logs Libreria - web y app'
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    Write-Host 'Logs Libreria - operaciones de la web y la app' -ForegroundColor Cyan
    Write-Host "Archivo: $ArchivoLog"
    Write-Host 'Puedes cerrar esta ventana al terminar de consultar los logs.'
    Write-Host ''

    if (-not (Test-Path -LiteralPath $ArchivoLog)) {
        Write-Host 'Esperando a que el servidor cree el archivo de logs...' -ForegroundColor Yellow
        while (-not (Test-Path -LiteralPath $ArchivoLog)) { Start-Sleep -Seconds 1 }
    }

    Get-Content -LiteralPath $ArchivoLog -Encoding UTF8 -Tail 40 -Wait
} finally {
    $bloqueo.ReleaseMutex()
    $bloqueo.Dispose()
}
