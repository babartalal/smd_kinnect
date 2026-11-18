@echo off
rem Copies the Kinnect web services into XAMPP so Apache can serve them at
rem http://localhost/kinnect_api/ . Run again after changing any PHP file.
rem Uploaded images and local config already in htdocs are left untouched.

set "SRC=%~dp0kinnect_api"
set "DEST=C:\xampp\htdocs\kinnect_api"
if not "%~1"=="" set "DEST=%~1"

robocopy "%SRC%" "%DEST%" /E /XD uploads /XF config.local.php firebase-service-account.json /NFL /NDL /NJH /NJS
if errorlevel 8 (
    echo Copy failed.
    exit /b 1
)
if not exist "%DEST%\uploads" mkdir "%DEST%\uploads"

echo Deployed to %DEST%
echo Next: start Apache and MySQL in XAMPP, then open http://localhost/kinnect_api/setup.php once.
exit /b 0
