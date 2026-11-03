@echo off
setlocal enabledelayedexpansion
chcp 936 >nul
title 旅行规划助手 - 停止

REM ============================================================
REM  旅行规划助手 · 一键停止
REM
REM  用法:
REM    stop.cmd            停止前后端
REM    stop.cmd /auto      非交互（脚本调用，不等待按键）
REM
REM  说明：前端 vite 是 node 进程、后端是 java 进程，按端口找到 PID 后结束。
REM ============================================================

set "NO_PAUSE="
if /i "%~1"=="/auto" set "NO_PAUSE=1"
if /i "%~1"=="-auto" set "NO_PAUSE=1"

cd /d "%~dp0"
call "%~dp0scripts\_common.cmd"

echo.
echo  ================================================================
echo   旅行规划助手  ·  停止服务
echo  ================================================================
echo.

call :kill_port %PORT% "后端"
call :kill_port %WEB_PORT% "前端"

REM 兜底：按窗口标题结束（万一端口没绑上但进程还在）
taskkill /FI "WINDOWTITLE eq tp-backend*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq tp-frontend*" /F >nul 2>&1

ping -n 2 127.0.0.1 >nul

echo.
set "LEFT="
netstat -ano | findstr ":%PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 set "LEFT=1"
netstat -ano | findstr ":%WEB_PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 set "LEFT=1"
if defined LEFT (
  echo   [警告] 仍有端口处于监听状态，可能进程需要更高权限才能结束：
  netstat -ano ^| findstr ":%PORT% " ^| findstr "LISTENING"
  netstat -ano ^| findstr ":%WEB_PORT% " ^| findstr "LISTENING"
  echo   请以管理员身份重跑本脚本，或手动执行 taskkill /PID ^<PID^> /F
) else (
  echo   前后端已全部停止，端口 %PORT% / %WEB_PORT% 已释放。
)

echo.
if not defined NO_PAUSE (
  echo  按任意键关闭本窗口 ...
  pause >nul
)
exit /b 0

REM ============================================================
REM  :kill_port <port> <名称>
REM ============================================================
:kill_port
set "KP=%~1"
set "KN=%~2"
set "KPID="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":!KP! " ^| findstr "LISTENING"') do if not defined KPID set "KPID=%%P"
if not defined KPID (
  echo   [!KN!] 端口 !KP! 未在监听，无需处理
  exit /b 0
)
echo   [!KN!] 端口 !KP! 被 PID !KPID! 占用，正在结束 ...
taskkill /PID !KPID! /F /T >nul 2>&1
if errorlevel 1 (
  echo   [!KN!] 结束失败（可能需要管理员权限）
) else (
  echo   [!KN!] 已停止
)
exit /b 0
