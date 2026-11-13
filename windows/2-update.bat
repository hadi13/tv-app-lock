@echo off
chcp 65001 >nul
setlocal
call "%~dp0config.bat"
%ADB% connect %D%
%ADB% -s %D% install -r "%APK%" || (echo فشل التحديث & pause & exit /b 1)
rem إعادة تشغيل خدمة القفل عشان تاخذ أي صلاحيات جديدة
%ADB% -s %D% shell "s=$(settings get secure enabled_accessibility_services); o=$(echo \"$s\" | tr ':' '\n' | grep -v com.hadi.tvlock | tr '\n' ':' | sed 's/:$//'); if [ -z \"$o\" ] || [ \"$o\" = null ]; then settings put secure enabled_accessibility_services %SVC%; else settings put secure enabled_accessibility_services \"$o\"; sleep 2; settings put secure enabled_accessibility_services \"$o:%SVC%\"; fi; settings put secure accessibility_enabled 1"
%ADB% -s %D% shell "dumpsys package com.hadi.tvlock | grep versionName"
echo تم التحديث. إعداداتك والرقم السري محفوظة.
pause
