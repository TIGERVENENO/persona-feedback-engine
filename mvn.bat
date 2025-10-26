@echo off
REM ============================================================================
REM Maven wrapper script for Windows
REM Sets JAVA_HOME to Java 21 (corretto) and runs Maven
REM ============================================================================

REM Set JAVA_HOME to Java 21 (corretto)
set JAVA_HOME=C:\Users\Tigran\.jdks\corretto-21.0.4

REM Display Java version for verification
echo [INFO] Using JAVA_HOME: %JAVA_HOME%
%JAVA_HOME%\bin\java -version

REM Run Maven with all arguments passed to this script
call mvnw.cmd %*
