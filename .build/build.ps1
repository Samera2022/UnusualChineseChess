Param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("exe", "jar", "zip")]
    [string]$Type
)

$PROJECT_ROOT = $env:GITHUB_WORKSPACE
Set-Location $PROJECT_ROOT

$props = Get-Content ".build/properties.json" | ConvertFrom-Json
$BaseName    = $props.name
$Version     = $props.'app-version'
$Vendor      = $props.vendor
$Description = $props.description
$Copyright   = $props.copyright

$FullName = "$BaseName-$Version"
$OUT_DIR = New-Item -ItemType Directory -Path "$PROJECT_ROOT\output" -Force
$IconPath = "$PROJECT_ROOT\.build\${BaseName}.ico"

if (-not (Test-Path "custom-jre")) {
    Write-Host "[BUILD] Generating custom-jre..." -ForegroundColor Cyan
    jlink --module-path "$env:JAVA_HOME\jmods" `
          --add-modules java.base,java.compiler,java.desktop,java.sql `
          --output custom-jre `
          --strip-debug --compress 2 --no-header-files --no-man-pages
}

# 4. 执行分支逻辑
switch ($Type) {
    "jar" {
        Copy-Item "target/${BaseName}_jar/${BaseName}.jar" "$OUT_DIR\$FullName.jar"
    }

    "zip" {
        jpackage --type app-image --name $BaseName --app-version $Version `
                 --vendor $Vendor --description $Description --icon "$IconPath" `
                 --input "target/${BaseName}_jar" --main-jar "${BaseName}.jar" `
                 --runtime-image "custom-jre" --dest "output/temp_zip"
        Compress-Archive -Path "output/temp_zip/$BaseName" -DestinationPath "$OUT_DIR\$FullName.zip" -Force
    }

    "exe" {
        Write-Host "[DEBUG] --- Environment Info ---" -ForegroundColor Gray
        Write-Host "Current Directory: $(Get-Location)" -ForegroundColor Gray

        # 1. 准备 app-image 环境
        Write-Host "[EXE] Step 1: Generating app-image..." -ForegroundColor Cyan
        jpackage --type app-image --name $BaseName --app-version $Version `
                 --vendor $Vendor --description $Description --copyright $Copyright --icon "$IconPath" `
                 --input "target/${BaseName}_jar" --main-jar "${BaseName}.jar" `
                 --runtime-image "custom-jre" --dest "output/temp_exe"

        # --- 【硬核调试开始】 ---
        Write-Host "[DEBUG] --- File System Verification ---" -ForegroundColor Yellow
        $ExpectedPath = "output\temp_exe\$BaseName"
        if (Test-Path $ExpectedPath) {
            Write-Host "[DEBUG] SUCCESS: App-image folder exists at $ExpectedPath" -ForegroundColor Green
            Write-Host "[DEBUG] Listing top-level content of $ExpectedPath :" -ForegroundColor Gray
            Get-ChildItem $ExpectedPath | Select-Object Name, Mode

            Write-Host "[DEBUG] Checking for specific app subfolder..." -ForegroundColor Gray
            $AppSubFolder = Join-Path $ExpectedPath "app"
            if (Test-Path $AppSubFolder) {
                Get-ChildItem $AppSubFolder | Select-Object Name, Mode
            } else {
                Write-Host "[DEBUG] WARNING: 'app' folder NOT FOUND inside app-image!" -ForegroundColor Red
            }
        } else {
            Write-Host "[DEBUG] CRITICAL ERROR: App-image folder NOT FOUND at $ExpectedPath" -ForegroundColor Red
            Write-Host "[DEBUG] Searching where it might be. Listing 'output' recursively..." -ForegroundColor Gray
            Get-ChildItem "output" -Recurse | Select-Object FullName
        }
        # --- 【硬核调试结束】 ---

        # 2. 准备执行 EVB
        $EVB_CONSOLE = "C:\Program Files (x86)\Enigma Virtual Box\enigmavbconsole.exe"
        $EVB_PROJECT = ".build/evb_settings.evb"

        if (Test-Path $EVB_CONSOLE) {
            Write-Host "[EXE] Step 2: Packing single executable..." -ForegroundColor Cyan
            Write-Host "[DEBUG] Using EVB Project: $EVB_PROJECT" -ForegroundColor Gray

            # 记录执行前的状态
            & $EVB_CONSOLE $EVB_PROJECT

            # 3. 结果处理
            # 尝试多种可能的输出路径进行搜寻
            $PossibleOutput = Get-ChildItem "output\*.exe" | Where-Object { $_.Name -notlike "*$Version*" } | Select-Object -First 1

            if ($null -ne $PossibleOutput) {
                Write-Host "[EXE] Found generated EXE: $($PossibleOutput.Name)" -ForegroundColor Green
                Move-Item $PossibleOutput.FullName "$OUT_DIR\$FullName.exe" -Force
            } else {
                Write-Host "[EXE] ERROR: EVB completed but no output EXE found in 'output\'" -ForegroundColor Red
                exit 1
            }
        } else {
            Write-Host "[EXE] ERROR: EVB Console not found at $EVB_CONSOLE" -ForegroundColor Red
            exit 1
        }
    }
}