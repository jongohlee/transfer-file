@echo off
@REM description: Transfer File Agent

@setlocal

set ERROR_CODE=0

set APP_NAME=transfer-file
@REM set JAVA_EXEC=C:\Program Files\Java\jdk-11.0.9\bin\java
set JAVA_EXEC=java

set APP_JAR=lib\transfer-file.jar
set LOG_FILE="logs/transfer-file.log"
set JAVA_OPTS=--spring.config.location=file:./config/transfer-file.yml --logging.file=%LOG_FILE% --spring.jmx.enabled=true -XX:+UseG1GC -Xms1024m -Xmx2048m
set PID_FS=logs\transfer-file.pid

if ""%1"" == ""start"" goto onStart
if ""%1"" == ""stop"" goto onStop
if ""%1"" == ""status"" goto onStatus

echo Usage:  agnet-boot ( commands ... )
echo commands:
echo   start    Start transfer-file
echo   stop     Stop transfer-file
echo   status   Status current transfer-file process
goto end

:onStart

set SESSIONNAME=Console
@REM  프로세스 제어를 위해 INSTANCE ID를 생성
set INSTANCE=%DATE:~0,2%%DATE:~3,2%%DATE:~8,2%%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%%TIME:~9,2%%RANDOM%
echo Instance: %INSTANCE%
set JAVA_OPTS=%JAVA_OPTS% -DWCID=%INSTANCE%

rem PID Find

echo Starting %APP_NAME%
start /B "%INSTANCE%" "%JAVA_EXEC%" -jar %APP_JAR% %JAVA_OPTS%

FOR /F %%T IN ('Wmic process where "Name="java.exe" and CommandLine Like '%%%INSTANCE%%%'" get ProcessId ^| more +1') DO (
SET /A CUR_PID=%%T) &GOTO SkipLine
:SkipLine

echo PID: %CUR_PID%   

if not exist "logs" mkdir logs
if exist %PID_FS% del /Q %PID_FS%
echo %INSTANCE%>> %PID_FS%

goto end

:onStop

echo Stopping %APP_NAME%
if not exist %PID_FS% (
        echo %APP_NAME% is not running...
        goto error
    )

set /p INSTANCE=< %PID_FS%
del /Q %PID_FS%
Wmic process where "Name="java.exe" and CommandLine Like '%%%INSTANCE%%%'" delete
rem Taskkill /PID %PS% /F


goto end

:onStatus

if exist %PID_FS% (
        set /p PS=< %PID_FS%
        echo %APP_NAME% pid %PS% is running...
        goto end
    )

echo %APP_NAME% is stopped
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%

exit /B %ERROR_CODE%