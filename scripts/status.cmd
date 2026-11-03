@echo off
setlocal enabledelayedexpansion
chcp 936 >nul
title 旅行规划助手 - 运行状态

REM ============================================================
REM  旅行规划助手 · 运行状态速查
REM  用法: scripts\status.cmd
REM ============================================================

for %%I in ("%~dp0..") do cd /d "%%~fI"
call "%~dp0_common.cmd"

echo.
echo  ================================================================
echo   旅行规划助手  ·  运行状态
echo  ================================================================
echo.

call :line "MySQL"  3306
call :line "后端"   %PORT%
call :line "前端"   %WEB_PORT%

echo.
echo  ---- 接口连通性 ----
REM 本机探测必须绕开系统代理，否则 curl 会走代理并拿到 502
set "NO_PROXY=localhost,127.0.0.1"
set "no_proxy=localhost,127.0.0.1"
set "C1=000"
for /f "delims=" %%C in ('curl -s -m 3 -o nul -w "%%{http_code}" http://127.0.0.1:%PORT%/api/user/profile 2^>nul') do set "C1=%%C"
set "C2=000"
for /f "delims=" %%C in ('curl -s -m 3 -o nul -w "%%{http_code}" http://127.0.0.1:%WEB_PORT%/ 2^>nul') do set "C2=%%C"
echo   后端 /api/user/profile ... HTTP !C1!   ^(401 = 服务正常但未登录^)
echo   前端首页 ................ HTTP !C2!
if "!C1!"=="000" echo   ^(后端未响应：可能未启动^)
if "!C2!"=="000" echo   ^(前端未响应：可能未启动^)

echo.
echo  ---- 日志末尾（后端 %BACKEND_LOG%）----
call :tail "%BACKEND_LOG%" 6
echo.
echo  ---- 日志末尾（前端 %FRONTEND_LOG%）----
call :tail "%FRONTEND_LOG%" 6
echo.
echo  ================================================================
echo.
exit /b 0

:line
set "LPORT=%~2"
set "LPID="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":!LPORT! " ^| findstr "LISTENING"') do if not defined LPID set "LPID=%%P"
if defined LPID (
  set "LIMG="
  for /f "tokens=1 delims=," %%I in ('tasklist /FI "PID eq !LPID!" /FO CSV /NH 2^>nul') do set "LIMG=%%~I"
  echo   [运行] %~1  端口 !LPORT!  PID !LPID!  !LIMG!
) else (
  echo   [停止] %~1  端口 !LPORT!  未监听
)
exit /b 0

:tail
set "TF=%~1"
set "TN=%~2"
if not exist "%TF%" (
  echo   ^(无日志^)
  exit /b 0
)
set "TLN=0"
for /f %%C in ('find /c /v "" ^< "%TF%"') do set "TLN=%%C"
set /a "TLS=TLN-%TN%"
if !TLS! LSS 0 set /a "TLS=0"
more +!TLS! "%TF%"
exit /b 0
