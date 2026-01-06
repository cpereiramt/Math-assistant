<#
Load variables from a .env file into the process environment, then run Gradle bootRun.
Usage: .\scripts\run-local.ps1 [.env]
#>
param(
    [string]$EnvFile = ".env"
)

if (-not (Test-Path $EnvFile)) {
    Write-Error "$EnvFile not found. Copy .env.example to $EnvFile and fill values."
    exit 1
}

Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -eq 0) { return }
    if ($line.StartsWith('#')) { return }
    if ($line -match '^\s*([^=]+)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        if ($value.StartsWith('"') -and $value.EndsWith('"')) { $value = $value.Trim('"') }
        # Use Set-Item so dynamic env var names work
        Set-Item -Path "Env:$name" -Value $value
        # Debug: print loaded var name; mask sensitive values
        if ($name -match '(?i)password|secret|jwt|token|key') {
            $display = '****'
        }
        else {
            $display = $value
        }
        Write-Host "Loaded $name=$display"
    }
}

Write-Host "Environment loaded from $EnvFile"
& .\gradlew.bat bootRun
