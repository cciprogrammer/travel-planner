@echo off
setlocal enabledelayedexpansion
chcp 936 >nul

REM ============================================================
REM  旅行规划助手 - 启动前端 (http://127.0.0.1:5173)
REM
REM  用法:
REM    start-frontend.cmd          前台运行（vite 自动打开浏览器，开发调试用）
REM    start-frontend.cmd --bg     后台模式：日志写入 logs\frontend.log
REM                               （由 start.cmd 一键启动调用）
REM ============================================================

set "BG="
if /i "%~1"=="--bg" set "BG=1"

call "%~dp0_common.cmd"

cd /d "%WEBDIR%"

netstat -ano | findstr ":%WEB_PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 (
  echo [ERROR] 端口 %WEB_PORT% 已被占用，前端可能已在运行：
  netstat -ano ^| findstr ":%WEB_PORT% " ^| findstr "LISTENING"
  if not defined BG pause
  exit /b 1
)

if not exist "%WEBDIR%\node_modules\vite" (
  echo [INFO] 未发现前端依赖，执行 npm install ...
  call npm install --no-audit --no-fund
  if errorlevel 1 (
    echo [ERROR] npm install 失败
    if not defined BG pause
    exit /b 1
  )
)

if not exist "%WEBDIR%\node_modules\.bin\vite.cmd" (
  echo [ERROR] 未找到 vite 可执行文件，请先在本目录执行 npm install
  if not defined BG pause
  exit /b 1
)

echo ===============================================
echo  旅行规划助手 - 前端
echo  地址  : http://127.0.0.1:%WEB_PORT%
echo  后端  : http://localhost:%PORT%  (需同时运行)
if defined BG echo  日志  : %FRONTEND_LOG%
echo ===============================================
echo.

if defined BG (
  REM 后台模式由 start.cmd 统一在前后端都就绪后打开浏览器，这里禁用 vite 自动打开
  set "BROWSER=none"
  call "%WEBDIR%\node_modules\.bin\vite.cmd" --host 127.0.0.1 --port %WEB_PORT% --strictPort > "%FRONTEND_LOG%" 2>&1
) else (
  call "%WEBDIR%\node_modules\.bin\vite.cmd" --host 127.0.0.1 --port %WEB_PORT% --strictPort
)

echo.
echo [INFO] 前端已退出
if not defined BG pause
exit /b 0
