@echo off
chcp 65001 >nul
setlocal
call "%~dp0config.bat"
set LOG=%~dp0diagnose.txt
%ADB% connect %D% > "%LOG%" 2>&1
echo ==== VERSION >> "%LOG%"
%ADB% -s %D% shell "dumpsys package com.hadi.tvlock | grep versionName" >> "%LOG%" 2>&1
echo ==== ACCESSIBILITY >> "%LOG%"
%ADB% -s %D% shell settings get secure enabled_accessibility_services >> "%LOG%" 2>&1
echo ==== SCREEN >> "%LOG%"
%ADB% -s %D% shell "dumpsys power | grep mWakefulness=; dumpsys window | grep mCurrentFocus | head -1" >> "%LOG%" 2>&1
echo ==== APP (via adb forward, works without the PIN only from this computer) >> "%LOG%"
%ADB% -s %D% forward tcp:18787 tcp:8787 >> "%LOG%" 2>&1
curl.exe -s -m 10 http://127.0.0.1:18787/debug/info >> "%LOG%" 2>&1
curl.exe -s -m 10 -o "%~dp0diagnose_app_shot.jpg" -w "\nshot HTTP %%{http_code}\n" http://127.0.0.1:18787/debug/shot.jpg >> "%LOG%" 2>&1
%ADB% -s %D% forward --remove tcp:18787 >> "%LOG%" 2>&1
echo ==== DONE >> "%LOG%"
type "%LOG%"
pause
