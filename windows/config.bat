@echo off
rem ===== Edit these two lines only / عدّل هالسطرين فقط =====
rem TV address on your home network / عنوان الشاشة في شبكة البيت (من إعدادات الشاشة: الشبكة والإنترنت)
set TV_IP=192.168.1.100
rem Path to adb (leave as is if Android Studio is installed) / مسار adb. إذا مثبّت Android Studio خله مثل ما هو، أو اكتب المسار الكامل لـ adb.exe
set ADB=adb
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set ADB="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if exist "%~dp0adb\adb.exe" set ADB="%~dp0adb\adb.exe"
rem ============================
set D=%TV_IP%:5555
set APK=%~dp0..\dist\TVLock.apk
if not exist "%APK%" for %%F in ("%~dp0..\dist\TVLock-*.apk") do set APK=%%F
set SVC=com.hadi.tvlock/com.hadi.tvlock.LockService
