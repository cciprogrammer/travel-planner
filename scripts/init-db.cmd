@echo off
setlocal
chcp 936 >nul

REM ===== 旅行规划助手 - 数据库初始化 =====
REM 用法: init-db.cmd  [MySQL root 密码]
REM 例:   init-db.cmd 123456

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not exist "%MYSQL_BIN%" set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql"

set "DB_PASSWORD=%~1"
if "%DB_PASSWORD%"=="" set /p DB_PASSWORD=MySQL root password:

if "%DB_PASSWORD%"=="" (
  echo [ERROR] password is empty, abort.
  exit /b 1
)

echo [1/2] creating database and tables ...
"%MYSQL_BIN%" -uroot -p"%DB_PASSWORD%" < "%~dp0..\sql\schema.sql"
if errorlevel 1 (
  echo [ERROR] failed to import schema.sql
  exit /b 1
)

echo [2/2] done.
"%MYSQL_BIN%" -uroot -p"%DB_PASSWORD%" -e "USE travel_planner; SHOW TABLES;"
endlocal
