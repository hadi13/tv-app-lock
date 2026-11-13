@echo off
chcp 65001 >nul
setlocal
call "%~dp0config.bat"
echo == الاتصال بالشاشة %D% (وافق على رسالة السماح في الشاشة إذا طلعت)
%ADB% connect %D%
echo == تثبيت التطبيق
%ADB% -s %D% install -r "%APK%" || goto fail
echo == تشغيل خدمة القفل
%ADB% -s %D% shell "s=$(settings get secure enabled_accessibility_services); case \"$s\" in *com.hadi.tvlock*) echo on ;; null|'') settings put secure enabled_accessibility_services %SVC% ;; *) settings put secure enabled_accessibility_services \"$s:%SVC%\" ;; esac; settings put secure accessibility_enabled 1"
echo == فتح التطبيق على الشاشة لاختيار الرقم السري
%ADB% -s %D% shell am start -n com.hadi.tvlock/.MainActivity
echo.
echo تم. لوحة الجوال: http://%TV_IP%:8787
pause
exit /b 0
:fail
echo فشل التثبيت. تأكد إن تصحيح الأخطاء عبر الشبكة شغال في الشاشة.
pause
exit /b 1
