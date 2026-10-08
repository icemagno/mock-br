@echo off
chcp 65001 > nul
title [8089] Simulador Gov.BR OIDC
cd /d "%~dp0"
echo ============================================================
echo  INICIANDO SIMULADOR GOV.BR NA PORTA 8089
echo ============================================================
echo  - Discovery: http://localhost:8089/.well-known/openid-configuration
echo  - JWKS:      http://localhost:8089/jwks.json
echo  - Login URL: http://localhost:8089/authorize
echo ============================================================
echo.
call mvn spring-boot:run
pause
