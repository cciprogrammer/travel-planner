@echo off
REM ============================================================
REM  旅行规划助手 - 公共环境变量
REM  被 start.cmd / stop.cmd / status.cmd / start-*.cmd 以 call 方式加载
REM  用法: call "%~dp0_common.cmd"
REM
REM  【重要】本文件不要写 setlocal，
REM          否则变量无法回传给调用方（setlocal 作用域在文件退出时销毁）。
REM ============================================================

REM ---- 项目根目录（本文件位于 <root>\scripts\）----
for %%I in ("%~dp0..") do set "ROOT=%%~fI"

REM ---- 端口（可用环境变量覆盖，例如 set PORT=8082）----
if not defined PORT     set "PORT=8081"
if not defined WEB_PORT set "WEB_PORT=5173"

REM ---- 运行时路径 ----
REM  【为什么不写死】托管运行时的目录名带版本号，升级后就会变
REM    （实测 node: 22.22.2-2 -> 22.22.2-3 -> 22.22.2-6，每变一次一键启动就报
REM     「未找到 Node.js」）。所以这里改为按序探测，找不到才算真缺失。
REM  解析优先级：显式指定(TP_*) > 托管 binaries 目录 > 系统 PATH > 常见安装位置
set "TP_BIN=%USERPROFILE%\.workbuddy\binaries"
set "JAVA_HOME="
set "NODE_HOME="
set "MAVEN_HOME="
if defined TP_JAVA_HOME  set "JAVA_HOME=%TP_JAVA_HOME%"
if defined TP_NODE_HOME  set "NODE_HOME=%TP_NODE_HOME%"
if defined TP_MAVEN_HOME set "MAVEN_HOME=%TP_MAVEN_HOME%"

REM -- JDK 17（只认 17+；系统默认 java 是 11，不能拿 PATH 里的凑数）
if not defined JAVA_HOME for /d %%D in ("%TP_BIN%\java\jdk-17*") do if exist "%%~fD\bin\java.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME for /d %%D in ("%TP_BIN%\java\jdk*")    do if exist "%%~fD\bin\java.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME for /d %%D in ("%ProgramFiles%\Java\jdk-17*") do if exist "%%~fD\bin\java.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-17*") do if exist "%%~fD\bin\java.exe" set "JAVA_HOME=%%~fD"

REM -- Node（托管 versions 下任意含 node.exe 的目录；按名升序，最后命中即最新）
if not defined NODE_HOME for /d %%D in ("%TP_BIN%\node\versions\*") do if exist "%%~fD\node.exe" set "NODE_HOME=%%~fD"
if not defined NODE_HOME for /f "delims=" %%P in ('where node.exe 2^>nul') do if not defined NODE_HOME set "NODE_HOME=%%~dpP"
if not defined NODE_HOME if exist "%ProgramFiles%\nodejs\node.exe" set "NODE_HOME=%ProgramFiles%\nodejs"

REM -- Maven（仅打包时需要）
if not defined MAVEN_HOME for /d %%D in ("%TP_BIN%\maven\apache-maven-*") do if exist "%%~fD\bin\mvn.cmd" set "MAVEN_HOME=%%~fD"

REM -- 去掉探测结果可能带的尾部反斜杠
if defined JAVA_HOME  if "%JAVA_HOME:~-1%"=="\"  set "JAVA_HOME=%JAVA_HOME:~0,-1%"
if defined NODE_HOME  if "%NODE_HOME:~-1%"=="\"  set "NODE_HOME=%NODE_HOME:~0,-1%"
if defined MAVEN_HOME if "%MAVEN_HOME:~-1%"=="\" set "MAVEN_HOME=%MAVEN_HOME:~0,-1%"

REM ---- 目录与文件 ----
set "LOGDIR=%ROOT%\logs"
set "RUNDIR=%ROOT%\.run"
if not exist "%LOGDIR%" md "%LOGDIR%" >nul 2>&1
if not exist "%RUNDIR%" md "%RUNDIR%" >nul 2>&1
set "BACKEND_LOG=%LOGDIR%\backend.log"
set "FRONTEND_LOG=%LOGDIR%\frontend.log"
set "JAR=%ROOT%\backend\target\travel-planner-0.0.1-SNAPSHOT.jar"
set "WEBDIR=%ROOT%\frontend"

REM ---- 让 JDK17 / Node / Maven 优先于系统默认版本 ----
REM  （本机系统默认 java 是 JDK11，必须显式前置，否则编译/启动会用错版本）
REM  只在探测成功时才前置，避免 PATH 里出现空项
if defined JAVA_HOME  set "PATH=%JAVA_HOME%\bin;%PATH%"
if defined NODE_HOME  set "PATH=%NODE_HOME%;%PATH%"
if defined MAVEN_HOME set "PATH=%MAVEN_HOME%\bin;%PATH%"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

exit /b 0
