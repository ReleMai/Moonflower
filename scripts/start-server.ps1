$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
mvn -pl server -am package
java -jar .\server\target\server-0.1.0-SNAPSHOT.jar
