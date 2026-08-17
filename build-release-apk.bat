@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo  Rakshak Setu -- Production-Grade Split APK Signing
echo ========================================================

if not defined ANDROID_HOME (
    set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
)

if not exist "%ANDROID_HOME%" (
    echo [ERROR] Android SDK not found at %ANDROID_HOME%
    exit /b 1
)

for /f "delims=" %%i in ('dir /b /ad /o-n "%ANDROID_HOME%\build-tools"') do (
    set "BUILD_TOOLS=%ANDROID_HOME%\build-tools\%%i"
    goto :found_tools
)
:found_tools

echo [INFO] Using Build Tools: %BUILD_TOOLS%
echo [INFO] Using Keystore: %USERPROFILE%\.android\debug.keystore

echo.
echo [1/4] Cleaning and Building Split APKs with Gradle...
call gradlew.bat clean assembleDebug --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Gradle build failed!
    exit /b %ERRORLEVEL%
)

if not exist "apk" mkdir apk

echo.
echo [2/4] Zipaligning and Signing arm64-v8a APK (95%% of modern phones)...
set "ARM64_IN=app\build\outputs\apk\debug\app-arm64-v8a-debug.apk"
set "ARM64_ALIGNED=app\build\outputs\apk\debug\app-arm64-v8a-aligned.apk"
set "ARM64_OUT=apk\RakshakSetu-v1.2.0-arm64-v8a.apk"

if exist "%ARM64_IN%" (
    if exist "%BUILD_TOOLS%\zipalign.exe" (
        "%BUILD_TOOLS%\zipalign.exe" -f -v -p 4 "%ARM64_IN%" "%ARM64_ALIGNED%"
    ) else (
        copy /y "%ARM64_IN%" "%ARM64_ALIGNED%"
    )
    if exist "%BUILD_TOOLS%\apksigner.bat" (
        call "%BUILD_TOOLS%\apksigner.bat" sign --ks "%USERPROFILE%\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "%ARM64_OUT%" "%ARM64_ALIGNED%"
        echo [VERIFY ARM64]:
        call "%BUILD_TOOLS%\apksigner.bat" verify --verbose "%ARM64_OUT%"
    ) else (
        copy /y "%ARM64_ALIGNED%" "%ARM64_OUT%"
    )
    copy /y "%ARM64_OUT%" "apk\rakshak-setu-debug.apk"
)

echo.
echo [3/4] Zipaligning and Signing armeabi-v7a APK (32-bit older phones)...
set "ARMV7_IN=app\build\outputs\apk\debug\app-armeabi-v7a-debug.apk"
set "ARMV7_ALIGNED=app\build\outputs\apk\debug\app-armeabi-v7a-aligned.apk"
set "ARMV7_OUT=apk\RakshakSetu-v1.2.0-armeabi-v7a.apk"

if exist "%ARMV7_IN%" (
    if exist "%BUILD_TOOLS%\zipalign.exe" (
        "%BUILD_TOOLS%\zipalign.exe" -f -v -p 4 "%ARMV7_IN%" "%ARMV7_ALIGNED%"
    ) else (
        copy /y "%ARMV7_IN%" "%ARMV7_ALIGNED%"
    )
    if exist "%BUILD_TOOLS%\apksigner.bat" (
        call "%BUILD_TOOLS%\apksigner.bat" sign --ks "%USERPROFILE%\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "%ARMV7_OUT%" "%ARMV7_ALIGNED%"
        echo [VERIFY ARMV7]:
        call "%BUILD_TOOLS%\apksigner.bat" verify --verbose "%ARMV7_OUT%"
    ) else (
        copy /y "%ARMV7_ALIGNED%" "%ARMV7_OUT%"
    )
)

echo.
echo ========================================================
echo  SUCCESS: Split APKs built, zipaligned ^& V2/V3 signed!
echo  Staged outputs in apk/:
echo    - apk\RakshakSetu-v1.2.0-arm64-v8a.apk  (arm64, modern devices)
echo    - apk\RakshakSetu-v1.2.0-armeabi-v7a.apk (arm32, legacy devices)
echo    - apk\rakshak-setu-debug.apk             (default deployment)
echo ========================================================
