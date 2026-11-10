@echo off
set VERSION=8.11.1
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set BASE=%GRADLE_USER_HOME%\manual-wrapper\gradle-%VERSION%
if not exist "%BASE%\bin\gradle.bat" (
  if not exist "%GRADLE_USER_HOME%\manual-wrapper" mkdir "%GRADLE_USER_HOME%\manual-wrapper"
  powershell -NoProfile -Command "$u='https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip'; Invoke-WebRequest $u -OutFile '%GRADLE_USER_HOME%\manual-wrapper\gradle-%VERSION%-bin.zip'; Expand-Archive -Force '%GRADLE_USER_HOME%\manual-wrapper\gradle-%VERSION%-bin.zip' '%GRADLE_USER_HOME%\manual-wrapper'"
)
call "%BASE%\bin\gradle.bat" %*
