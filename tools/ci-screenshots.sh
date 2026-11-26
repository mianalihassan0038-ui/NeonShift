#!/usr/bin/env bash
# Installs the APK on the CI emulator, walks through the main screens and saves screenshots to shots/.
# Taps use the game's layout units: a 9.5 x 17.2 area centred on the screen, y pointing up.
set -u
APK=${1:-out/NeonShift.apk}
mkdir -p shots
adb install -r "$APK"
adb logcat -c

read -r W H < <(adb shell wm size | tail -1 | sed 's/.*: //; s/x/ /')
NAV=$(( H / 20 ))              # rough navigation bar height
VH=$(( H - NAV ))
PPU=$(python3 -c "print(min($W/9.5, $VH/17.2))")
px() { python3 -c "print(int($W/2 + ($1)*$PPU))"; }
py() { python3 -c "print(int($VH/2 - ($1)*$PPU))"; }
tap() { adb shell input tap "$(px "$1")" "$(py "$2")"; sleep "${3:-1.2}"; }
swipe() {
  local cx=$(( W / 2 )) cy=$(( VH / 2 )) d=$(( W / 4 ))
  case $1 in
    R) adb shell input swipe $cx $cy $((cx + d)) $cy 80 ;;
    L) adb shell input swipe $cx $cy $((cx - d)) $cy 80 ;;
    U) adb shell input swipe $cx $cy $cx $((cy - d)) 80 ;;
    D) adb shell input swipe $cx $cy $cx $((cy + d)) 80 ;;
  esac
  sleep 0.6
}
shot() { adb exec-out screencap -p > "shots/$1.png"; echo "saved $1"; }

adb shell am start -W -n com.neonshift.game/.MainActivity
sleep 0.8; shot 01-splash
sleep 3;   shot 02-menu
tap 0 0.7 1.5;  shot 03-level1
swipe R; swipe R; shot 04-level1-moves
tap 3.75 7.45;  shot 05-pause
tap 0 2.9;      # continue
swipe R; swipe R; sleep 1.5; shot 06-victory
tap 0 -1.9 1.5; shot 07-level2
swipe L; sleep 0.5; shot 08-blocked
tap -3.75 7.45; shot 09-world-map
tap -3.75 7.35; shot 10-worlds
tap -3.75 7.35; tap 0 -1.85; shot 11-daily
tap -3.75 7.35; tap 0 -4.45; shot 12-achievements
tap -3.75 7.35; tap 0 -5.75; shot 13-settings
tap -3.75 7.35; tap -3.7 7.3; shot 14-profile

adb logcat -d > shots/logcat.txt
if grep -q "FATAL EXCEPTION" shots/logcat.txt; then
  echo "::error::The app crashed. See logcat.txt in the screenshots artifact."
  grep -A 30 "FATAL EXCEPTION" shots/logcat.txt | head -60
  exit 1
fi
echo "No crash found."
