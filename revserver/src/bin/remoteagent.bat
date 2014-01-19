@echo off
setlocal

IF "%JAVA_HOME%"=="" SET LOCAL_JAVA=java
IF NOT "%JAVA_HOME%"=="" SET LOCAL_JAVA=%JAVA_HOME%\bin\java

cd %~dp0

set TMP_CP="reverse-server.jar"

dir /b "lib\*.*" > temp.tmp
FOR /F %%I IN (temp.tmp) DO CALL "addpath.bat" "lib\%%I"

del temp.tmp 2>&1 > NUL

SET TMP_CP=%TMP_CP%;"%CLASSPATH%"
REM SET PARMS=REMOTE-IP-ADDRESS 23 localhost 22

start "remoteagent" %LOCAL_JAVA% -classpath %TMP_CP% org.fengzh.tools.net.revsever.remote.RemoteMain %PARMS%

endlocal
