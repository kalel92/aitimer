@echo off
set JAVA_HOME=C:\IDE-SPRING_win64\tools\jdk1.8.0_201
set PATH=%JAVA_HOME%\bin;%PATH%

if not exist AITimerApp.class (
    echo Compilando primero...
    call build.bat
)

echo Iniciando AI Timer...
start javaw -Dfile.encoding=UTF-8 -cp . AITimerApp
