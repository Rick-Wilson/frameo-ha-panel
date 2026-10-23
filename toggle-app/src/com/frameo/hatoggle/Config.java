package com.frameo.hatoggle;

/** Central config for the HA Toggle overlay. */
public final class Config {
    /** Default HA URL the floating button opens. This is a generic placeholder; the
     *  real address (e.g. your HA's LAN IP) is entered on the frame in Settings and
     *  stored in prefs (see {@link Prefs#haUrl}) — it is never committed to source.
     *  The ?kiosk hides HA's header/sidebar; the in-dashboard fullscreen card hides
     *  Firefox's toolbar. */
    public static final String DEFAULT_HA_URL = "http://homeassistant.local:8123/frame-dashboard/home?kiosk";
    /** Browser package used to render HA (Firefox 143, the last Fenix supporting Android 6). */
    public static final String BROWSER_PKG = "org.mozilla.firefox";

    /** Button label when currently on the photos (tap goes to Home Assistant). */
    public static final String LABEL_TO_HA = "HA";
    /** Button label when currently on Home Assistant (tap goes back to Frameo). */
    public static final String LABEL_TO_FRAMEO = "FR";
    /** Button label once a long-press has registered (release to open Settings). */
    public static final String LABEL_SETTINGS = "⚙"; // gear

    /** Hold this long for the button to arm Settings (with a visual cue). */
    public static final long LONG_PRESS_MS = 650L;

    /** Root-controllable backlight power node (rk28_bl): 0 = on, 4 = off (FB_BLANK).
     *  Using bl_power (not brightness) gives a truly dark screen that the Android
     *  framework won't override, while the screen stays "on" so touch still wakes it. */
    public static final String BACKLIGHT_POWER_NODE = "/sys/class/backlight/rk28_bl/bl_power";
    /** Brightness node; we restore a sane value when lighting the screen so a stray
     *  0 can never leave the panel dark with the power on. */
    public static final String BACKLIGHT_NODE = "/sys/class/backlight/rk28_bl/brightness";
    /** Brightness to restore when the screen is lit (day / night wake). */
    public static final int DAY_BRIGHTNESS = 200;

    private Config() {}
}
