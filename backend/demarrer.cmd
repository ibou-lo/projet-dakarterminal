@echo off
chcp 65001 >nul
REM Démarre le backend sur PostgreSQL (données conservées entre deux démarrages).
REM Base : localhost:5432/autotrack, utilisateur autotrack / autotrack (modifiable via DB_URL, DB_USER, DB_PASSWORD).
cd /d "%~dp0"
set DDL_AUTO=update
mvn spring-boot:run
