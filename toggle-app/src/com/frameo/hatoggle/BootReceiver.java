package com.frameo.hatoggle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the overlay after a reboot so the frame needs no USB/computer. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            context.startService(new Intent(context, OverlayService.class));
        }
    }
}
