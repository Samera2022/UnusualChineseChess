Param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("exe", "jar", "zip")]
    [string]$Type
)

$PROJECT_ROOT = $env:GITHUB_WORKSPACE
if ($null -eq $PROJECT_ROOT) { $PROJECT_ROOT = Get-Location } # 兼容本地运行
Set-Location $PROJECT_ROOT

# 1. 读取配置
$props = Get-Content ".build/properties.json" | ConvertFrom-Json
$BaseName    = $props.name
$Version     = $props.'app-version'
$Vendor      = $props.vendor
$Description = $props.description
$Copyright   = $props.copyright

$FullName = "$BaseName-$Version"
$OUT_DIR = New-Item -ItemType Directory -Path "$PROJECT_ROOT\output" -Force
$IconPath = "$PROJECT_ROOT\.build\${BaseName}.ico"
$MAVEN_JAR_DIR = "$PROJECT_ROOT\target"
$JAR_NAME = "${BaseName}.jar"

# 2. 生成 JRE (jlink)
if (-not (Test-Path "custom-jre")) {
    Write-Host "[BUILD] Generating custom-jre..." -ForegroundColor Cyan
    jlink --module-path "$env:JAVA_HOME\jmods" `
          --add-modules java.base,java.compiler,java.desktop,java.sql `
          --output custom-jre `
          --strip-debug --compress 2 --no-header-files --no-man-pages
}

# 3. 执行分支逻辑
switch ($Type) {
    "jar" {
        Copy-Item "$MAVEN_JAR_DIR\$JAR_NAME" "$OUT_DIR\$FullName.jar"
    }

    "zip" {
        $ZIP_TEMP = "output/temp_zip"
        jpackage --type app-image --name $BaseName --app-version $Version `
                 --vendor $Vendor --description $Description --icon "$IconPath" `
                 --input "$MAVEN_JAR_DIR" --main-jar "$JAR_NAME" `
                 --runtime-image "custom-jre" --dest "$ZIP_TEMP"

        # --- 修复 1：压缩包不应包含 .ico ---
        $icoInZip = "$ZIP_TEMP/$BaseName/$BaseName.ico"
        if (Test-Path $icoInZip) {
            Write-Host "[ZIP] Removing icon from app-image..." -ForegroundColor Gray
            Remove-Item $icoInZip -Force
        }

        Compress-Archive -Path "$ZIP_TEMP/$BaseName" -DestinationPath "$OUT_DIR\$FullName.zip" -Force
    }

    "exe" {
        # --- 修复 2：EVB 动态更名逻辑 ---
        Write-Host "[EXE] Step 1: Generating app-image..." -ForegroundColor Cyan
        $EXE_TEMP = "output/temp_exe"
        jpackage --type app-image --name $BaseName --app-version $Version `
                 --vendor $Vendor --description $Description --copyright $Copyright --icon "$IconPath" `
                 --input "$MAVEN_JAR_DIR" --main-jar "$JAR_NAME" `
                 --runtime-image "custom-jre" --dest "$EXE_TEMP"

        # 定义路径
        $APP_IMAGE_ROOT = "$PROJECT_ROOT\$EXE_TEMP\$BaseName"
        $APP_DIR = "$APP_IMAGE_ROOT\app"
        $originalCfg = "$APP_DIR\$BaseName.cfg"
        $newCfgName = "$FullName.cfg"
        $newCfgPath = "$APP_DIR\$newCfgName"
        $EVB_TEMPLATE = "$PROJECT_ROOT\.build\evb_settings.evb"
        $TEMP_EVB = "$PROJECT_ROOT\.build\temp_build.evb"
        $EVB_CONSOLE = "C:\Program Files (x86)\Enigma Virtual Box\enigmavbconsole.exe"

        Write-Host "[EXE] Step 2: Patching paths for EVB..." -ForegroundColor Cyan

        # A. 物理更名：把 .cfg 改为带版本号的名字，否则 EXE 改名后找不到配置
        if (Test-Path $originalCfg) {
            Move-Item -Path $originalCfg -Destination $newCfgPath -Force
        }

        # B. 路径兼容性补丁：把这三个核心文件从 /app 复制到根目录，满足 EVB 模板中的旧路径引用
        Copy-Item "$APP_DIR\.jpackage.xml" "$APP_IMAGE_ROOT\.jpackage.xml" -Force
        Copy-Item "$APP_DIR\$JAR_NAME" "$APP_IMAGE_ROOT\$JAR_NAME" -Force
        Copy-Item "$newCfgPath" "$APP_IMAGE_ROOT\$newCfgName" -Force

        # C. 动态修改 EVB 模板
        Write-Host "[EXE] Step 3: Modifying EVB Project..." -ForegroundColor Cyan
        $finalExePath = "$OUT_DIR\$FullName.exe"
        $evbContent = Get-Content $EVB_TEMPLATE -Raw

        # 替换输出路径
        $evbContent = $evbContent -replace '<OutputFile>.*?</OutputFile>', "<OutputFile>$finalExePath</OutputFile>"
        # 替换配置文件引用 (包括物理路径和虚拟名称)
        $evbContent = $evbContent -replace "$BaseName.cfg", "$newCfgName"
        # 确保虚拟文件系统中的主执行文件也对应 (如果你的 .evb 引用了主 exe)
        # $evbContent = $evbContent -replace "$BaseName.exe", "$FullName.exe"

        $evbContent | Set-Content $TEMP_EVB -Encoding UTF8

        # D. 执行 EVB
        if (Test-Path $EVB_CONSOLE) {
            Write-Host "[EXE] Step 4: Packing with Enigma Virtual Box..." -ForegroundColor Cyan
            Push-Location "$PROJECT_ROOT\.build"
            & $EVB_CONSOLE "temp_build.evb"
            Pop-Location

            if (Test-Path $finalExePath) {
                Write-Host "[EXE] SUCCESS: $FullName.exe generated!" -ForegroundColor Green
            } else {
                Write-Host "[EXE] ERROR: EVB failed to generate output." -ForegroundColor Red
                exit 1
            }
        }

        # 清理临时文件
        Remove-Item $TEMP_EVB -ErrorAction SilentlyContinue
    }
}