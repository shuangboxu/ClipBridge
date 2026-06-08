param(
    [string]$Version = "1.0.0",
    [string]$AppName = "ClipBridge Lite",
    [switch]$SkipBuild,
    [switch]$PortableOnly,
    [ValidateSet("inno", "jpackage")]
    [string]$InstallerType = "inno"
)

$ErrorActionPreference = "Stop"

$wixDownloadUrl = "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"
$mainClass = "com.xushuangbo.clipbridge.windows.AppLauncher"
$runtimeModules = "java.base,java.desktop,java.net.http,java.logging,java.xml,java.prefs,jdk.crypto.ec,jdk.unsupported,jdk.unsupported.desktop"

function Assert-WindowsJavaFxJar {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JarPath
    )

    $jarCmd = (Get-Command jar -ErrorAction Stop).Source
    $entries = & $jarCmd tf $JarPath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to inspect jar entries: $JarPath"
    }

    $hasJavaFx = $entries | Where-Object {
        $_ -like "javafx/*" -or $_ -like "com/sun/glass/*"
    } | Select-Object -First 1
    if (-not $hasJavaFx) {
        Write-Host "JavaFX entries not found in jar, skip Windows native validation."
        return
    }

    $hasWindowsDll = $entries | Where-Object { $_ -match '\.dll$' } | Select-Object -First 1
    if ($hasWindowsDll) {
        return
    }

    $linuxNativeSample = $entries | Where-Object { $_ -match '\.so$' } | Select-Object -First 3
    $sampleHint = ""
    if ($linuxNativeSample) {
        $sampleHint = " Detected non-Windows native files: " + ($linuxNativeSample -join ", ")
    }
    throw "Packable jar does not contain JavaFX Windows native .dll files. Please rebuild on Windows with JavaFX 'win' classifier.$sampleHint"
}

function Ensure-WixTools {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot
    )

    $wixDir = Join-Path $ProjectRoot "tools\\wix"
    $candleExe = Join-Path $wixDir "candle.exe"
    $lightExe = Join-Path $wixDir "light.exe"

    if (-not ((Test-Path $candleExe) -and (Test-Path $lightExe))) {
        Write-Host "WiX not found, downloading local WiX binaries..."
        New-Item -ItemType Directory -Path $wixDir -Force | Out-Null
        $zipPath = Join-Path $ProjectRoot "tools\\wix314-binaries.zip"
        Invoke-WebRequest -Uri $wixDownloadUrl -OutFile $zipPath
        Expand-Archive -Path $zipPath -DestinationPath $wixDir -Force
        Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue
    } else {
        Write-Host "Local WiX already exists."
    }

    # jpackage --type exe requires candle/light
    $env:PATH = "$wixDir;$env:PATH"
}

function Build-InnoInstaller {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScriptDir,
        [Parameter(Mandatory = $true)]
        [string]$AppName,
        [Parameter(Mandatory = $true)]
        [string]$Version,
        [Parameter(Mandatory = $true)]
        [string]$PortableAppDir,
        [Parameter(Mandatory = $true)]
        [string]$IconPath
    )

    $issPath = Join-Path $ScriptDir "installer.iss"
    if (-not (Test-Path $issPath)) {
        throw "Inno Setup script not found: $issPath"
    }

    $isccCmd = (Get-Command ISCC.exe -ErrorAction Stop).Source
    & $isccCmd `
        "/DMyAppName=$AppName" `
        "/DMyAppVersion=$Version" `
        "/DMyAppPublisher=ClipBridge" `
        "/DMyAppImageDir=$PortableAppDir" `
        "/DMySetupIcon=$IconPath" `
        $issPath

    if ($LASTEXITCODE -ne 0) {
        throw "Inno Setup compile failed"
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path

Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        Write-Host "[1/5] Building fat jar..."
        mvn -DskipTests clean package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed. Packaging is stopped to avoid using stale/incompatible jars. Re-run with -SkipBuild only if you intentionally want to package an existing jar."
        }
    } else {
        Write-Host "[1/5] Skip build enabled, using existing jar..."
    }

    $targetDir = Join-Path $projectRoot "target"
    $mainJar = Get-ChildItem -Path $targetDir -Filter "windows-lite-*.jar" |
        Where-Object { $_.Name -notlike "original-*" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $mainJar) {
        throw "Packable jar not found"
    }

    Assert-WindowsJavaFxJar -JarPath $mainJar.FullName

    $iconPath = Join-Path $projectRoot "src/main/resources/icons/icon.ico"
    if (-not (Test-Path $iconPath)) {
        throw "Icon not found: $iconPath"
    }

    $distDir = Join-Path $projectRoot "dist"
    if (Test-Path $distDir) {
        Remove-Item -Path $distDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $distDir | Out-Null

    $packInputDir = Join-Path $targetDir "pack-input"
    if (Test-Path $packInputDir) {
        Remove-Item -Path $packInputDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $packInputDir | Out-Null
    Copy-Item -Path $mainJar.FullName -Destination (Join-Path $packInputDir $mainJar.Name) -Force

    $jpackageCmd = (Get-Command jpackage -ErrorAction Stop).Source
    $commonArgs = @(
        "--name", $AppName,
        "--app-version", $Version,
        "--vendor", "ClipBridge",
        "--description", "ClipBridge Lite for Windows",
        "--input", $packInputDir,
        "--main-jar", $mainJar.Name,
        "--main-class", $mainClass,
        "--icon", $iconPath,
        "--add-modules", $runtimeModules,
        "--jlink-options", "--strip-debug --no-header-files --no-man-pages --compress=zip-6",
        "--java-options", "-Dfile.encoding=UTF-8"
    )

    Write-Host "[2/5] Running jpackage to build app-image..."
    $appImageDest = Join-Path $distDir "app-image"
    if (Test-Path $appImageDest) {
        Remove-Item -Path $appImageDest -Recurse -Force
    }
    New-Item -ItemType Directory -Path $appImageDest | Out-Null

    $appImageArgs = @(
        "--type", "app-image",
        "--dest", $appImageDest
    ) + $commonArgs
    & $jpackageCmd @appImageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage app-image failed"
    }

    $portableAppDir = Join-Path $appImageDest $AppName
    if (-not (Test-Path $portableAppDir)) {
        throw "Portable app-image output not found: $portableAppDir"
    }

    Write-Host "[3/5] Generating portable zip..."
    $portableZip = Join-Path $distDir ("{0}-{1}-portable.zip" -f $AppName, $Version)
    if (Test-Path $portableZip) {
        Remove-Item -Path $portableZip -Force
    }
    Compress-Archive -Path $portableAppDir -DestinationPath $portableZip -CompressionLevel Optimal

    if (-not $PortableOnly) {
        if ($InstallerType -eq "jpackage") {
            Write-Host "[4/5] Building jpackage MSI-based installer (.exe wrapper)..."
            Ensure-WixTools -ProjectRoot $projectRoot
            $installerArgs = @(
                "--type", "exe",
                "--dest", $distDir,
                "--win-dir-chooser",
                "--win-menu",
                "--win-shortcut",
                "--win-per-user-install"
            ) + $commonArgs
            & $jpackageCmd @installerArgs
            if ($LASTEXITCODE -ne 0) {
                throw "jpackage exe failed"
            }
        } else {
            Write-Host "[4/5] Building Inno Setup installer (.exe)..."
            Build-InnoInstaller `
                -ScriptDir $scriptDir `
                -AppName $AppName `
                -Version $Version `
                -PortableAppDir $portableAppDir `
                -IconPath $iconPath
        }
    } else {
        Write-Host "[4/5] PortableOnly enabled, skip installer."
    }

    Write-Host "[5/5] Output directory: $distDir"
    Get-ChildItem -Path $distDir | Select-Object Name, Length, LastWriteTime
    Write-Host "[Done]"
}
finally {
    Pop-Location
}


