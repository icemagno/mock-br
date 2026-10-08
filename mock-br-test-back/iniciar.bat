@echo off
chcp 65001 > nul
title [8080] Backend Consumidor Spring Boot
cd /d "%~dp0"
echo ============================================================
echo  INICIANDO BACKEND CONSUMIDOR NA PORTA 8080
echo ============================================================
echo  - Certifique-se de que o mock-br (:8089) ja esteja rodando!
echo  - Endpoint Me:    http://localhost:8080/api/auth/me
echo  - Inicio Login:   http://localhost:8080/oauth2/authorization/govbr
echo ============================================================
echo.
call mvn spring-boot:run
pause
