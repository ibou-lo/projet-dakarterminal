@echo off
chcp 65001 >nul
REM Démo rapide sans PostgreSQL : base H2 en mémoire, remise à zéro à chaque démarrage.
cd /d "%~dp0"
mvn spring-boot:run -Dspring-boot.run.profiles=h2
