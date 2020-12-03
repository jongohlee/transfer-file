@echo off
@REM Startup script for a spring boot project
@REM
@REM description: Transfer File Agent

:skipRcPre

@setlocal

set ERROR_CODE=0

@REM To isolate internal variables from possible post scripts, we use another setlocal
@setlocal

@REM ==== START VALIDATION ====
@REM if not "%JAVA_HOME%" == "" goto OkJHome

@REM echo.
@REM echo Error: JAVA_HOME not found in your environment. >&2
@REM echo Please set the JAVA_HOME variable in your environment to match the >&2
@REM echo location of your Java installation. >&2
@REM echo.
@REM goto error

@REM :OkJHome
@REM if exist "%JAVA_HOME%\bin\java.exe" goto init

@REM echo.
@REM echo Error: JAVA_HOME is set to an invalid directory. >&2
@REM echo JAVA_HOME = "%JAVA_HOME%" >&2
@REM echo Please set the JAVA_HOME variable in your environment to match the >&2
@REM echo location of your Java installation. >&2
@REM echo.
@REM goto error

@REM ==== END VALIDATION ====

@REM :init

set APP_NAME=transfer-file
@REM set JAVA_EXEC=%JAVA_HOME%\bin\java
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
set INSTANCE=%DATE:~0,4%%DATE:~5,2%%DATE:~8,2%%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%%TIME:~9,2%%RANDOM%
echo Instance: %INSTANCE%
set JAVA_OPTS=%JAVA_OPTS% -DWCID=%INSTANCE%

rem PID Find

echo Starting %APP_NAME%
start /B "%INSTANCE%" "%JAVA_EXEC%" -jar %APP_JAR% %JAVA_OPTS%

FOR /F %%T IN ('Wmic process where "Name="java.exe" and CommandLine Like '%%%INSTANCE%%%'" get ProcessId ^| more +1') DO (
SET /A CUR_PID=%%T) &GOTO SkipLine
:SkipLine

echo %CUR_PID%   

if exist %PID_FS% del /Q %PID_FS%
echo %CUR_PID% >> %PID_FS%

goto end

:onStop

echo Stopping %APP_NAME%
if not exist %PID_FS% (
		echo %APP_NAME% is not running...
		goto error
	)

set /p PS=< %PID_FS%
del /Q %PID_FS%
Taskkill /PID %PS% /F

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