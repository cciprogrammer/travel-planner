@echo off
setlocal enabledelayedexpansion
chcp 936 >nul
title 旅行规划助手 - 一键启动

REM ============================================================
REM  旅行规划助手 · 一键启动
REM
REM  双击本文件即可：
REM    环境检查 -> 数据库检查 -> 准备产物 -> 端口/运行状态检查
REM    -> 后台启动前后端 -> 等待就绪 -> 自动打开浏览器
REM
REM  参数（可选，可组合）：
REM    /rebuild     强制重新打包后端（改过 Java 代码后用）
REM    /nobrowser   启动完成后不自动打开浏览器
REM    /auto        非交互模式：等价于 /nobrowser，且结束/失败时不等待按键
REM                 （供脚本、自动化、开机自启调用）
REM
REM  退出码：0 成功 / 1 失败
REM ============================================================

set "NO_BROWSER="
set "NO_PAUSE="
set "FORCE_BUILD="

:parse
if "%~1"=="" goto parsed
if /i "%~1"=="/nobrowser" set "NO_BROWSER=1"
if /i "%~1"=="-nobrowser" set "NO_BROWSER=1"
if /i "%~1"=="/rebuild"   set "FORCE_BUILD=1"
if /i "%~1"=="-rebuild"   set "FORCE_BUILD=1"
if /i "%~1"=="/auto"      ( set "NO_BROWSER=1" & set "NO_PAUSE=1" )
if /i "%~1"=="-auto"      ( set "NO_BROWSER=1" & set "NO_PAUSE=1" )
shift
goto parse
:parsed

cd /d "%~dp0"
call "%~dp0scripts\_common.cmd"

REM 浏览器由本脚本在「前后端都就绪之后」统一打开，
REM 因此要让 vite 不要自己抢先打开（vite 读到 BROWSER=none 会跳过）
set "BROWSER=none"

set "WAIT_BACKEND=180"
set "WAIT_FRONTEND=90"
set "T_START=%TIME%"

echo.
echo  ================================================================
echo   旅行规划助手  ·  一键启动
echo  ----------------------------------------------------------------
echo   前端  http://127.0.0.1:%WEB_PORT%
echo   后端  http://localhost:%PORT%
echo   日志  %LOGDIR%
echo  ================================================================
echo.

REM ---------------- [1/7] 运行时环境 ----------------
echo  [1/7] 运行时环境 ...
if not exist "%JAVA_HOME%\bin\java.exe" (
  set "ERR=未找到 JDK 17"
  set "HINT=已自动探测: %TP_BIN%\java\jdk-17*、%ProgramFiles%\Java\jdk-17*；如装在别处，先 set TP_JAVA_HOME=你的JDK17目录 再重跑"
  goto :abort
)
if not exist "%NODE_HOME%\node.exe" (
  set "ERR=未找到 Node.js（需 18 及以上）"
  set "HINT=已自动探测: %TP_BIN%\node\versions\*、系统 PATH、%ProgramFiles%\nodejs；如装在别处，先 set TP_NODE_HOME=你的Node目录 再重跑"
  goto :abort
)
echo        JDK  %JAVA_HOME%
echo        Node %NODE_HOME%

REM ---------------- [2/7] 数据库 ----------------
echo  [2/7] 数据库连通性 ...
set "DBPID="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":3306 " ^| findstr "LISTENING"') do if not defined DBPID set "DBPID=%%P"
if defined DBPID (
  echo        MySQL 已运行 ^(PID !DBPID!^)
) else (
  set "ERR=MySQL 未启动：本机 3306 端口没有监听"
  set "HINT=请先启动 MySQL —— 管理员身份运行: net start mysql80（或到「服务」面板启动 MySQL），然后重跑本脚本"
  goto :abort
)

REM ---------------- [3/7] 构建产物 ----------------
echo  [3/7] 准备构建产物 ...
set "NEED_BUILD="
if "%FORCE_BUILD%"=="1" set "NEED_BUILD=1"
if not exist "%JAR%" set "NEED_BUILD=1"

if defined NEED_BUILD (
  echo        后端 jar 缺失或强制重建，开始打包 ^(首次约 1-3 分钟，请勿关闭窗口^) ...
  pushd "%ROOT%\backend"
  call "%MAVEN_HOME%\bin\mvn.cmd" -B clean package -DskipTests
  set "RC=!errorlevel!"
  popd
  if not "!RC!"=="0" (
    set "ERR=后端打包失败（mvn clean package 退出码 !RC!）"
    set "HINT=若日志出现 Unable to rename '*.jar'，说明有后端进程正占用 jar，请先执行 stop.cmd"
    goto :abort
  )
  echo        打包完成
) else (
  echo        后端 jar 已就绪
)

if not exist "%WEBDIR%\node_modules\vite" (
  echo        前端依赖缺失，执行 npm install ^(首次约 1-2 分钟^) ...
  pushd "%WEBDIR%"
  call npm install --no-audit --no-fund
  set "RC=!errorlevel!"
  popd
  if not "!RC!"=="0" (
    set "ERR=前端依赖安装失败（npm install 退出码 !RC!）"
    set "HINT=请检查网络后重试，或手动执行: cd /d "%WEBDIR%" ^&^& npm install"
    goto :abort
  )
  echo        依赖安装完成
) else (
  echo        前端依赖已就绪
)

REM ---------------- [4/7] 端口与运行状态 ----------------
echo  [4/7] 检查端口与运行状态 ...
call :probe_app %PORT%
if "!PROBE!"=="ours" (
  echo        端口 %PORT% 已被「本项目的后端」占用，直接复用
  set "SKIP_BACKEND=1"
) else if "!PROBE!"=="other" (
  set "ERR=端口 %PORT% 被其它程序占用（PID !PROBE_PID! / !PROBE_IMG!）"
  set "HINT=释放端口: taskkill /PID !PROBE_PID! /F   或换端口: set PORT=8082 后重跑"
  goto :abort
) else (
  echo        端口 %PORT% 空闲
)

call :probe_app %WEB_PORT%
if "!PROBE!"=="ours" (
  echo        端口 %WEB_PORT% 已被「本项目的前端」占用，直接复用
  set "SKIP_FRONTEND=1"
) else if "!PROBE!"=="other" (
  set "ERR=端口 %WEB_PORT% 被其它程序占用（PID !PROBE_PID! / !PROBE_IMG!）"
  set "HINT=释放端口: taskkill /PID !PROBE_PID! /F   或换端口: set WEB_PORT=5174 后重跑"
  goto :abort
) else (
  echo        端口 %WEB_PORT% 空闲
)

REM ---------------- [5/7] 启动后端 ----------------
echo  [5/7] 启动后端 ...
if defined SKIP_BACKEND (
  echo        已复用运行中的后端，跳过启动
) else (
  start "tp-backend" /min cmd /c "scripts\start-backend.cmd --bg"
  call :wait_ready %PORT% %WAIT_BACKEND% "%BACKEND_LOG%" "Started TravelPlannerApplication"
  if errorlevel 1 (
    set "ERR=后端启动失败：%WAIT_BACKEND% 秒内未就绪"
    set "HINT=完整日志 %BACKEND_LOG%；常见原因：数据库未建库 / DB 口令不符 / 端口被占用"
    goto :abort
  )
  echo        后端已就绪
)

REM ---------------- [6/7] 启动前端 ----------------
echo  [6/7] 启动前端 ...
if defined SKIP_FRONTEND (
  echo        已复用运行中的前端，跳过启动
) else (
  start "tp-frontend" /min cmd /c "scripts\start-frontend.cmd --bg"
  call :wait_ready %WEB_PORT% %WAIT_FRONTEND% "%FRONTEND_LOG%" "-"
  if errorlevel 1 (
    set "ERR=前端启动失败：%WAIT_FRONTEND% 秒内未就绪"
    set "HINT=完整日志 %FRONTEND_LOG%；常见原因：端口被占用 / 依赖缺失 / vite 启动报错"
    goto :abort
  )
  echo        前端已就绪
)

REM ---------------- [7/7] 打开浏览器 ----------------
echo  [7/7] 打开浏览器 ...
if defined NO_BROWSER (
  echo        已按参数要求跳过自动打开
) else (
  start "" "http://127.0.0.1:%WEB_PORT%"
  echo        已打开 http://127.0.0.1:%WEB_PORT%
)

call :elapsed

echo.
echo  ================================================================
echo   启动完成（耗时 %ELAPSED% 秒）
echo  ----------------------------------------------------------------
echo   前端    http://127.0.0.1:%WEB_PORT%
echo   后端    http://localhost:%PORT%/api
echo   日志    %LOGDIR%\backend.log
echo           %LOGDIR%\frontend.log
echo   停止    stop.cmd        （或直接关闭最小化的 tp-backend / tp-frontend 窗口）
echo   状态    scripts\status.cmd
echo  ================================================================
echo.
echo  前后端在最小化的独立窗口里运行，本窗口关闭不影响服务。
echo.
if not defined NO_PAUSE (
  echo  按任意键关闭本窗口 ...
  pause >nul
)
exit /b 0

REM ============================================================
REM  失败处理
REM ============================================================
:abort
call :elapsed
echo.
echo  ================================================================
echo   [启动失败] !ERR!   （已耗时 !ELAPSED! 秒）
echo  ----------------------------------------------------------------
if defined HINT echo   !HINT!
echo  ================================================================
if exist "%BACKEND_LOG%" (
  findstr /i /c:"APPLICATION FAILED TO START" /c:"Access denied" /c:"Unknown database" /c:"Web server failed to start" "%BACKEND_LOG%" >nul 2>&1
  if not errorlevel 1 (
    echo.
    call :tail_log "%BACKEND_LOG%"
  )
)
echo.
if not defined NO_PAUSE (
  echo  按任意键关闭本窗口 ...
  pause >nul
)
exit /b 1

REM ============================================================
REM  :elapsed   由 T_START 与当前时间算出耗时秒数 -> ELAPSED
REM ============================================================
:elapsed
REM 单数字小时时 %TIME% 形如 " 9:05:12.34"，先用 0 替换空格再做算术
set "E1=%T_START: =0%"
set "E2=%TIME: =0%"
for /f "tokens=1-4 delims=:.," %%a in ("%E1%") do set /a "ES=(1%%a-100)*3600+(1%%b-100)*60+(1%%c-100)"
for /f "tokens=1-4 delims=:.," %%a in ("%E2%") do set /a "EE=(1%%a-100)*3600+(1%%b-100)*60+(1%%c-100)"
set /a "ELAPSED=EE-ES"
if !ELAPSED! LSS 0 set /a "ELAPSED=ELAPSED+86400"
exit /b 0

REM ============================================================
REM  :wait_ready <port> <timeout_sec> <logfile> <log_token>
REM    就绪判定：
REM      token 为 "-"  -> 端口出现监听即视为就绪（前端）
REM      token 其它值   -> 日志出现该关键字才算就绪（后端，更严格）
REM    提前失败：日志出现致命错误关键字时立即返回，不再干等超时
REM    返回：0 就绪 / 1 失败或超时
REM ============================================================
:wait_ready
set "WR_PORT=%~1"
set "WR_T=%~2"
set "WR_LOG=%~3"
set "WR_TOKEN=%~4"
set /a "WR_N=0"
:wr_loop
set "WR_PID="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":!WR_PORT! " ^| findstr "LISTENING"') do if not defined WR_PID set "WR_PID=%%P"
if defined WR_PID (
  if "!WR_TOKEN!"=="-" exit /b 0
  if exist "!WR_LOG!" (
    findstr /c:"!WR_TOKEN!" "!WR_LOG!" >nul 2>&1
    if not errorlevel 1 exit /b 0
  )
)
if exist "!WR_LOG!" (
  findstr /i /c:"APPLICATION FAILED TO START" /c:"Web server failed to start" /c:"Access denied for user" /c:"Unknown database" /c:"EADDRINUSE" /c:"is already in use" /c:"error when starting dev server" "!WR_LOG!" >nul 2>&1
  if not errorlevel 1 (
    echo.
    call :tail_log "!WR_LOG!"
    exit /b 1
  )
)
set /a "WR_N+=2"
if !WR_N! GEQ !WR_T! exit /b 1
ping -n 3 127.0.0.1 >nul
goto :wr_loop

REM ============================================================
REM  :probe_app <port>
REM    结果写入 PROBE：none（空闲）/ ours（本项目服务）/ other（别人占用）
REM    附带 PROBE_PID、PROBE_IMG
REM ============================================================
:probe_app
set "PROBE=none"
set "PROBE_PID="
set "PROBE_IMG="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":%~1 " ^| findstr "LISTENING"') do if not defined PROBE_PID set "PROBE_PID=%%P"
if not defined PROBE_PID exit /b 0
for /f "tokens=1 delims=," %%I in ('tasklist /FI "PID eq !PROBE_PID!" /FO CSV /NH 2^>nul') do set "PROBE_IMG=%%~I"
if /i "!PROBE_IMG!"=="java.exe" (
  set "PROBE=ours"
  exit /b 0
)
if /i "!PROBE_IMG!"=="node.exe" (
  set "PROBE=ours"
  exit /b 0
)
set "PROBE=other"
exit /b 0

REM ============================================================
REM  :tail_log <file>   打印文件末尾 12 行
REM ============================================================
:tail_log
set "TL=%~1"
if not exist "%TL%" (
  echo   ^(无日志文件 %TL%^)
  exit /b 0
)
set "TLN=0"
for /f %%C in ('find /c /v "" ^< "%TL%"') do set "TLN=%%C"
set /a "TLS=TLN-12"
if !TLS! LSS 0 set /a "TLS=0"
echo   ---- %TL% 末尾内容 ----
more +!TLS! "%TL%"
echo   ------------------------
exit /b 0
