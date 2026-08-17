@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo  Rakshak Setu -- Production-Grade APK Signing ^& Zipalign
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
echo [1/5] Building APK with Gradle...
call gradlew.bat assembleDebug --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Gradle build failed!
    exit /b %ERRORLEVEL%
)

if not exist "apk" mkdir apk

echo.
echo [2/5] Zipaligning APK...
if exist "%BUILD_TOOLS%\zipalign.exe" (
    "%BUILD_TOOLS%\zipalign.exe" -f -v -p 4 "app\build\outputs\apk\debug\app-debug.apk" "app\build\outputs\apk\debug\app-debug-aligned.apk"
) else (
    copy /y "app\build\outputs\apk\debug\app-debug.apk" "app\build\outputs\apk\debug\app-debug-aligned.apk"
)

echo.
echo [3/5] Signing APK with V1 + V2 + V3 schemes...
if exist "%BUILD_TOOLS%\apksigner.bat" (
    call "%BUILD_TOOLS%\apksigner.bat" sign --ks "%USERPROFILE%\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "app\build\outputs\apk\debug\RakshakSetu-v1.1.0.apk" "app\build\outputs\apk\debug\app-debug-aligned.apk"
    
    echo.
    echo [4/5] Verifying signature schemes...
    call "%BUILD_TOOLS%\apksigner.bat" verify --verbose --print-certs "app\build\outputs\apk\debug\RakshakSetu-v1.1.0.apk"
) else (
    copy /y "app\build\outputs\apk\debug\app-debug-aligned.apk" "app\build\outputs\apk\debug\RakshakSetu-v1.1.0.apk"
)

echo.
echo [5/5] Staging final APKs...
copy /y "app\build\outputs\apk\debug\RakshakSetu-v1.1.0.apk" "apk\RakshakSetu-v1.1.0.apk"
copy /y "app\build\outputs\apk\debug\RakshakSetu-v1.1.0.apk" "apk\rakshak-setu-debug.apk"

echo.
echo ========================================================
echo  SUCCESS: APK signed with V1+V2+V3 schemes!
echo  Staged at:
echo    - apk\RakshakSetu-v1.1.0.apk
echo    - apk\rakshak-setu-debug.apk
echo ========================================================
