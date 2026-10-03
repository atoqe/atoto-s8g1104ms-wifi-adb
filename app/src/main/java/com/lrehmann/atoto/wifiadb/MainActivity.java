package com.lrehmann.atoto.wifiadb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int ADB_PORT = 5555;
    private static final int COLOR_ATOTO_BLUE = Color.rgb(20, 91, 150);
    private static final int COLOR_PANEL = Color.rgb(235, 239, 243);
    private static final String TARGET_MODEL = "S8G1104MS";
    private static final String TARGET_INCREMENTAL = "46117";
    private static final String NOT_SUPPORTED_HINT =
            " The FYT service accepted the request but the property did not change."
                    + " Either this unit runs different firmware or Android refused that property."
                    + " Do not keep retrying; note the firmware line above and stop here.";

    /** Snapshot of everything the status panel shows, read off the UI thread. */
    private static final class Status {
        String address;
        String servicePort;
        String persistentPort;
        String usbConfig;
        boolean toolkitPresent;
        boolean listening;
        boolean adbEnabled;

        boolean usbDebuggingAtBoot() {
            return usbConfig != null && usbConfig.contains("adb");
        }
    }

    /** Turns a post-request snapshot into the message shown in the status line. */
    private interface Verdict {
        String describe(Status status);
    }

    private final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();
    private FytAdbController controller;
    private TextView statusText;
    private TextView connectionText;
    private TextView compatibilityText;
    private Button temporaryButton;
    private Button persistentButton;
    private Button disableButton;
    private Button usbBootButton;
    private Button copyButton;
    private String currentAddress;
    private boolean usbDebuggingAtBoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        controller = new FytAdbController(this);
        setContentView(buildContent());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            controller.close();
        }
        statusExecutor.shutdownNow();
        super.onDestroy();
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(30), dp(22), dp(30), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("ATOTO S8 Wi-Fi ADB (" + TARGET_MODEL + ")", 30, Color.BLACK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = text(
                "Enable legacy ADB-over-TCP through the FYT vendor service. No root or boot-image patching.",
                17, Color.DKGRAY);
        addWithTopMargin(root, subtitle, 4);

        compatibilityText = panelText(15);
        compatibilityText.setText(buildCompatibilityText());
        addWithTopMargin(root, compatibilityText, 16);

        connectionText = panelText(18);
        connectionText.setText("Checking FYT service, Wi-Fi address, and ADB properties...");
        addWithTopMargin(root, connectionText, 12);

        statusText = text("Ready.", 18, COLOR_ATOTO_BLUE);
        statusText.setMinHeight(dp(42));
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        addWithTopMargin(root, statusText, 10);

        persistentButton = actionButton("ENABLE PERSISTENT WI-FI ADB");
        persistentButton.setOnClickListener(v -> confirmPersistentEnable());
        addWithTopMargin(root, persistentButton, 8);

        temporaryButton = actionButton("ENABLE FOR THIS BOOT ONLY");
        temporaryButton.setOnClickListener(v -> runRequest(false, false));
        addWithTopMargin(root, temporaryButton, 8);

        disableButton = actionButton("DISABLE WI-FI ADB");
        disableButton.setOnClickListener(v -> confirmDisable());
        addWithTopMargin(root, disableButton, 8);

        usbBootButton = actionButton("TURN ON USB DEBUGGING AT BOOT");
        usbBootButton.setOnClickListener(v -> confirmUsbDebuggingAtBoot());
        addWithTopMargin(root, usbBootButton, 8);

        LinearLayout helperRow = new LinearLayout(this);
        helperRow.setOrientation(LinearLayout.HORIZONTAL);
        addWithTopMargin(root, helperRow, 8);

        copyButton = actionButton("COPY ADB CONNECT COMMAND");
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(v -> copyConnectCommand());
        helperRow.addView(copyButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button refreshButton = actionButton("REFRESH STATUS");
        refreshButton.setOnClickListener(v -> refreshStatus());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        refreshParams.leftMargin = dp(8);
        helperRow.addView(refreshButton, refreshParams);

        TextView warning = panelText(14);
        warning.setText("Security: use only on a trusted network. Android should require RSA approval on the head unit, but verify that prompt before relying on it. Disable persistent ADB when you no longer need it.");
        addWithTopMargin(root, warning, 14);

        return scrollView;
    }

    private String buildCompatibilityText() {
        return "TARGET: ATOTO " + TARGET_MODEL + ", Android 10, incremental " + TARGET_INCREMENTAL + "\n"
                + "Original method tested on S8G2A74MS, incremental 33515.\n"
                + "Bridge checked against " + TARGET_MODEL + " firmware APP20251124; the app still verifies each result.\n\n"
                + Build.MANUFACTURER + " " + Build.MODEL
                + " | Android " + Build.VERSION.RELEASE
                + " | " + Build.DISPLAY
                + " | incremental " + Build.VERSION.INCREMENTAL;
    }

    private void confirmPersistentEnable() {
        new AlertDialog.Builder(this)
                .setTitle("Enable persistent Wi-Fi ADB?")
                .setMessage("Port 5555 will be requested after future boots. Keep this head unit on a trusted network and approve only your computer's RSA key.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Enable", (dialog, which) -> runRequest(true, false))
                .show();
    }

    private void confirmUsbDebuggingAtBoot() {
        boolean enable = !usbDebuggingAtBoot;
        new AlertDialog.Builder(this)
                .setTitle(enable ? "Turn on USB debugging at boot?" : "Turn off USB debugging at boot?")
                .setMessage(enable
                        ? "Use this only if Developer options > USB debugging will not stay on. It sets persist.sys.usb.config=adb; after a reboot Android turns USB debugging on, which is what makes the RSA prompt appear for Wi-Fi ADB."
                        : "Sets persist.sys.usb.config back to the factory value (none). Takes effect after a reboot.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(enable ? "Turn on" : "Turn off",
                        (dialog, which) -> runUsbBootRequest(enable))
                .show();
    }

    private void runUsbBootRequest(boolean enable) {
        setActionButtonsEnabled(false);
        statusText.setText("Requesting persist.sys.usb.config=" + (enable ? "adb" : "none") + "...");
        controller.setUsbDebuggingAtBoot(enable, listener(false, status -> {
            if (status.usbDebuggingAtBoot() == enable) {
                return "Saved (persist.sys.usb.config=" + status.usbConfig
                        + "). Reboot the head unit for it to take effect.";
            }
            return "FAILED: persist.sys.usb.config is still \""
                    + emptyFallback(status.usbConfig, "empty") + "\"." + NOT_SUPPORTED_HINT;
        }));
    }

    private void confirmDisable() {
        new AlertDialog.Builder(this)
                .setTitle("Disable Wi-Fi ADB?")
                .setMessage("The current wireless ADB connection will close.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Disable", (dialog, which) -> runRequest(false, true))
                .show();
    }

    private void runRequest(boolean persistent, boolean disable) {
        setActionButtonsEnabled(false);
        statusText.setText(disable
                ? "Disabling Wi-Fi ADB and restarting adbd..."
                : persistent
                ? "Requesting persistent Wi-Fi ADB from the FYT service..."
                : "Requesting temporary Wi-Fi ADB from the FYT service...");

        String port = Integer.toString(ADB_PORT);
        Verdict verdict;
        if (disable) {
            verdict = status -> {
                if (port.equals(status.persistentPort) || port.equals(status.servicePort)) {
                    return "FAILED: ADB port properties are still " + ADB_PORT + "." + NOT_SUPPORTED_HINT;
                }
                return status.listening
                        ? "Disable sent, but port " + ADB_PORT + " is still listening. Press REFRESH STATUS in a few seconds."
                        : "Wi-Fi ADB is off, now and after reboot.";
            };
        } else {
            verdict = status -> {
                boolean portSet = port.equals(status.servicePort)
                        && (!persistent || port.equals(status.persistentPort));
                if (!portSet) {
                    return "FAILED: ADB port properties did not change." + NOT_SUPPORTED_HINT;
                }
                if (!status.listening) {
                    return "Port set to " + ADB_PORT + " but adbd is not listening yet."
                            + " Press REFRESH STATUS in a few seconds.";
                }
                String ok = "Wi-Fi ADB is listening on port " + ADB_PORT + ". Run the adb connect command on the computer";
                return status.adbEnabled
                        ? ok + " and approve the RSA prompt here."
                        : ok + ", but USB debugging is OFF, so the RSA prompt may not appear (adb shows"
                        + " 'unauthorized'). Turn on Developer options > USB debugging, or use"
                        + " TURN ON USB DEBUGGING AT BOOT and reboot.";
            };
        }

        FytAdbController.Listener listener = listener(disable, verdict);
        if (disable) {
            controller.disablePersistent(listener);
        } else if (persistent) {
            controller.enablePersistent(listener);
        } else {
            controller.enableTemporary(listener);
        }
    }

    /** Waits for adbd to restart, then reads everything back and reports the verdict. */
    private FytAdbController.Listener listener(boolean disable, Verdict verdict) {
        return new FytAdbController.Listener() {
            @Override
            public void onRequestSent() {
                runOnUiThread(() -> {
                    statusText.setText(disable
                            ? "Disable request sent; checking..."
                            : "Request sent; checking the result...");
                    statusText.postDelayed(() -> refreshStatus(verdict), 2500L);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    statusText.setText("FAILED: " + message);
                    setActionButtonsEnabled(true);
                });
            }
        };
    }

    private void refreshStatus() {
        refreshStatus(null);
    }

    private void refreshStatus(Verdict verdict) {
        statusExecutor.execute(() -> {
            Status status = new Status();
            status.address = findPreferredIpv4Address();
            status.servicePort = readAndroidProperty("service.adb.tcp.port");
            status.persistentPort = readAndroidProperty("persist.adb.tcp.port");
            status.usbConfig = readAndroidProperty("persist.sys.usb.config");
            status.toolkitPresent = controller.isToolkitServicePresent();
            status.listening = isLocalPortListening(ADB_PORT);
            status.adbEnabled = Settings.Global.getInt(
                    getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
            String display = String.format(Locale.US,
                    "FYT ToolkitService: %s\n"
                            + "Head-unit IPv4: %s\n"
                            + "Current ADB TCP port: %s\n"
                            + "Persistent ADB TCP port: %s\n"
                            + "adbd listening on %d: %s\n"
                            + "Android USB debugging (adb_enabled): %s\n"
                            + "USB debugging at boot (persist.sys.usb.config): %s\n"
                            + "Computer command: %s",
                    status.toolkitPresent ? "FOUND" : "NOT FOUND",
                    emptyFallback(status.address, "not found"),
                    emptyFallback(status.servicePort, "not set"),
                    emptyFallback(status.persistentPort, "not set"),
                    ADB_PORT,
                    status.listening ? "YES" : "NO",
                    status.adbEnabled ? "ON" : "OFF",
                    emptyFallback(status.usbConfig, "not set"),
                    TextUtils.isEmpty(status.address)
                            ? "connect Wi-Fi first"
                            : "adb connect " + status.address + ":" + ADB_PORT);
            runOnUiThread(() -> {
                currentAddress = status.address;
                usbDebuggingAtBoot = status.usbDebuggingAtBoot();
                connectionText.setText(display);
                copyButton.setEnabled(!TextUtils.isEmpty(status.address));
                usbBootButton.setText(usbDebuggingAtBoot
                        ? "TURN OFF USB DEBUGGING AT BOOT"
                        : "TURN ON USB DEBUGGING AT BOOT");
                if (verdict != null) {
                    statusText.setText(verdict.describe(status));
                    setActionButtonsEnabled(true);
                }
            });
        });
    }

    private void copyConnectCommand() {
        if (TextUtils.isEmpty(currentAddress)) {
            return;
        }
        String command = "adb connect " + currentAddress + ":" + ADB_PORT;
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("ATOTO ADB command", command));
        statusText.setText("Copied: " + command);
    }

    private void setActionButtonsEnabled(boolean enabled) {
        temporaryButton.setEnabled(enabled);
        persistentButton.setEnabled(enabled);
        disableButton.setEnabled(enabled);
        usbBootButton.setEnabled(enabled);
    }

    private TextView panelText(int sizeSp) {
        TextView view = text("", sizeSp, Color.rgb(30, 36, 42));
        view.setBackgroundColor(COLOR_PANEL);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setTextIsSelectable(true);
        return view;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setMinHeight(dp(54));
        return button;
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private void addWithTopMargin(LinearLayout parent, View child, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String readAndroidProperty(String key) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getprop", key)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                return value == null ? "" : value.trim();
            }
        } catch (Exception ignored) {
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static boolean isLocalPortListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String findPreferredIpv4Address() {
        try {
            List<NetworkInterface> interfaces =
                    Collections.list(NetworkInterface.getNetworkInterfaces());
            List<NetworkInterface> ordered = new ArrayList<>();
            for (String preferred : new String[]{"wlan0", "eth0"}) {
                for (NetworkInterface item : interfaces) {
                    if (preferred.equals(item.getName())) {
                        ordered.add(item);
                    }
                }
            }
            for (NetworkInterface item : interfaces) {
                if (!ordered.contains(item)) {
                    ordered.add(item);
                }
            }
            for (NetworkInterface item : ordered) {
                if (!item.isUp() || item.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = item.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // The UI will explain that a Wi-Fi address was not found.
        }
        return "";
    }

    private static String emptyFallback(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }
}
