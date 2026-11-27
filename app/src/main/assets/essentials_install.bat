@echo off
setlocal
color 0A
title Install All Essentials

if not exist "D:\WinlatorExtras" mkdir "D:\WinlatorExtras"
if not exist "D:\WinlatorExtras" goto setup_failed

echo.
echo ============================================
echo         INSTALL ALL ESSENTIALS
echo ============================================
echo.
echo Downloads are checked before installation.
echo Existing packages are reused only when their size matches.
echo.

echo [1/7] Downloading Wine Mono...
call :download "D:\WinlatorExtras\wine-mono-9.0.0-x86.msi" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/wine-mono-9.0.0-x86.msi" 84583424
if errorlevel 1 goto download_failed

echo [2/7] Downloading Wine Gecko...
call :download "D:\WinlatorExtras\wine-gecko-2.47.4-x86_64.msi" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/wine-gecko-2.47.4-x86_64.msi" 53898752
if errorlevel 1 goto download_failed

echo [3/7] Downloading Visual C++ runtimes...
call :download "D:\WinlatorExtras\vcredist2012_x86.exe" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/vcredist2012_x86.exe" 6554576
if errorlevel 1 goto download_failed
call :download "D:\WinlatorExtras\vcredist2012_x64.exe" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/vcredist2012_x64.exe" 7186992
if errorlevel 1 goto download_failed
call :download "D:\WinlatorExtras\vcredist2013_x86.exe" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/vcredist2013_x86.exe" 6510136
if errorlevel 1 goto download_failed
call :download "D:\WinlatorExtras\vcredist2013_x64.exe" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/vcredist2013_x64.exe" 7200744
if errorlevel 1 goto download_failed
call :download "D:\WinlatorExtras\vcredist2015_2017_2019_2022_x86.exe" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/vcredist2015_2017_2019_2022_x86.exe" 13950496
if errorlevel 1 goto download_failed
call :download "D:\WinlatorExtras\vcredist2015_2017_2019_2022_x64.exe" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/vcredist2015_2017_2019_2022_x64.exe" 25502344
if errorlevel 1 goto download_failed

echo [4/7] Downloading DirectX...
call :download "D:\WinlatorExtras\DirectX.exe" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/DirectX.exe" 142792980
if errorlevel 1 goto download_failed

echo [5/7] Downloading PhysX...
call :download "D:\WinlatorExtras\PhysX.msi" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/PhysX.msi" 28080640
if errorlevel 1 goto download_failed

echo [6/7] Downloading Media Foundation files...
call :download "D:\WinlatorExtras\dll.7z" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/dll.7z" 4601310
if errorlevel 1 goto download_failed
call :download "D:\WinlatorExtras\mediafoundation.reg" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/mediafoundation.reg" 146287
if errorlevel 1 goto download_failed

echo [7/7] Downloading XNA Framework...
call :download "D:\WinlatorExtras\xnafx40_redist.msi" "https://github.com/GunaCharanTeja/Winlator-Extras/releases/download/1/xnafx40_redist.msi" 7054336
if errorlevel 1 goto download_failed

echo.
echo All 13 packages passed download and size checks.
echo Installing Wine Mono...
msiexec /i "D:\WinlatorExtras\wine-mono-9.0.0-x86.msi" /quiet /norestart
call :check_installer
if errorlevel 1 goto install_failed

echo Installing Wine Gecko...
msiexec /i "D:\WinlatorExtras\wine-gecko-2.47.4-x86_64.msi" /quiet /norestart
call :check_installer
if errorlevel 1 goto install_failed

echo Installing Visual C++ 2012 x86...
start /wait "" "D:\WinlatorExtras\vcredist2012_x86.exe" /passive /norestart
call :check_installer
if errorlevel 1 goto install_failed
echo Installing Visual C++ 2012 x64...
start /wait "" "D:\WinlatorExtras\vcredist2012_x64.exe" /passive /norestart
call :check_installer
if errorlevel 1 goto install_failed
echo Installing Visual C++ 2013 x86...
start /wait "" "D:\WinlatorExtras\vcredist2013_x86.exe" /passive /norestart
call :check_installer
if errorlevel 1 goto install_failed
echo Installing Visual C++ 2013 x64...
start /wait "" "D:\WinlatorExtras\vcredist2013_x64.exe" /passive /norestart
call :check_installer
if errorlevel 1 goto install_failed
echo Installing Visual C++ 2015-2022 x86...
start /wait "" "D:\WinlatorExtras\vcredist2015_2017_2019_2022_x86.exe" /passive /norestart
call :check_installer
if errorlevel 1 goto install_failed
echo Installing Visual C++ 2015-2022 x64...
start /wait "" "D:\WinlatorExtras\vcredist2015_2017_2019_2022_x64.exe" /passive /norestart
call :check_installer
if errorlevel 1 goto install_failed

echo Installing DirectX June 2010 runtimes...
start /wait "" "D:\WinlatorExtras\DirectX.exe"
call :check_installer
if errorlevel 1 goto install_failed

echo Installing PhysX...
msiexec /i "D:\WinlatorExtras\PhysX.msi" /quiet /norestart
call :check_installer
if errorlevel 1 goto install_failed

echo Installing Media Foundation files...
"Z:\opt\7z.exe" x "D:\WinlatorExtras\dll.7z" -o"C:\windows" -y
if errorlevel 1 goto install_failed
reg import "D:\WinlatorExtras\mediafoundation.reg" /reg:64
if errorlevel 1 goto install_failed

echo Installing XNA Framework 4.0...
msiexec /i "D:\WinlatorExtras\xnafx40_redist.msi" /quiet /norestart
call :check_installer
if errorlevel 1 goto install_failed

echo.
echo ============================================
echo       ALL ESSENTIALS INSTALLED
echo ============================================
echo Every download passed its size check and each installer returned success.
pause
exit /b 0

:download
set "TARGET=%~1"
set "URL=%~2"
set "EXPECTED=%~3"
set "PART=%TARGET%.part"
if exist "%TARGET%" (
    for %%F in ("%TARGET%") do if "%%~zF"=="%EXPECTED%" exit /b 0
    del /q "%TARGET%" >nul 2>&1
)
if exist "%PART%" del /q "%PART%" >nul 2>&1
"Z:\opt\wget.exe" -q --show-progress -O "%PART%" "%URL%"
if errorlevel 1 goto download_error
if not exist "%PART%" goto download_error
for %%F in ("%PART%") do if not "%%~zF"=="%EXPECTED%" goto download_error
move /y "%PART%" "%TARGET%" >nul
if errorlevel 1 goto download_error
exit /b 0

:download_error
if exist "%PART%" del /q "%PART%" >nul 2>&1
echo DOWNLOAD FAILED: %URL%
exit /b 1

:check_installer
if errorlevel 3010 exit /b 0
if errorlevel 1641 if not errorlevel 1642 exit /b 0
if errorlevel 1638 if not errorlevel 1639 exit /b 0
if errorlevel 1 exit /b 1
exit /b 0

:download_failed
echo.
echo ERROR: A package download failed or its size was incorrect.
echo No installer phase was started. Check the network and retry.
pause
exit /b 1

:install_failed
echo.
echo ERROR: An installer or registry operation failed.
echo The script stopped instead of reporting a false success.
pause
exit /b 1

:setup_failed
echo ERROR: Could not create D:\WinlatorExtras.
pause
exit /b 1
