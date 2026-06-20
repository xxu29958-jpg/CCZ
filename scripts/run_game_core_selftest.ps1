param(
    [string]$KotlinVersion = "2.2.21"
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ModuleDir = Join-Path $RepoRoot "android\game-core"
$ToolsDir = Join-Path $RepoRoot ".tools"
$CompilerRoot = Join-Path $ToolsDir "kotlin-compiler-$KotlinVersion"
$CompilerBat = Join-Path $CompilerRoot "kotlinc\bin\kotlinc.bat"
$JarPath = Join-Path $ToolsDir "ccz-game-core-selftest.jar"

if (-not (Test-Path -LiteralPath $CompilerBat)) {
    New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
    $ZipPath = Join-Path $ToolsDir "kotlin-compiler-$KotlinVersion.zip"
    $Url = "https://github.com/JetBrains/kotlin/releases/download/v$KotlinVersion/kotlin-compiler-$KotlinVersion.zip"
    Write-Host "Downloading Kotlin compiler $KotlinVersion..."
    Invoke-WebRequest -Uri $Url -OutFile $ZipPath
    if (Test-Path -LiteralPath $CompilerRoot) {
        Remove-Item -LiteralPath $CompilerRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $CompilerRoot | Out-Null
    Expand-Archive -LiteralPath $ZipPath -DestinationPath $CompilerRoot -Force
}

$Sources = Get-ChildItem -LiteralPath (Join-Path $ModuleDir "src\main\kotlin") -Filter "*.kt" -Recurse |
    Sort-Object FullName |
    ForEach-Object { $_.FullName }

if ($Sources.Count -eq 0) {
    throw "No Kotlin sources found under $ModuleDir"
}

& $CompilerBat @Sources -include-runtime -d $JarPath
if ($LASTEXITCODE -ne 0) {
    throw "Kotlin compilation failed with exit code $LASTEXITCODE"
}

& java -cp $JarPath com.ccz.core.selftest.SelfTestKt
if ($LASTEXITCODE -ne 0) {
    throw "Self-test failed with exit code $LASTEXITCODE"
}
