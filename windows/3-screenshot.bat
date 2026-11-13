@echo off
chcp 65001 >nul
setlocal
call "%~dp0config.bat"
%ADB% connect %D% >nul
%ADB% -s %D% exec-out screencap -p > "%~dp0screenshot.png"
echo انحفظت اللقطة: %~dp0screenshot.png
start "" "%~dp0screenshot.png"
