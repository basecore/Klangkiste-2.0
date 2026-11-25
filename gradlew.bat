@echo off
setlocal
set VERSION=8.10.2
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (gradle %* & exit /b %ERRORLEVEL%)
set BASE=%USERPROFILE%\.gradle\wrapper\dists\klangkiste-gradle-%VERSION%
set DIST=%BASE%\gradle-%VERSION%
if not exist "%DIST%\bin\gradle.bat" (if not exist "%BASE%" mkdir "%BASE%" & powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%BASE%\gradle-%VERSION%-bin.zip'; Expand-Archive -Force '%BASE%\gradle-%VERSION%-bin.zip' '%BASE%'" )
call "%DIST%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
