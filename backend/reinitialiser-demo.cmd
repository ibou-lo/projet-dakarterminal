@echo off
chcp 65001 >nul
REM Efface toutes les tables PostgreSQL et recharge les données de démonstration, puis démarre le backend.
REM Au prochain démarrage normal (demarrer.cmd), les données sont conservées.
cd /d "%~dp0"
echo ATTENTION : toutes les données de la base autotrack vont être effacées.
choice /M "Continuer"
if errorlevel 2 exit /b 1
set DDL_AUTO=create
mvn spring-boot:run
