@echo off
chcp 65001 > nul
title Compilando Mock Gov.BR (Porta 8089)
cd /d "%~dp0"
echo ============================================================
echo  COMPILANDO MOCK GOV.BR (PORTA 8089)
echo ============================================================
echo.
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo ============================================================
    echo  [ERRO] Ocorreu uma falha na compilacao do Mock Gov.BR.
    echo ============================================================
) else (
    echo.
    echo ============================================================
    echo  [SUCESSO] Mock Gov.BR compilado com sucesso!
    echo ============================================================
)
echo.
pause
