package com.frameo.hatoggle;

import android.content.Context;
import android.content.SharedPreferences;

/** User-editable settings (from SettingsActivity), read by OverlayService. */
public final class Prefs {
    private static final String FILE = "hatoggle";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** HA URL the button opens; set on the frame in Settings (kept out of source). */
    public static String  haUrl(Context c)        { return get(c).getString("ha_url", Config.DEFAULT_HA_URL); }
    public static boolean nightEnabled(Context c) { return get(c).getBoolean("night_enabled", true); }
    public static String  nightStart(Context c)   { return get(c).getString("night_start", "20:00"); }
    public static String  dayStart(Context c)     { return get(c).getString("day_start", "09:00"); }
    /** Seconds the screen stays lit after a night wake before blanking again. */
    public static int     nightIdleSec(Context c) { return get(c).getInt("night_idle_sec", 60); }
    /** Minutes on HA with no touch (daytime) before returning to the slideshow. */
    public static int     idleReturnMin(Context c){ return get(c).getInt("idle_return_min", 5); }

    public static void save(Context c, String haUrl, boolean nightEnabled, String nightStart,
                            String dayStart, int nightIdleSec, int idleReturnMin) {
        get(c).edit()
            .putString("ha_url", haUrl)
            .putBoolean("night_enabled", nightEnabled)
            .putString("night_start", nightStart)
            .putString("day_start", dayStart)
            .putInt("night_idle_sec", nightIdleSec)
            .putInt("idle_return_min", idleReturnMin)
            .apply();
    }

    /** True if `now` (minutes since midnight) is within [nightStart, dayStart), wrapping midnight. */
    public static boolean isNight(int nowMin, int nightStartMin, int dayStartMin) {
        if (nightStartMin == dayStartMin) return false;
        if (nightStartMin < dayStartMin) return nowMin >= nightStartMin && nowMin < dayStartMin;
        return nowMin >= nightStartMin || nowMin < dayStartMin; // wraps midnight
    }

    /** Parse "HH:mm" to minutes since midnight; -1 on bad input. */
    public static int parseHm(String s) {
        try {
            String[] p = s.trim().split(":");
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) { return -1; }
    }

    private Prefs() {}
}
