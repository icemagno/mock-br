@echo off
chcp 65001 > nul
title Compilando Backend Consumidor (Porta 8080)
cd /d "%~dp0"
echo ============================================================
echo  COMPILANDO BACKEND CONSUMIDOR SPRING BOOT (PORTA 8080)
echo ============================================================
echo.
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo ============================================================
    echo  [ERRO] Ocorreu uma falha na compilacao do Backend.
    echo ============================================================
) else (
    echo.
    echo ============================================================
    echo  [SUCESSO] Backend Consumidor compilado com sucesso!
    echo ============================================================
)
echo.
pause
