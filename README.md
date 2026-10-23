# Frameo → Home Assistant hybrid

Turning a **Frameo HN-DPF1560** digital photo frame (Rockchip rk3368, Android 6.0.1,
rooted, 1 GB RAM, 1920×1080) into a photo-frame **and** Home Assistant wall panel —
non-destructively (sideloaded apps only, no reflashing).

## How it works

- **Photos**: the stock Frameo slideshow stays the default home screen.
- **Home Assistant**: rendered in **Firefox 143** (the last Firefox that installs on
  Android 6; every current browser engine dropped API 23). Points at a lean, kiosk
  dashboard (`/frame-dashboard/home?kiosk`) served by the HA instance.
- **Switching**: a small floating overlay button (the `toggle-app`) sits on top of
  everything.
  - **Single tap** toggles HA ↔ photos (label shows the destination: `HA` / `FR`).
  - **Long-press** (green ⚙ cue) opens an on-frame **Settings** screen.
  - Drag to reposition.
- **Daytime idle**: on HA with no touch for N minutes → back to the slideshow.
- **Night mode** (configurable window, default 20:00–09:00): switches to HA and turns
  the **backlight fully off** (`bl_power`, via root) for a truly dark screen; a touch
  wakes it to HA, then it re-darkens after a short idle. At day-start it restores the
  slideshow and brightness.
- Everything **auto-starts on boot** and needs **no USB/computer** at runtime.

## Layout

- `toggle-app/` — the overlay/scheduler Android app (plain Java, built without Gradle;
  see `toggle-app/build.sh`). Config lives in `src/.../Config.java`.
- `backup/` — a one-time pull of the frame's APK, media, license and app data
  (git-ignored; private).

## HA side

The lean dashboard + a small fullscreen custom card live in the separate
Home-Assistant config repo (`config/frame-dashboard.yaml`,
`config/www/frame-fullscreen-card.js`).

## Device notes

- App package `com.frameo.hatoggle`. Find your frame's ADB serial with `adb devices`.
- Build + install: `toggle-app/build.sh`, then
  `adb install -r toggle-app/build/hatoggle.apk`, grant overlay with
  `adb shell appops set com.frameo.hatoggle SYSTEM_ALERT_WINDOW allow`, start via
  `adb shell am start -n com.frameo.hatoggle/.MainActivity`.
- **Your HA URL is not in the source.** On first run the app defaults to
  `http://homeassistant.local:8123/...`; open Settings on the frame (long-press the
  button) and enter your HA address (e.g. its LAN IP). It's stored in on-device prefs.
