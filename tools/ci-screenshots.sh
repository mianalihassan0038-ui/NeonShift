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
sleep 0.8; shot 01-splash
sleep 3;   shot 02-login-reward
tap 0 -3.35 1.5; shot 03-login-claimed
tap 0 -3.9;      shot 04-menu
tap 0 2.3 1.5;   shot 05-level1
swipe R; swipe R; shot 06-level1-moves
tap HW-0.72 HH-0.8; shot 07-pause
tap 0 2.9
# Level 1-1 is three swipes right; extra swipes after the win are ignored.
swipe R; sleep 0.6; swipe R; sleep 0.6; swipe R; sleep 0.6; swipe R; sleep 0.3; shot 08-victory-stars
sleep 1.5; shot 09-victory
tap 0 -1.9 1.5; shot 10-level2
swipe L; sleep 0.2; shot 11-blocked
tap $BACK; shot 12-world-map
tap $BACK; shot 13-worlds
tap $BACK; tap -1.95 -0.4; shot 14-daily
tap $BACK; tap 1.95 -0.4; shot 15-time-attack
tap 0 -3.4 0.6; shot 16-ta-ready
sleep 3; shot 17-ta-puzzle
swipe R; swipe U; swipe L; sleep 0.3; shot 18-ta-moves
tap $BACK; tap $BACK; tap 1.95 -2.3; shot 19-shop
tap 2.05 2.55; shot 20-shop-locked
tap $BACK; tap 0 -4.1; shot 21-achievements
tap $BACK; tap HW-0.75 HH-0.85; shot 22-settings
tap $BACK; tap $BACK; shot 23-profile

timeout 60 adb logcat -d > shots/logcat.txt
if grep -q "FATAL EXCEPTION" shots/logcat.txt; then
  echo "::error::The app crashed. See logcat.txt in the screenshots artifact."
  grep -A 30 "FATAL EXCEPTION" shots/logcat.txt | head -60
  exit 1
fi
echo "No crash found."
