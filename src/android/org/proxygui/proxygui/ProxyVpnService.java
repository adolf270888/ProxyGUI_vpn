package org.proxygui.proxygui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.ServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import hev.htproxy.TProxyService;

/**
 * Full-device VPN interface backed by HevSocks5Tunnel.
 * The ProxyGUI package is excluded to prevent the upstream SOCKS socket from
 * recursively entering its own VPN. All other installed apps use the TUN.
 * When the upstream disappears, the native packet engine is stopped while the
 * TUN interface stays established: packets are dropped rather than sent direct.
 */
public final class ProxyVpnService extends VpnService {
    private static final String TAG = "ProxyGUI-VPN";
    public static final String ACTION_START = "org.proxygui.proxygui.vpn.START";
    public static final String ACTION_UPDATE = "org.proxygui.proxygui.vpn.UPDATE";
    public static final String ACTION_PAUSE = "org.proxygui.proxygui.vpn.PAUSE";
    public static final String ACTION_STOP = "org.proxygui.proxygui.vpn.STOP";
    public static final String EXTRA_HOST = "proxy_host";
    public static final String EXTRA_PORT = "proxy_port";
    private static final String CHANNEL_ID = "proxygui_vpn";
    private static final int NOTIFICATION_ID = 7401;
    private static final int MTU = 1500;

    private ParcelFileDescriptor tun;
    private boolean engineRunning;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTunnel();
            return START_NOT_STICKY;
        }
        ensureForeground();
        if (!ensureTun()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            pauseEngine();
        } else if (intent == null) {
            // Android may recreate an always-on VPN after process/device restart.
            // Restore the last endpoint; ProxyGUI's watcher can replace it later.
            SharedPreferences prefs = getSharedPreferences("proxygui_vpn", MODE_PRIVATE);
            String host = prefs.getString("host", null);
            int port = prefs.getInt("port", 0);
            if (host != null && port > 0) startEngine(host, port);
            else pauseEngine();
        } else if (ACTION_START.equals(action) || ACTION_UPDATE.equals(action)) {
            String host = intent.getStringExtra(EXTRA_HOST);
            int port = intent.getIntExtra(EXTRA_PORT, 0);
            if (host != null && port > 0) {
                startEngine(host, port);
            } else {
                pauseEngine();
            }
        }
        return START_STICKY;
    }

    private boolean ensureTun() {
        if (tun != null) return true;
        try {
            Builder builder = new Builder()
                    .setSession("ProxyGUI · SOCKS5 VPN")
                    .setMtu(MTU)
                    .setBlocking(false)
                    .addAddress("198.18.0.1", 15)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("198.18.0.2")
                    .addAddress("fc00::1", 126)
                    .addRoute("::", 0);
            // Native SOCKS sockets and ProxyGUI's proxy-health checks must bypass this TUN.
            builder.addDisallowedApplication(getPackageName());
            tun = builder.establish();
            if (tun == null) {
                Log.e(TAG, "VpnService.Builder.establish() returned null");
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Cannot establish TUN", e);
            closeTun();
            return false;
        }
    }

    private synchronized void startEngine(String host, int port) {
        if (tun == null) return;
        if (!host.matches("[0-9.]+") || port < 1 || port > 65535) {
            Log.e(TAG, "Invalid SOCKS5 endpoint");
            pauseEngine();
            return;
        }
        pauseEngine();
        File config = new File(getFilesDir(), "proxygui-hev.yml");
        String yaml = "tunnel:\n" +
                "  mtu: " + MTU + "\n" +
                "  ipv4: 198.18.0.1\n" +
                "  ipv6: 'fc00::1'\n" +
                "  icmp: 'off'\n" +
                "socks5:\n" +
                "  address: '" + host + "'\n" +
                "  port: " + port + "\n" +
                "  udp: 'udp'\n" +
                "mapdns:\n" +
                "  address: 198.18.0.2\n" +
                "  port: 53\n" +
                "  network: 198.18.0.0\n" +
                "  netmask: 255.254.0.0\n" +
                "  cache-size: 10000\n" +
                "misc:\n" +
                "  log-level: warn\n";
        try (FileOutputStream out = new FileOutputStream(config, false)) {
            out.write(yaml.getBytes("UTF-8"));
            out.flush();
            boolean ok = TProxyService.TProxyStartService(config.getAbsolutePath(), tun.getFd());
            engineRunning = ok;
            Log.i(TAG, "HevSocks5Tunnel start " + (ok ? "OK" : "FAILED") + " via " + host + ":" + port);
            if (!ok) pauseEngine();
        } catch (Exception e) {
            Log.e(TAG, "Cannot start HevSocks5Tunnel", e);
            pauseEngine();
        }
    }

    /** Stop forwarding but keep the VPN interface alive: fail-closed during proxy search. */
    private synchronized void pauseEngine() {
        try {
            if (engineRunning || TProxyService.TProxyIsRunning()) {
                TProxyService.TProxyStopService();
            }
        } catch (Throwable e) {
            Log.e(TAG, "Hev stop failed", e);
        } finally {
            engineRunning = false;
        }
    }

    private void ensureForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "ProxyGUI VPN", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent content = open == null ? null : PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("ProxyGUI VPN")
                .setContentText("VPN активен · трафик блокируется при потере прокси")
                .setOngoing(true)
                .setContentIntent(content)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private synchronized void stopTunnel() {
        pauseEngine();
        closeTun();
        stopForeground(true);
        stopSelf();
    }

    private void closeTun() {
        if (tun != null) {
            try { tun.close(); } catch (IOException e) { Log.w(TAG, "TUN close failed", e); }
            tun = null;
        }
    }

    @Override
    public void onRevoke() {
        stopTunnel();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        pauseEngine();
        closeTun();
        super.onDestroy();
    }
}
