package com.frameo.hatoggle;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * On-frame settings, opened by long-pressing the floating button — so times can be
 * changed at the frame without reconnecting a computer. Built in code (no XML res).
 */
public class SettingsActivity extends Activity {

    private Switch nightEnabled;
    private EditText haUrl, nightStart, dayStart, nightIdleSec, idleReturnMin;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(title("Frame Settings"));

        haUrl = field(root, "Home Assistant URL", Prefs.haUrl(this),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        nightEnabled = new Switch(this);
        nightEnabled.setText("  Night mode (dark screen + HA on touch)");
        nightEnabled.setTextSize(18);
        nightEnabled.setChecked(Prefs.nightEnabled(this));
        nightEnabled.setPadding(0, dp(12), 0, dp(12));
        root.addView(nightEnabled);

        nightStart   = field(root, "Night starts (HH:mm)", Prefs.nightStart(this), InputType.TYPE_CLASS_DATETIME);
        dayStart     = field(root, "Day starts (HH:mm)", Prefs.dayStart(this), InputType.TYPE_CLASS_DATETIME);
        nightIdleSec = field(root, "Night: seconds lit after touch", String.valueOf(Prefs.nightIdleSec(this)), InputType.TYPE_CLASS_NUMBER);
        idleReturnMin= field(root, "Day: minutes on HA before photos return", String.valueOf(Prefs.idleReturnMin(this)), InputType.TYPE_CLASS_NUMBER);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(24), 0, 0);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveAndClose(); }
        });
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        row.addView(save, lw());
        row.addView(cancel, lw());
        root.addView(row);

        setContentView(scroll);
    }

    private void saveAndClose() {
        int ns = Prefs.parseHm(nightStart.getText().toString());
        int ds = Prefs.parseHm(dayStart.getText().toString());
        if (ns < 0 || ds < 0) {
            nightStart.setError(ns < 0 ? "HH:mm" : null);
            dayStart.setError(ds < 0 ? "HH:mm" : null);
            return;
        }
        Prefs.save(this,
                haUrl.getText().toString().trim(),
                nightEnabled.isChecked(),
                nightStart.getText().toString().trim(),
                dayStart.getText().toString().trim(),
                parseInt(nightIdleSec.getText().toString(), 60, 5, 3600),
                parseInt(idleReturnMin.getText().toString(), 5, 1, 1440));

        // Tell the service to re-evaluate immediately.
        Intent i = new Intent(this, OverlayService.class);
        i.setAction(OverlayService.ACTION_RELOAD);
        startService(i);
        finish();
    }

    private static int parseInt(String s, int def, int min, int max) {
        try { int v = Integer.parseInt(s.trim()); return Math.max(min, Math.min(max, v)); }
        catch (Exception e) { return def; }
    }

    private TextView title(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(24);
        tv.setTextColor(Color.BLACK);
        tv.setPadding(0, 0, 0, dp(16));
        return tv;
    }

    private EditText field(ViewGroup parent, String label, String value, int inputType) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15);
        tv.setPadding(0, dp(14), 0, dp(2));
        parent.addView(tv);
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextSize(18);
        e.setInputType(inputType);
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }

    private LinearLayout.LayoutParams lw() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
