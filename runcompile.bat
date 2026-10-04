@echo off
cd /d "C:\Users\Public\Downloads\chrome downloads\Rakshak Setu\RakshakSetu"
call ".\gradlew.bat" :app:assembleBenchmark --offline > "C:\Users\Public\Downloads\chrome downloads\Rakshak Setu\RakshakSetu\build1.log" 2>&1
echo EXITCODE=%ERRORLEVEL% >> "C:\Users\Public\Downloads\chrome downloads\Rakshak Setu\RakshakSetu\build1.log"