[CmdletBinding()]
param(
    [string]$RestoreFromPath
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "assert-client-stopped.ps1")

$sensitiveExactKeys = @(
    "savedAccounts",
    "token-id",
    "token-desc",
    "webMapEndpoint",
    "uploadMapTiles",
    "enableLocationTracking",
    "liveLocationName",
    "cookBookEndpoint",
    "cookBookToken"
)
$sensitivePrefixes = @(
    "savedtoken-",
    "lasttoken-",
    "saved-tokens@",
    "loginname@",
    "tokenname@"
)

function Test-SensitiveKey {
    param([string]$Name)

    if ($Name -in $sensitiveExactKeys) {
        return $true
    }
    foreach ($prefix in $sensitivePrefixes) {
        if ($Name.StartsWith($prefix, [System.StringComparison]::Ordinal)) {
            return $true
        }
    }
    return $false
}

$preferencesPath = Join-Path $env:APPDATA "Haven and Hearth\MoonFlower-prefs.xml"
$sourcePreferencesPath = $preferencesPath
if (-not [string]::IsNullOrWhiteSpace($RestoreFromPath)) {
    $sourcePreferencesPath = [System.IO.Path]::GetFullPath($RestoreFromPath)
    if (-not (Test-Path -LiteralPath $sourcePreferencesPath -PathType Leaf)) {
        throw "Preference restore source does not exist: $sourcePreferencesPath"
    }
}
if (Test-Path -LiteralPath $sourcePreferencesPath -PathType Leaf) {
    & java (Join-Path $PSScriptRoot "PreferenceSanitizer.java") $sourcePreferencesPath $preferencesPath
    if ($LASTEXITCODE -ne 0) {
        throw "Java preference sanitization failed with exit code $LASTEXITCODE."
    }
}

$removedRegistryValues = 0
$registryRoot = "HKCU:\Software\JavaSoft\Prefs\haven"
if (Test-Path -LiteralPath $registryRoot) {
    $moonFlowerNodes = @(Get-ChildItem -LiteralPath $registryRoot -Recurse -ErrorAction SilentlyContinue | Where-Object {
        $_.Name.EndsWith("hafen-/Moon/Flower", [System.StringComparison]::OrdinalIgnoreCase)
    })
    foreach ($node in $moonFlowerNodes) {
        foreach ($valueName in $node.GetValueNames()) {
            if (Test-SensitiveKey $valueName) {
                $node.DeleteValue($valueName, $false)
                $removedRegistryValues++
            }
        }
    }
}

Write-Host "MoonFlower sensitive preference cleanup completed."
Write-Host "Removed legacy registry values: $removedRegistryValues"
