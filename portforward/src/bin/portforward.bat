@echo off
setlocal

IF "%JAVA_HOME%"=="" SET LOCAL_JAVA=javaw
IF NOT "%JAVA_HOME%"=="" SET LOCAL_JAVA=%JAVA_HOME%\bin\javaw

cd %~dp0

set TMP_CP="portforward.jar"

dir /b "lib\*.*" > temp.tmp
FOR /F %%I IN (temp.tmp) DO CALL "addpath.bat" "lib\%%I"

del temp.tmp 2>&1 > NUL

SET TMP_CP=%TMP_CP%;"%CLASSPATH%"
REM SET PARMS=203.208.39.22/74.125.159.147/74.125.159.105/74.125.159.104/74.125.159.103/74.125.159.106/74.125.159.99/74.125.47.19/74.125.47.17/74.125.47.83/74.125.47.18/209.85.146.139/209.85.146.138/74.125.153.136/64.233.183.104/64.233.183.17/64.233.183.19/64.233.183.18/64.233.183.83/64.233.183.102/74.125.71.83/74.125.71.17/74.125.71.18/74.125.71.19/74.125.71.99/74.125.71.104/74.125.153.104/74.125.153.147/74.125.153.99 80/443
SET PARMS=@hosts.txt 80/443

start "portforward" %LOCAL_JAVA% -classpath %TMP_CP% org.fengzh.tools.net.portforward.Main %PARMS%

endlocal
