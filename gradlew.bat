@echo off
setlocal
set "GRADLE_VERSION=9.2.1"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "BASE=%GRADLE_USER_HOME%\manual-wrapper"
set "GRADLE_HOME=%BASE%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
if not exist "%GRADLE_BIN%" (
  if not exist "%BASE%" mkdir "%BASE%"
  set "ZIP=%BASE%\gradle-%GRADLE_VERSION%-bin.zip"
  if not exist "%ZIP%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%BASE%\gradle-%GRADLE_VERSION%-bin.zip'"
    if errorlevel 1 exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%BASE%\gradle-%GRADLE_VERSION%-bin.zip' '%BASE%'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
