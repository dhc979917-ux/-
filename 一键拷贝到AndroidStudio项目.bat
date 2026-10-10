@echo off
chcp 65001 >nul
if "%~1"=="" (
  echo 请先用 Android Studio 新建好空项目，然后把那个项目文件夹直接拖到本脚本图标上
  pause
  exit /b
)
echo 正在把 MyReaderApp（v0.2 多模型版）源代码拷入：%~1
robocopy "%~dp0MyReaderApp\app\src\main" "%~1\app\src\main" /E /NFL /NDL /NJH /NJS
copy /Y "%~dp0MyReaderApp\app\build.gradle.kts" "%~1\app\build.gradle.kts" >nul
if exist "%~1\app\src\main\res\values-night\themes.xml" copy /Y "%~dp0MyReaderApp\app\src\main\res\values\themes.xml" "%~1\app\src\main\res\values-night\themes.xml" >nul
if errorlevel 8 (
  echo 有文件拷贝失败，请截图联系助手排查
) else (
  echo 拷贝完成！回到 Android Studio，如弹出 Sync Now 就点一下，然后直接运行
)
pause
