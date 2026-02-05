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
        Copy-Item "out/artifacts/${BaseName}_jar/${BaseName}.jar" "$OUT_DIR\$FullName.jar"
    }

    "zip" {
        jpackage --type app-image --name $BaseName --app-version $Version `
                 --vendor $Vendor --description $Description --icon "$IconPath" `
                 --input "out/artifacts/${BaseName}_jar" --main-jar "${BaseName}.jar" `
                 --runtime-image "custom-jre" --dest "output/temp_zip"
        Compress-Archive -Path "output/temp_zip/$BaseName" -DestinationPath "$OUT_DIR\$FullName.zip" -Force
    }

    "exe" {
        jpackage --type app-image --name $BaseName --app-version $Version `
                 --vendor $Vendor --description $Description --copyright $Copyright --icon "$IconPath" `
                 --input "out/artifacts/${BaseName}_jar" --main-jar "${BaseName}.jar" `
                 --runtime-image "custom-jre" --dest "output/temp_exe"

        $EVB_CONSOLE = "C:\Program Files (x86)\Enigma Virtual Box\enigmavbconsole.exe"
        if (Test-Path $EVB_CONSOLE) {
            Write-Host "[EXE] Packing single executable..." -ForegroundColor Cyan
            & $EVB_CONSOLE ".build/evb_settings.evb"
            if (Test-Path "output/${BaseName}_boxed.exe") {
                Move-Item "output/${BaseName}_boxed.exe" "$OUT_DIR\$FullName.exe" -Force
                Write-Host "[EXE] Success! Final: $FullName.exe" -ForegroundColor Green
            }
        }
    }
}