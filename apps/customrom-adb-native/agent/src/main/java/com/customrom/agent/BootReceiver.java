package com.customrom.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String result = AdbRecovery.apply(context);
        Log.i("CUSTOMROM-Agent", "action=" + intent.getAction() + " result=" + result);
    }
}
