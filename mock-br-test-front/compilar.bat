@echo off
chcp 65001 > nul
title Compilando Frontend Angular (Porta 4200)
cd /d "%~dp0"
echo ============================================================
echo  COMPILANDO FRONTEND ANGULAR 18
echo ============================================================
echo.
call npm run build
if errorlevel 1 (
    echo.
    echo ============================================================
    echo  [ERRO] Ocorreu uma falha na compilacao do Angular.
    echo ============================================================
) else (
    echo.
    echo ============================================================
    echo  [SUCESSO] Frontend Angular compilado com sucesso!
    echo ============================================================
)
echo.
pause
