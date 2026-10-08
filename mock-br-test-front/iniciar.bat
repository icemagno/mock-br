@echo off
chcp 65001 > nul
title [4200] Frontend Angular Consumidor
cd /d "%~dp0"
echo ============================================================
echo  INICIANDO FRONTEND ANGULAR NA PORTA 4200
echo ============================================================
echo  - Acesse no seu navegador: http://localhost:4200
echo ============================================================
echo.
call npm start
pause
