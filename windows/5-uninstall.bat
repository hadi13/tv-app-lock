@echo off
chcp 65001 >nul
setlocal
call "%~dp0config.bat"
echo راح يحذف التطبيق وكل إعداداته من الشاشة.
pause
%ADB% connect %D%
rem Restore the original home first, so the screen never goes black.
call "%~dp07-restore-google-home.bat" nopause
%ADB% -s %D% shell "s=$(settings get secure enabled_accessibility_services); o=$(echo \"$s\" | tr ':' '\n' | grep -v com.hadi.tvlock | tr '\n' ':' | sed 's/:$//'); settings put secure enabled_accessibility_services \"$o\""
%ADB% -s %D% uninstall com.hadi.tvlock
pause
