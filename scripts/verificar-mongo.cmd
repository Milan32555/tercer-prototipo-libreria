@echo off
rem Verifica las colecciones de MongoDB (ver scripts\VerificarMongo.java). Uso: scripts\verificar-mongo.cmd [base]
rem Usa el driver de MongoDB que Maven ya descargo al compilar el servidor.
setlocal
set M2=%USERPROFILE%\.m2\repository\org\mongodb
set V=5.8.1
set CP=%M2%\mongodb-driver-sync\%V%\mongodb-driver-sync-%V%.jar;%M2%\mongodb-driver-core\%V%\mongodb-driver-core-%V%.jar;%M2%\bson\%V%\bson-%V%.jar;%M2%\bson-record-codec\%V%\bson-record-codec-%V%.jar
set JAVA=java
if defined JAVA_HOME set JAVA="%JAVA_HOME%\bin\java"
%JAVA% -cp "%CP%" "%~dp0VerificarMongo.java" %*
