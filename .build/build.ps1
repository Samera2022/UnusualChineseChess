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

switch ($Type) {
    "jar" {
        Copy-Item "$PROJECT_ROOT\target\${BaseName}.jar" "$OUT_DIR\$FullName.jar"
    }

    "zip" {
        jpackage --type app-image --name $BaseName --app-version $Version `
                 --vendor $Vendor --description $Description --icon "$IconPath" `
                 --input "$PROJECT_ROOT\target" --main-jar "${BaseName}.jar" `
                 --runtime-image "custom-jre" --dest "output/temp_zip"
        Compress-Archive -Path "output/temp_zip/$BaseName" -DestinationPath "$OUT_DIR\$FullName.zip" -Force
    }

    "exe" {
        Write-Host "[DEBUG] --- Environment Info ---" -ForegroundColor Gray
        Write-Host "Current Directory: $(Get-Location)" -ForegroundColor Gray

        $CLEAN_INPUT = New-Item -ItemType Directory -Path "$PROJECT_ROOT\target\dist" -Force
        Copy-Item "$PROJECT_ROOT\target\${BaseName}.jar" $CLEAN_INPUT

        Write-Host "[EXE] Step 1: Generating app-image..." -ForegroundColor Cyan
        jpackage --type app-image --name $BaseName --app-version $Version `
                 --vendor $Vendor --description $Description --copyright $Copyright --icon "$IconPath" `
                 --input "$PROJECT_ROOT\target\dist" --main-jar "${BaseName}.jar" `
                 --runtime-image "custom-jre" --dest "output/temp_exe"

        Write-Host "[DEBUG] --- File System Tree (target/dist) ---" -ForegroundColor Yellow
        tree "$PROJECT_ROOT\target\dist" /f

        Write-Host "[DEBUG] --- File System Tree (App-Image Output) ---" -ForegroundColor Yellow
        # 这里会列出 jpackage 生成的所有文件，方便排查 .jpackage.xml 到底在哪
        tree "output/temp_exe" /f
        # --- 【打印结束】 ---

        $EVB_CONSOLE = "C:\Program Files (x86)\Enigma Virtual Box\enigmavbconsole.exe"

        if (Test-Path $EVB_CONSOLE) {
            Write-Host "[EXE] Step 2: Packing single executable..." -ForegroundColor Cyan

            Push-Location "$PROJECT_ROOT\.build"

            & $EVB_CONSOLE "$PROJECT_ROOT\.build\evb_settings.evb"

            Pop-Location

            # 3. 结果处理
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