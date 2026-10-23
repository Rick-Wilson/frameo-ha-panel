package com.frameo.hatoggle;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * No UI. Launching the app simply (re)starts the overlay service, then gets out of
 * the way by returning to HOME (the Frameo slideshow).
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, OverlayService.class));
        Intent h = new Intent(Intent.ACTION_MAIN);
        h.addCategory(Intent.CATEGORY_HOME);
        h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(h);
        finish();
    }
}
