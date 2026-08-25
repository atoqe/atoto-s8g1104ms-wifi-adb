package com.lrehmann.atoto.wifiadb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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
import java.net.NetworkInterface;
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

    private final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();
    private FytAdbController controller;
    private TextView statusText;
    private TextView connectionText;
    private TextView compatibilityText;
    private Button temporaryButton;
    private Button persistentButton;
    private Button disableButton;
    private Button copyButton;
    private String currentAddress;

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

        TextView title = text("ATOTO S8 Wi-Fi ADB", 30, Color.BLACK);
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
        return "TARGET: ATOTO S8 / FYT-based firmware\n"
                + "Tested: S8G2A74MS, Android 10, incremental 33515\n"
                + "This is not a universal Android wireless-debugging app.\n\n"
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

        FytAdbController.Listener listener = new FytAdbController.Listener() {
            @Override
            public void onRequestSent() {
                runOnUiThread(() -> {
                    statusText.setText(disable
                            ? "Disable request sent; the connection should close."
                            : "Request sent. Approve the computer RSA prompt on the head unit.");
                    statusText.postDelayed(() -> {
                        setActionButtonsEnabled(true);
                        refreshStatus();
                    }, 1200L);
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

        if (disable) {
            controller.disablePersistent(listener);
        } else if (persistent) {
            controller.enablePersistent(listener);
        } else {
            controller.enableTemporary(listener);
        }
    }

    private void refreshStatus() {
        statusExecutor.execute(() -> {
            String address = findPreferredIpv4Address();
            String servicePort = readAndroidProperty("service.adb.tcp.port");
            String persistentPort = readAndroidProperty("persist.adb.tcp.port");
            boolean toolkitPresent = controller.isToolkitServicePresent();
            String display = String.format(Locale.US,
                    "FYT ToolkitService: %s\n"
                            + "Head-unit IPv4: %s\n"
                            + "Current ADB TCP port: %s\n"
                            + "Persistent ADB TCP port: %s\n"
                            + "Computer command: %s",
                    toolkitPresent ? "FOUND" : "NOT FOUND",
                    emptyFallback(address, "not found"),
                    emptyFallback(servicePort, "not set"),
                    emptyFallback(persistentPort, "not set"),
                    TextUtils.isEmpty(address)
                            ? "connect Wi-Fi first"
                            : "adb connect " + address + ":" + ADB_PORT);
            runOnUiThread(() -> {
                currentAddress = address;
                connectionText.setText(display);
                copyButton.setEnabled(!TextUtils.isEmpty(address));
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
