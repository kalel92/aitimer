@echo off
set JAVA_HOME=C:\IDE-SPRING_win64\tools\jdk1.8.0_201
set PATH=%JAVA_HOME%\bin;%PATH%

echo Compilando AITimerApp...
javac -encoding UTF-8 -d . AITimerApp.java

if %ERRORLEVEL% == 0 (
    echo.
    echo Build OK. Ejecuta run.bat para iniciar.
) else (
    echo.
    echo ERROR en compilacion.
    pause
)
