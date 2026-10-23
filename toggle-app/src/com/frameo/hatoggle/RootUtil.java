package com.frameo.hatoggle;

import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Tiny helper to run shell commands as root via the on-device `su`. */
public final class RootUtil {

    /** Run a command as root; returns combined output, or null on failure. */
    public static String runAsRoot(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            StringBuilder out = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) out.append(line).append('\n');
            p.waitFor();
            return out.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (p != null) p.destroy();
        }
    }

    /**
     * Turn the panel backlight fully off (true) or on (false).
     * When lighting it, also restore a sane brightness + the manual brightness
     * setting, so a stray brightness of 0 can never leave the panel dark.
     */
    public static void setBacklightOff(boolean off) {
        if (off) {
            runAsRoot("echo 4 > " + Config.BACKLIGHT_POWER_NODE);
        } else {
            runAsRoot("settings put system screen_brightness_mode 0; "
                    + "settings put system screen_brightness " + Config.DAY_BRIGHTNESS + "; "
                    + "echo " + Config.DAY_BRIGHTNESS + " > " + Config.BACKLIGHT_NODE + "; "
                    + "echo 0 > " + Config.BACKLIGHT_POWER_NODE);
        }
    }

    private RootUtil() {}
}
