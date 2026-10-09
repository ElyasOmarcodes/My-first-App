#!/usr/bin/env bash
# Opens every screen of the debug build on the CI emulator, in light and in
# dark, and saves compact JPEG screenshots into .build-outputs/screens.
set -u
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=$(grep -oE 'applicationId = "[^"]+"' app/build.gradle.kts | cut -d'"' -f2)
NS=com.elyas.multiling
OUT=.build-outputs/screens
mkdir -p "$OUT" && rm -f "$OUT"/*

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
adb install -r -g "$APK"

IM=""
command -v magick >/dev/null && IM=magick
[ -z "$IM" ] && command -v convert >/dev/null && IM=convert
# no ImageMagick on the runner: fall back to Pillow
PY=""
if [ -z "$IM" ]; then
  python3 -c "import PIL" 2>/dev/null || pip3 install -q pillow >/dev/null 2>&1
  python3 -c "import PIL" 2>/dev/null && PY=1
fi

shot() {   # shot <name> [seconds to settle]
  sleep "${2:-2.5}"
  adb exec-out screencap -p > "$OUT/$1.png"
  if [ -n "$IM" ]; then
    $IM "$OUT/$1.png" -resize 540x -quality 84 "$OUT/$1.jpg" && rm -f "$OUT/$1.png"
  elif [ -n "$PY" ]; then
    python3 -c "import sys;from PIL import Image;i=Image.open(sys.argv[1]).convert('RGB');i=i.resize((540,round(i.height*540/i.width)),Image.LANCZOS);i.save(sys.argv[2],quality=84,optimize=True)" "$OUT/$1.png" "$OUT/$1.jpg" && rm -f "$OUT/$1.png"
  fi
}
open() {   # open <Activity> [am extras...]
  local act=$1; shift
  adb shell am start -W -n "$PKG/$NS.$act" "$@" >/dev/null 2>&1
}

# ---- first run: the language sheet, before any preference exists
adb shell cmd uimode night no
open MainActivity
shot 00_first_run_light 3.5

# ---- from here on: language chosen (Pashto), everything else default
cat > /tmp/prefs.xml <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="app_lang">ps</string>
    <boolean name="app_lang_chosen" value="true" />
</map>
EOF
adb shell am force-stop "$PKG"
adb push /tmp/prefs.xml /data/local/tmp/hk_prefs.xml >/dev/null
adb shell chmod 644 /data/local/tmp/hk_prefs.xml
adb shell run-as "$PKG" mkdir -p shared_prefs
adb shell run-as "$PKG" cp /data/local/tmp/hk_prefs.xml "shared_prefs/${PKG}_preferences.xml"

for mode in light dark; do
  if [ "$mode" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  adb shell am force-stop "$PKG"
  sleep 1
  open SplashActivity;                                shot "${mode}_01_splash" 0.6
  sleep 2
  open MainActivity --es tab home;                    shot "${mode}_02_home"
  adb shell input swipe 540 1700 540 500 400;         shot "${mode}_02b_home_scrolled" 1.5
  open MainActivity --es tab shortcuts;               shot "${mode}_03_shortcuts"
  open MainActivity --es tab settings;                shot "${mode}_04_settings"
  open MainActivity --es tab about;                   shot "${mode}_05_about"
  adb shell input swipe 540 1800 540 450 400;         shot "${mode}_05b_about_contact" 1.5
  adb shell input swipe 540 1800 540 450 400;         shot "${mode}_05c_about_app" 1.5
  open SettingsActivity --es open_screen look;        shot "${mode}_06_look"
  open SettingsActivity --es open_screen typing;      shot "${mode}_07_typing"
  open SettingsActivity --es open_screen colors;      shot "${mode}_08_colors"
  open SettingsActivity --es open_screen sizes;       shot "${mode}_10_sizes"
  open SettingsActivity --es open_screen feedback;    shot "${mode}_11_feedback"
  open PrivacyActivity;                               shot "${mode}_09_privacy"
done
adb shell cmd uimode night no
ls -la "$OUT"
