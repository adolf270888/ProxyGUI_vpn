package org.proxygui.proxygui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;

/** Small Java entry point used by the Kivy/pyjnius UI. */
public final class ProxyVpnBridge {
    private static final String PREFS = "proxygui_vpn";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";

    private ProxyVpnBridge() {}

    private static void saveEndpoint(Context context, String host, int port) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_HOST, host);
        editor.putInt(KEY_PORT, port);
        editor.apply();
    }

    public static void request(Context context, String host, int port) {
        saveEndpoint(context, host, port);
        Intent intent = new Intent(context, ProxyVpnPermissionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void update(Context context, String host, int port) {
        saveEndpoint(context, host, port);
        // If the first consent dialog is still pending, the Activity will read
        // the latest saved endpoint when the user accepts it.
        if (VpnService.prepare(context) != null) return;
        Intent intent = serviceIntent(context, ProxyVpnService.ACTION_UPDATE);
        intent.putExtra(ProxyVpnService.EXTRA_HOST, host);
        intent.putExtra(ProxyVpnService.EXTRA_PORT, port);
        startService(context, intent);
    }

    public static void pause(Context context) {
        if (VpnService.prepare(context) != null) return;
        startService(context, serviceIntent(context, ProxyVpnService.ACTION_PAUSE));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, ProxyVpnService.class));
    }

    private static Intent serviceIntent(Context context, String action) {
        return new Intent(context, ProxyVpnService.class).setAction(action);
    }

    private static void startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
