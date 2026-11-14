@echo off
chcp 65001 >nul
setlocal
call "%~dp0config.bat"
set H=com.hadi.tvlock/.HomeActivity
set LOG=%~dp0set-home.txt
rem Makes the app's home screen the TV's home. Rolls back to Google's home if it fails.
rem يخلي واجهة التطبيق هي الواجهة الرئيسية، ويرجّع واجهة قوقل إذا فشل.
%ADB% connect %D% > "%LOG%" 2>&1
%ADB% -s %D% shell am start -W -n %H% > "%~dp0h_start.txt" 2>&1
findstr /c:"Status: ok" "%~dp0h_start.txt" >nul || ( echo The new home did not start. Nothing was changed. / الواجهة ما اشتغلت، وما تغيّر شي. & type "%~dp0h_start.txt" & pause & exit /b 1 )
%ADB% -s %D% shell cmd package set-home-activity --user 0 %H% >> "%LOG%" 2>&1
for %%P in (com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher com.mitv.tvhome.atv com.google.android.tungsten.setupwraith) do (
  %ADB% -s %D% shell pm disable-user --user 0 %%P >> "%LOG%" 2>&1
)
%ADB% -s %D% shell cmd package set-home-activity --user 0 %H% >> "%LOG%" 2>&1
%ADB% -s %D% shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "%~dp0h_res.txt" 2>&1
findstr /c:"com.hadi.tvlock" "%~dp0h_res.txt" >nul && goto ok
echo Rolling back / ترجيع واجهة قوقل
call "%~dp07-restore-google-home.bat" nopause
pause
exit /b 1
:ok
%ADB% -s %D% shell input keyevent 3
echo Done: the new home screen is set. / تم: الواجهة الجديدة صارت الرئيسية.
del "%~dp0h_start.txt" "%~dp0h_res.txt" >nul 2>&1
pause
