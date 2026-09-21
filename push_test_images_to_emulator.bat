@echo off
title Push Test Images to Android Emulator

echo ========================================================
echo  CarGuard AI - Pushing Test Images to Android Emulator
echo ========================================================
echo.

echo [*] Pushing images from server/NewYoloModel/damage_image to /sdcard/Pictures/ ...
adb push server/NewYoloModel/damage_image/. /sdcard/Pictures/

echo.
echo [*] Refreshing Android media scanner...
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/ >nul 2>&1

echo.
echo ========================================================
echo [OK] All test images have been uploaded to emulator!
echo You can now open the App and click [🖼️ 選測試圖] to pick them.
echo ========================================================
echo.
pause
