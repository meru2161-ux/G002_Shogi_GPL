param(
    [Parameter(Mandatory = $true)]
    [string]$AndroidNdkRoot
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $projectRoot 'third_party\YaneuraOu'
$ndkBuild = Join-Path $AndroidNdkRoot 'ndk-build.cmd'
$builtBinary = Join-Path $sourceRoot 'build\android\NNUE_KP256\YaneuraOu_NNUE_KP256_arm64-v8a'
$destination = Join-Path $projectRoot 'app\src\main\jniLibs\arm64-v8a\libg002_yaneuraou.so'

if (!(Test-Path -LiteralPath $ndkBuild)) { throw "ndk-build.cmd was not found: $ndkBuild" }
if (!(Test-Path -LiteralPath $sourceRoot)) { throw "YaneuraOu source was not found: $sourceRoot" }

$previousPath = $env:Path
try {
    $env:Path = "$AndroidNdkRoot;$previousPath"
    Push-Location $sourceRoot
    & (Join-Path $sourceRoot 'script\android_build.ps1') -Edition 'YANEURAOU_ENGINE_NNUE_KP256'
} finally {
    Pop-Location
    $env:Path = $previousPath
}

if (!(Test-Path -LiteralPath $builtBinary)) { throw "Expected ARM64 engine was not produced: $builtBinary" }
Copy-Item -LiteralPath $builtBinary -Destination $destination -Force
Write-Host "Updated $destination from fixed GPL source. Verify NOTICE checksums before release."
