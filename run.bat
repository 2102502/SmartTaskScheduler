@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
javac --release 17 -d out src\main\java\smarttaskscheduler\*.java
if errorlevel 1 exit /b 1
java -cp out smarttaskscheduler.SmartTaskScheduler
