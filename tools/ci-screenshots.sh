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
# HW / HH: half the visible width / height in layout units (top bars sit at HH - 0.85).
CTX="HW=$W/$PPU/2; HH=$VH/$PPU/2"
px() { python3 -c "$CTX; print(int($W/2 + ($1)*$PPU))"; }
py() { python3 -c "$CTX; print(int($VH/2 - ($1)*$PPU))"; }
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
shot() {
  if ! adb get-state >/dev/null 2>&1; then echo "::error::Emulator disconnected before $1"; adb logcat -d > shots/logcat.txt 2>/dev/null; exit 1; fi
  timeout 60 adb exec-out screencap -p > "shots/$1.png" && echo "saved $1"
}

adb shell am start -W -n com.neonshift.game/.MainActivity
BACK="-HW+0.75 HH-0.85"
moves() { for m in "$@"; do swipe "$m"; sleep 0.25; done; }
sleep 0.8; shot 01-splash
sleep 2.6; shot 02-level1-solo          # new players start straight in level 1
moves R R R; sleep 1.3; shot 03-victory
sleep 3.5; shot 04-level2
moves U U R R; sleep 1.9; shot 05-victory-levelup
sleep 3.0; moves U U R R R U; sleep 2.2; shot 06-victory-chest
tap 0.35 -1.75 0.5; shot 07-chest-shaking
sleep 1.2; shot 08-chest-open
tap 0 -3.0 0.4; sleep 3.5; shot 09-level3
moves U U R R R U; sleep 4.6; shot 10-shadow-intro
sleep 1.2; shot 11-shadow-guided
moves R R R; sleep 1.3; shot 12-victory-l4
sleep 3.4; shot 12b-level5
tap HW-0.72 HH-0.8; shot 13-pause
tap 0 2.6
tap $BACK; shot 14-world-map
tap $BACK; shot 15-worlds
tap $BACK; shot 16-login-reward
tap 0 -3.35 1.5; tap 0 -3.9; shot 17-menu
tap -3.3 -4.75; shot 18-daily
tap $BACK; tap 3.3 -4.75; shot 19-shop
tap $BACK; tap HW-2.0 HH-0.85; shot 20-achievements
tap $BACK; tap HW-0.75 HH-0.85; shot 21-settings
tap $BACK; tap $BACK; shot 22-profile

timeout 60 adb logcat -d > shots/logcat.txt
if grep -q "FATAL EXCEPTION" shots/logcat.txt; then
  echo "::error::The app crashed. See logcat.txt in the screenshots artifact."
  grep -A 30 "FATAL EXCEPTION" shots/logcat.txt | head -60
  exit 1
fi
echo "No crash found."
