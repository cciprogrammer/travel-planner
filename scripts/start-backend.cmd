@echo off
setlocal enabledelayedexpansion
chcp 936 >nul

REM ============================================================
REM  旅行规划助手 - 启动后端 (http://localhost:8081)
REM
REM  用法:
REM    start-backend.cmd          前台运行：日志直接打印在本窗口（开发调试用）
REM    start-backend.cmd --bg     后台模式：日志写入 logs\backend.log
REM                               （由 start.cmd 一键启动调用）
REM
REM  数据库账号口令来自 application-local.yml（local profile），无需命令行传入
REM ============================================================

set "BG="
if /i "%~1"=="--bg" set "BG=1"

call "%~dp0_common.cmd"

cd /d "%ROOT%\backend"

REM 端口被占用直接退出，避免起在随机端口上导致前端代理 500（比"起不来"更难排查）
netstat -ano | findstr ":%PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 (
  echo [ERROR] 端口 %PORT% 已被占用，后端可能已在运行：
  netstat -ano ^| findstr ":%PORT% " ^| findstr "LISTENING"
  if not defined BG pause
  exit /b 1
)

if not exist "%JAR%" (
  echo [ERROR] 未找到后端 jar：
  echo         %JAR%
  echo         请先打包：cd /d "%ROOT%\backend" 然后 mvn clean package -DskipTests
  if not defined BG pause
  exit /b 1
)

echo ===============================================
echo  旅行规划助手 - 后端
echo  端口     : %PORT%
echo  Profile  : local
echo  DeepSeek : 见启动日志 "DeepSeek 生效配置"（Key 后 4 位 / 模型名）
if defined BG echo  日志     : %BACKEND_LOG%
echo ===============================================
echo.
if defined BG echo  [INFO] 本窗口是后端进程本身，关闭它等于停止后端；完整日志见 %BACKEND_LOG%
if defined BG echo.

if defined BG (
  REM 后台模式：日志落文件，便于一键启动脚本读取失败原因
  "%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%JAR%" ^
    --spring.profiles.active=local --server.port=%PORT% > "%BACKEND_LOG%" 2>&1
) else (
  "%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%JAR%" ^
    --spring.profiles.active=local --server.port=%PORT%
)

echo.
echo [INFO] 后端已退出 (exit code %errorlevel%)
if not defined BG pause
exit /b 0
