package com.customrom.agent;

import android.content.Context;
import android.provider.Settings;

final class AdbRecovery {
    private AdbRecovery() {}

    static String apply(Context context) {
        try {
            Settings.Global.putInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 1);
            Settings.Global.putInt(context.getContentResolver(), "adb_wifi_enabled", 1);
            int adb = Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            int wifi = Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", 0);
            return "ADB=" + adb + " · Wireless=" + wifi;
        } catch (SecurityException security) {
            return "Permissão necessária: prepare o Agent pelo CUSTOMROM no S23.";
        } catch (Throwable error) {
            return "Falha: " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        }
    }

    static String status(Context context) {
        int adb = Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
        int wifi = Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", 0);
        return "ADB=" + adb + " · Wireless=" + wifi;
    }
}
