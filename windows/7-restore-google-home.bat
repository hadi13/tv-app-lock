@echo off
chcp 65001 >nul
setlocal
call "%~dp0config.bat"
rem Brings back the TV's original home screen. Run this BEFORE uninstalling the app.
rem يرجّع واجهة الشاشة الأصلية. شغّله قبل حذف التطبيق.
%ADB% connect %D% >nul
for %%P in (com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher com.mitv.tvhome.atv com.google.android.tungsten.setupwraith) do (
  %ADB% -s %D% shell pm enable %%P >nul 2>&1
)
%ADB% -s %D% shell "c=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME com.google.android.apps.tv.launcherx | tail -n 1); case \"$c\" in */*) cmd package set-home-activity --user 0 $c ;; esac"
%ADB% -s %D% shell input keyevent 3
echo Original home restored. / رجعت الواجهة الأصلية.
if not "%1"=="nopause" pause
