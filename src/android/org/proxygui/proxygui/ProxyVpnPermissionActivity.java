package org.proxygui.proxygui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;

/** Shows Android's mandatory VPN consent dialog, then starts the foreground VPN service. */
public final class ProxyVpnPermissionActivity extends Activity {
    private static final int REQUEST_VPN = 7314;
    private String host;
    private int port;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences("proxygui_vpn", MODE_PRIVATE);
        host = prefs.getString("host", null);
        port = prefs.getInt("port", 0);
        Intent consent = VpnService.prepare(this);
        if (consent == null) {
            startTunnel();
        } else {
            startActivityForResult(consent, REQUEST_VPN);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            startTunnel();
        } else {
            finish();
        }
    }

    private void startTunnel() {
        if (host != null && port > 0) {
            Intent service = new Intent(this, ProxyVpnService.class)
                    .setAction(ProxyVpnService.ACTION_START)
                    .putExtra(ProxyVpnService.EXTRA_HOST, host)
                    .putExtra(ProxyVpnService.EXTRA_PORT, port);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        }
        finish();
    }
}
