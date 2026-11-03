param(
    [string]$WebProjectPath
)

$ErrorActionPreference = 'Stop'
$androidProject = Split-Path -Parent $MyInvocation.MyCommand.Path
$destination = Join-Path $androidProject 'app\src\main\assets\site'

if ([string]::IsNullOrWhiteSpace($WebProjectPath)) {
    $WebProjectPath = Join-Path (Split-Path -Parent $androidProject) 'Bylot-main\Bylot-main\bylot'
}

if (-not (Test-Path -LiteralPath $WebProjectPath)) {
    throw "The web project was not found at '$WebProjectPath'. Pass its location with -WebProjectPath."
}

$webProject = (Resolve-Path -LiteralPath $WebProjectPath).Path
if (-not (Test-Path -LiteralPath (Join-Path $webProject 'package.json'))) {
    throw "The web project was not found at '$webProject'."
}

$previousBasePath = $env:VITE_BASE_PATH
try {
    $env:VITE_BASE_PATH = '/assets/site/'
    Push-Location $webProject
    try {
        npm run build
        if ($LASTEXITCODE -ne 0) {
            throw "The Bylot web build failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    if ($null -eq $previousBasePath) {
        Remove-Item Env:\VITE_BASE_PATH -ErrorAction SilentlyContinue
    }
    else {
        $env:VITE_BASE_PATH = $previousBasePath
    }
}

$buildOutput = Join-Path $webProject 'dist'
if (-not (Test-Path -LiteralPath (Join-Path $buildOutput 'index.html'))) {
    throw "The web build completed without producing '$buildOutput\index.html'."
}

New-Item -ItemType Directory -Force -Path $destination | Out-Null
Copy-Item -Path (Join-Path $buildOutput '*') -Destination $destination -Recurse -Force

Write-Output "Bundled the Bylot web app from '$webProject' into '$destination'."
