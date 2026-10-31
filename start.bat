@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title Campaign assistant

if not exist ".env" (
  copy ".env.example" ".env" >nul
  echo Создан .env из .env.example — проверьте адрес LLM.
)

rem ---- порты из .env ----
set "BACKEND_PORT=8080"
set "FRONTEND_PORT=5173"
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
  if /i "%%A"=="BACKEND_PORT" if not "%%B"=="" set "BACKEND_PORT=%%B"
  if /i "%%A"=="FRONTEND_PORT" if not "%%B"=="" set "FRONTEND_PORT=%%B"
)

rem ---- проверки ----
where java >nul 2>nul
if errorlevel 1 (
  echo [ОШИБКА] Не найдена Java. Нужен JDK 17 или новее: https://adoptium.net
  pause
  exit /b 1
)
where npm >nul 2>nul
if errorlevel 1 (
  echo [ОШИБКА] Не найден Node.js: https://nodejs.org
  pause
  exit /b 1
)

call :port_busy %BACKEND_PORT%
if not errorlevel 1 (
  echo [!] Порт %BACKEND_PORT% уже занят — возможно, сервер уже запущен. Использую его.
) else (
  echo Запускаю сервер на порту %BACKEND_PORT% в отдельном окне...
  start "Campaign BACKEND :%BACKEND_PORT%" cmd /k "cd /d "%~dp0backend" && gradlew.bat :server:run"
)

if not exist "frontend\node_modules" (
  echo Устанавливаю зависимости экрана ^(один раз^)...
  pushd frontend
  call npm install
  popd
)

echo Жду, пока сервер ответит на порту %BACKEND_PORT%. Первый запуск собирает проект: 2-5 минут.
set /a TRIES=0
:wait
call :port_busy %BACKEND_PORT%
if not errorlevel 1 goto backend_up
set /a TRIES+=1
if !TRIES! GEQ 200 (
  echo [ОШИБКА] Сервер не поднялся за 10 минут. Смотрите окно "Campaign BACKEND".
  pause
  exit /b 1
)
timeout /t 3 /nobreak >nul
goto wait

:backend_up
echo Сервер запущен.
start "Campaign FRONTEND :%FRONTEND_PORT%" cmd /k "cd /d "%~dp0frontend" && npm run dev"
timeout /t 5 /nobreak >nul
start "" "http://localhost:%FRONTEND_PORT%"
echo.
echo Готово: http://localhost:%FRONTEND_PORT%
echo Чтобы остановить, закройте окна BACKEND и FRONTEND.
pause
exit /b 0

:port_busy
powershell -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient('127.0.0.1', %1); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>nul
exit /b %errorlevel%
