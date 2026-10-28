package com.frameo.hatoggle;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Floating toggle + day/night scheduler for the Frameo-as-HA-panel.
 *
 * Button (daytime): single tap toggles HA <-> Frameo photos; long-press (with a
 * "release for settings" cue) opens Settings; drag repositions. On HA, after the
 * configured idle it returns to the slideshow.
 *
 * Night window (configurable): switches to HA and blanks the screen (backlight 0
 * via root + a black overlay). A touch wakes it to HA; after a short idle it
 * blanks again. At day-start it restores brightness and returns to the slideshow.
 */
public class OverlayService extends Service {

    public static final String ACTION_RELOAD = "com.frameo.hatoggle.RELOAD";

    private static final int COLOR_NORMAL   = Color.argb(150, 3, 169, 244);
    private static final int COLOR_PRESSED  = Color.argb(240, 2, 136, 209);
    private static final int COLOR_SETTINGS = Color.argb(245, 76, 175, 80); // green = release for settings

    private WindowManager wm;
    private TextView button;
    private GradientDrawable bg;
    private WindowManager.LayoutParams lp;
    private View blackOverlay;

    private boolean onHA = false;
    private boolean nightActive = false;
    private boolean blanked = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final Runnable modeTick = new Runnable() {
        @Override public void run() {
            reevaluate();
            handler.postDelayed(this, 30_000);
        }
    };
    private final Runnable dayIdle = new Runnable() {
        @Override public void run() {
            if (!nightActive && onHA && !blanked) {
                goHome();
                onHA = false;
                refreshLabel();
            }
        }
    };
    private final Runnable nightReblank = new Runnable() {
        @Override public void run() { if (nightActive) blank(); }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
        addButton();
        // Once per start: keep the display awake (never OS-sleep) + sane brightness,
        // so a Frameo/OS update that resets these self-heals on next launch.
        io.execute(new Runnable() {
            @Override public void run() { RootUtil.primeDisplaySettings(); }
        });
        // Ensure the backlight is on if we're starting during the day.
        if (!computeNight()) setBacklightOffAsync(false);
        handler.post(modeTick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        addButton();
        if (intent != null && ACTION_RELOAD.equals(intent.getAction())) {
            // Settings changed: re-evaluate against the new times immediately.
            if (nightActive && !computeNight()) exitNight();
            reevaluate();
        }
        return START_STICKY;
    }

    // ---- scheduler ----

    private boolean computeNight() {
        if (!Prefs.nightEnabled(this)) return false;
        int ns = Prefs.parseHm(Prefs.nightStart(this));
        int ds = Prefs.parseHm(Prefs.dayStart(this));
        if (ns < 0 || ds < 0) return false;
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        return Prefs.isNight(now, ns, ds);
    }

    private void reevaluate() {
        boolean night = computeNight();
        if (night && !nightActive) enterNight();
        else if (!night && nightActive) exitNight();
    }

    private void enterNight() {
        nightActive = true;
        handler.removeCallbacks(dayIdle);
        if (button != null) button.setVisibility(View.GONE);
        openHA();
        onHA = true;
        blank();
    }

    private void exitNight() {
        nightActive = false;
        handler.removeCallbacks(nightReblank);
        removeBlackOverlay();
        blanked = false;
        setBacklightOffAsync(false);
        if (button != null) { button.setVisibility(View.VISIBLE); }
        goHome();
        onHA = false;
        refreshLabel();
    }

    // ---- blanking ----

    private void blank() {
        if (blanked) return;
        blanked = true;
        handler.removeCallbacks(nightReblank);
        addBlackOverlay();
        setBacklightOffAsync(true);
    }

    private void wakeFromBlank() {
        if (!blanked) return;
        blanked = false;
        removeBlackOverlay();
        setBacklightOffAsync(false);
        armNightReblank();
    }

    private void armNightReblank() {
        handler.removeCallbacks(nightReblank);
        handler.postDelayed(nightReblank, Prefs.nightIdleSec(this) * 1000L);
    }

    private void addBlackOverlay() {
        if (blackOverlay != null) return;
        View v = new View(this);
        v.setBackgroundColor(Color.BLACK);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE);
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) wakeFromBlank();
                return true; // consume the wake touch
            }
        });
        blackOverlay = v;
        wm.addView(blackOverlay, p);
    }

    private void removeBlackOverlay() {
        if (blackOverlay != null) {
            try { wm.removeView(blackOverlay); } catch (Exception ignored) {}
            blackOverlay = null;
        }
    }

    private void setBacklightOffAsync(final boolean off) {
        io.execute(new Runnable() {
            @Override public void run() { RootUtil.setBacklightOff(off); }
        });
    }

    // ---- floating button ----

    private int dp(float v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void addButton() {
        if (button != null) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        TextView b = new TextView(this);
        b.setText(onHA ? Config.LABEL_TO_FRAMEO : Config.LABEL_TO_HA);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(COLOR_NORMAL);
        bg.setStroke(dp(1), Color.argb(190, 255, 255, 255));
        b.setBackground(bg);

        lp = new WindowManager.LayoutParams(
                dp(48), dp(48),
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(12);
        lp.y = dp(12);

        b.setOnTouchListener(new TouchHandler());
        button = b;
        wm.addView(button, lp);
    }

    private void refreshLabel() {
        if (button != null) button.setText(onHA ? Config.LABEL_TO_FRAMEO : Config.LABEL_TO_HA);
    }

    private class TouchHandler implements View.OnTouchListener {
        private int startX, startY;
        private float rawX, rawY;
        private boolean moved, longFired;
        private final int touchSlop = dp(8);
        private final Runnable longPress = new Runnable() {
            @Override public void run() {
                if (!moved) {
                    longFired = true;
                    bg.setColor(COLOR_SETTINGS);
                    button.setScaleX(1.28f); button.setScaleY(1.28f);
                    button.setText(Config.LABEL_SETTINGS); // release to open Settings
                }
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = lp.x; startY = lp.y;
                    rawX = e.getRawX(); rawY = e.getRawY();
                    moved = false; longFired = false;
                    setPressed(true);
                    handler.postDelayed(longPress, Config.LONG_PRESS_MS);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int dx = (int) (e.getRawX() - rawX);
                    int dy = (int) (e.getRawY() - rawY);
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        moved = true;
                        handler.removeCallbacks(longPress);
                        setPressed(false);
                    }
                    if (moved) {
                        lp.x = startX + dx;
                        lp.y = startY + dy;
                        wm.updateViewLayout(button, lp);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPress);
                    setPressed(false);
                    if (moved) { /* was a drag */ }
                    else if (longFired) openSettings();
                    else toggle();
                    refreshLabel();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPress);
                    setPressed(false);
                    refreshLabel();
                    return true;
                case MotionEvent.ACTION_OUTSIDE:
                    // Any touch elsewhere counts as activity (resets the daytime idle timer).
                    if (!nightActive && onHA && !blanked) armDayIdle();
                    return false;
            }
            return false;
        }
    }

    private void setPressed(boolean pressed) {
        bg.setColor(pressed ? COLOR_PRESSED : COLOR_NORMAL);
        float s = pressed ? 1.18f : 1.0f;
        button.setScaleX(s);
        button.setScaleY(s);
    }

    private void toggle() {
        if (onHA) {
            goHome();
            onHA = false;
            handler.removeCallbacks(dayIdle);
        } else {
            openHA();
            onHA = true;
            armDayIdle();
        }
    }

    private void armDayIdle() {
        handler.removeCallbacks(dayIdle);
        handler.postDelayed(dayIdle, Prefs.idleReturnMin(this) * 60_000L);
    }

    private void openSettings() {
        Intent i = new Intent(this, SettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    private void openHA() {
        String url = Prefs.haUrl(this);
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.setPackage(Config.BROWSER_PKG);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("com.android.browser.application_id", getPackageName());
            startActivity(i);
        } catch (Exception ex) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    }

    private void goHome() {
        Intent h = new Intent(Intent.ACTION_MAIN);
        h.addCategory(Intent.CATEGORY_HOME);
        h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(h);
    }

    @SuppressWarnings("deprecation")
    private void startForegroundCompat() {
        Notification n = new Notification.Builder(this)
                .setContentTitle("HA Toggle")
                .setContentText("Tap to switch screens; long-press for settings")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
        startForeground(1, n);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removeBlackOverlay();
        if (button != null && wm != null) {
            try { wm.removeView(button); } catch (Exception ignored) {}
            button = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
