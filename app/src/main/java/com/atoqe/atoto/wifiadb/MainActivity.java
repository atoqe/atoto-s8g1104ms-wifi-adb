package com.atoqe.atoto.wifiadb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class MainActivity extends Activity {
    private static final int ADB_PORT = FytAdbController.ADB_PORT;
    private static final String PORT = Integer.toString(ADB_PORT);
    private static final String TARGET_INCREMENTAL = "46117";
    private static final long POLL_INTERVAL_MS = 5000L;
    private static final String NOT_SUPPORTED_HINT =
            " The head unit accepted the request, but the setting did not change."
                    + " This firmware may not support it; please don't keep retrying.";

    // Dark palette: easy on the eyes in a car and readable in daylight.
    private static final int COLOR_BACKGROUND = 0xFF0F1216;
    private static final int COLOR_SURFACE = 0xFF1A1F26;
    private static final int COLOR_SURFACE_RAISED = 0xFF232A33;
    private static final int COLOR_OUTLINE = 0xFF343D48;
    private static final int COLOR_TEXT = 0xFFF2F4F7;
    private static final int COLOR_TEXT_MUTED = 0xFFA3ADB9;
    private static final int COLOR_ACCENT = 0xFF4DA3FF;
    private static final int COLOR_GREEN = 0xFF3DD68C;
    private static final int COLOR_AMBER = 0xFFF5B942;
    private static final int COLOR_RED = 0xFFFF6B6B;
    private static final int COLOR_GREY = 0xFF6B7684;

    // Three text sizes (plus the address), sized to read at arm's length in a car.
    private static final int TEXT_TITLE = 28;
    private static final int TEXT_BODY = 20;
    private static final int TEXT_SMALL = 18;
    private static final int TEXT_ADDRESS = 52;

    private enum Mode { ALWAYS, UNTIL_REBOOT, OFF }

    /** Snapshot of everything the screen shows, read off the UI thread. */
    private static final class Status {
        String address;
        boolean wifiEnabled;
        String servicePort;
        String persistentPort;
        String usbConfig;
        boolean toolkitPresent;
        boolean listening;
        boolean adbEnabled;

        boolean sameAs(Status other) {
            return other != null
                    && Objects.equals(address, other.address)
                    && wifiEnabled == other.wifiEnabled
                    && Objects.equals(servicePort, other.servicePort)
                    && Objects.equals(persistentPort, other.persistentPort)
                    && Objects.equals(usbConfig, other.usbConfig)
                    && toolkitPresent == other.toolkitPresent
                    && listening == other.listening
                    && adbEnabled == other.adbEnabled;
        }

        boolean hasNetwork() {
            return !TextUtils.isEmpty(address);
        }

        boolean usbDebuggingAtBoot() {
            return usbConfig != null && usbConfig.contains("adb");
        }

        Mode mode() {
            if (PORT.equals(persistentPort)) {
                return Mode.ALWAYS;
            }
            return PORT.equals(servicePort) ? Mode.UNTIL_REBOOT : Mode.OFF;
        }
    }

    /** A line in the result box: what happened after the last action. */
    private static final class Message {
        final int color;
        final String text;

        Message(int color, String text) {
            this.color = color;
            this.text = text;
        }
    }

    /** Turns a post-request snapshot into the result message. */
    private interface Verdict {
        Message describe(Status status);
    }

    /** One of the three selectable Wi-Fi ADB modes. */
    private final class ModeOption {
        final Mode mode;
        final LinearLayout view;
        final View radio;

        ModeOption(Mode mode, String title, String detail) {
            this.mode = mode;
            view = new LinearLayout(MainActivity.this);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setMinimumHeight(dp(84));
            view.setPadding(dp(20), dp(10), dp(20), dp(10));
            view.setOnClickListener(v -> onModeTapped(mode));

            radio = new View(MainActivity.this);
            view.addView(radio, new LinearLayout.LayoutParams(dp(26), dp(26)));

            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(title, TEXT_BODY, COLOR_TEXT, true));
            labels.addView(text(detail, TEXT_SMALL, COLOR_TEXT_MUTED, false));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.leftMargin = dp(18);
            view.addView(labels, params);
            setSelected(false);
        }

        void setSelected(boolean selected) {
            view.setBackground(pressable(rounded(
                    selected ? COLOR_SURFACE_RAISED : COLOR_SURFACE,
                    selected ? COLOR_ACCENT : COLOR_OUTLINE,
                    dp(selected ? 2 : 1), 14)));
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            if (selected) {
                dot.setColor(COLOR_ACCENT);
                dot.setStroke(dp(6), COLOR_SURFACE_RAISED);
            } else {
                dot.setColor(0);
            }
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(dp(2), selected ? COLOR_ACCENT : COLOR_GREY);
            radio.setBackground(new android.graphics.drawable.LayerDrawable(
                    new Drawable[]{dot, ring}));
        }
    }

    /** A "label .......... value" line with a coloured dot. */
    private final class CheckRow {
        final View dot;
        final TextView value;

        CheckRow(LinearLayout parent, String label) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(42));

            dot = new View(MainActivity.this);
            row.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));

            TextView name = text(label, TEXT_BODY, COLOR_TEXT_MUTED, false);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nameParams.leftMargin = dp(14);
            row.addView(name, nameParams);

            value = text("", TEXT_BODY, COLOR_TEXT, true);
            value.setGravity(Gravity.END);
            row.addView(value);
            parent.addView(row);
        }

        void set(int color, String text) {
            dot.setBackground(circle(color));
            value.setText(text);
        }
    }

    private final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();
    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            root.postDelayed(this, POLL_INTERVAL_MS);
        }
    };
    private final Runnable resetCopyLabel = () -> this.copyButton.setText("Copy");
    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    refreshStatus();
                }

                @Override
                public void onLost(Network network) {
                    refreshStatus();
                }

                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                    refreshStatus();
                }
            };

    private FytAdbController controller;
    private View root;
    private View statusDot;
    private TextView headline;
    private TextView headlineDetail;
    private TextView addressText;
    private TextView addressHint;
    private LinearLayout commandBox;
    private TextView commandText;
    private TextView copyButton;
    private TextView wifiSettingsButton;
    private CheckRow wifiRow;
    private CheckRow listeningRow;
    private CheckRow rebootRow;
    private CheckRow approvalRow;
    private ModeOption[] modeOptions;
    private LinearLayout resultBox;
    private View resultBar;
    private TextView resultText;
    private LinearLayout usbRow;
    private FrameLayout usbToggle;
    private View usbToggleThumb;
    private Status lastStatus;
    private boolean busy;
    private boolean networkCallbackRegistered;

    @Override
    protected void attachBaseContext(Context base) {
        // The unit runs at font scale 1.25; this layout is already sized for a car screen.
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = 1f;
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        controller = new FytAdbController(this);
        root = buildContent();
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        root.post(poller);
        try {
            ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
            connectivity.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                    .build(), networkCallback);
            networkCallbackRegistered = true;
        } catch (Exception ignored) {
            // Polling still keeps the screen current.
        }
    }

    @Override
    protected void onPause() {
        root.removeCallbacks(poller);
        if (networkCallbackRegistered) {
            try {
                getSystemService(ConnectivityManager.class)
                        .unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
                // Already unregistered.
            }
            networkCallbackRegistered = false;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            controller.close();
        }
        statusExecutor.shutdownNow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- layout

    private View buildContent() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(COLOR_BACKGROUND);
        page.setPadding(dp(24), dp(16), dp(24), dp(20));

        page.addView(buildHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams columnsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        columnsParams.topMargin = dp(14);
        page.addView(columns, columnsParams);

        columns.addView(buildStatusColumn(), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        rightParams.leftMargin = dp(20);
        columns.addView(buildControlColumn(), rightParams);
        return page;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        header.addView(text("Wi-Fi ADB", TEXT_TITLE, COLOR_TEXT, true), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        boolean tested = TARGET_INCREMENTAL.equals(Build.VERSION.INCREMENTAL);
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(14), dp(8), dp(16), dp(8));
        chip.setBackground(rounded(COLOR_SURFACE, COLOR_OUTLINE, dp(1), 20));
        View chipDot = new View(this);
        chipDot.setBackground(circle(tested ? COLOR_GREEN : COLOR_AMBER));
        chip.addView(chipDot, new LinearLayout.LayoutParams(dp(12), dp(12)));
        TextView chipText = text("Android " + Build.VERSION.RELEASE
                        + "  ·  build " + Build.VERSION.INCREMENTAL
                        + (tested ? "  ·  tested" : "  ·  untested firmware"),
                TEXT_SMALL, COLOR_TEXT_MUTED, false);
        LinearLayout.LayoutParams chipTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipTextParams.leftMargin = dp(10);
        chip.addView(chipText, chipTextParams);
        header.addView(chip);

        TextView refresh = pillButton("↻  Refresh");
        refresh.setOnClickListener(v -> refreshStatus());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        refreshParams.leftMargin = dp(12);
        header.addView(refresh, refreshParams);
        return header;
    }

    private View buildStatusColumn() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(28), dp(24), dp(28), dp(20));
        card.setBackground(rounded(COLOR_SURFACE, COLOR_OUTLINE, dp(1), 18));

        LinearLayout headlineRow = new LinearLayout(this);
        headlineRow.setOrientation(LinearLayout.HORIZONTAL);
        headlineRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        statusDot.setBackground(circle(COLOR_GREY));
        headlineRow.addView(statusDot, new LinearLayout.LayoutParams(dp(22), dp(22)));
        headline = text("Checking…", TEXT_TITLE, COLOR_TEXT, true);
        LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headlineParams.leftMargin = dp(14);
        headlineRow.addView(headline, headlineParams);
        card.addView(headlineRow);

        headlineDetail = text("", TEXT_BODY, COLOR_TEXT_MUTED, false);
        addWithTopMargin(card, headlineDetail, 6);

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_OUTLINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(20);
        card.addView(divider, dividerParams);

        addWithTopMargin(card, sectionLabel("HEAD UNIT ADDRESS"), 18);
        addressText = text("—", TEXT_ADDRESS, COLOR_TEXT, true);
        addWithTopMargin(card, addressText, 2);
        addressHint = text("", TEXT_BODY, COLOR_TEXT_MUTED, false);
        addressHint.setVisibility(View.GONE);
        addWithTopMargin(card, addressHint, 2);

        commandBox = new LinearLayout(this);
        commandBox.setOrientation(LinearLayout.HORIZONTAL);
        commandBox.setGravity(Gravity.CENTER_VERTICAL);
        commandBox.setPadding(dp(18), dp(6), dp(6), dp(6));
        commandBox.setMinimumHeight(dp(64));
        commandBox.setBackground(rounded(COLOR_BACKGROUND, COLOR_OUTLINE, dp(1), 12));
        commandText = text("", TEXT_BODY, COLOR_TEXT, false);
        commandText.setTypeface(Typeface.MONOSPACE);
        commandText.setTextIsSelectable(true);
        commandBox.addView(commandText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        copyButton = pillButton("Copy");
        copyButton.setOnClickListener(v -> copyConnectCommand());
        commandBox.addView(copyButton);
        addWithTopMargin(card, commandBox, 12);

        wifiSettingsButton = pillButton("Open Wi-Fi settings");
        wifiSettingsButton.setOnClickListener(v -> openWifiSettings());
        wifiSettingsButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams wifiParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wifiParams.topMargin = dp(12);
        card.addView(wifiSettingsButton, wifiParams);

        card.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        LinearLayout checks = new LinearLayout(this);
        checks.setOrientation(LinearLayout.VERTICAL);
        checks.addView(sectionLabel("CHECKS"));
        wifiRow = new CheckRow(checks, "Wi-Fi");
        listeningRow = new CheckRow(checks, "Answering on port " + ADB_PORT);
        rebootRow = new CheckRow(checks, "After a reboot");
        approvalRow = new CheckRow(checks, "Computer approval prompt");
        addWithTopMargin(card, checks, 12);
        return card;
    }

    private View buildControlColumn() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        column.addView(sectionLabel("MODE"));
        modeOptions = new ModeOption[]{
                new ModeOption(Mode.ALWAYS, "Always on", "Starts again after every reboot"),
                new ModeOption(Mode.UNTIL_REBOOT, "On until reboot", "Turns off when the unit restarts"),
                new ModeOption(Mode.OFF, "Off", "No wireless ADB connections"),
        };
        for (ModeOption option : modeOptions) {
            addWithTopMargin(column, option.view, 10);
        }

        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.HORIZONTAL);
        resultBox.setBackground(rounded(COLOR_SURFACE, COLOR_OUTLINE, dp(1), 12));
        resultBar = new View(this);
        resultBox.addView(resultBar, new LinearLayout.LayoutParams(
                dp(5), LinearLayout.LayoutParams.MATCH_PARENT));
        resultText = text("", TEXT_BODY, COLOR_TEXT, false);
        resultText.setPadding(dp(16), dp(12), dp(16), dp(12));
        resultBox.addView(resultText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        resultBox.setVisibility(View.GONE);
        addWithTopMargin(column, resultBox, 14);

        column.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        usbRow = new LinearLayout(this);
        usbRow.setOrientation(LinearLayout.HORIZONTAL);
        usbRow.setGravity(Gravity.CENTER_VERTICAL);
        usbRow.setMinimumHeight(dp(72));
        usbRow.setPadding(dp(20), dp(12), dp(20), dp(12));
        usbRow.setBackground(pressable(rounded(COLOR_SURFACE, COLOR_OUTLINE, dp(1), 14)));
        usbRow.setOnClickListener(v -> confirmUsbDebuggingAtBoot());
        LinearLayout usbLabels = new LinearLayout(this);
        usbLabels.setOrientation(LinearLayout.VERTICAL);
        usbLabels.addView(text("USB debugging at boot", TEXT_BODY, COLOR_TEXT, true));
        usbLabels.addView(text("Turn on if adb says \"unauthorized\". Needs a reboot.",
                TEXT_SMALL, COLOR_TEXT_MUTED, false));
        usbRow.addView(usbLabels, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        // Drawn here rather than a platform Switch, which is too small for a car
        // screen and clips when scaled. The whole row is the touch target.
        usbToggle = new FrameLayout(this);
        usbToggleThumb = new View(this);
        usbToggleThumb.setBackground(circle(COLOR_TEXT));
        usbToggle.addView(usbToggleThumb, new FrameLayout.LayoutParams(
                dp(28), dp(28), Gravity.CENTER_VERTICAL | Gravity.START));
        usbToggle.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(dp(64), dp(36));
        toggleParams.leftMargin = dp(16);
        usbRow.addView(usbToggle, toggleParams);
        setUsbToggle(false);
        addWithTopMargin(column, sectionLabel("TROUBLESHOOTING"), 14);
        addWithTopMargin(column, usbRow, 10);

        TextView note = text("Use only on a trusted network.", TEXT_SMALL, COLOR_TEXT_MUTED, false);
        addWithTopMargin(column, note, 12);
        return column;
    }

    // ---------------------------------------------------------------- status

    private void refreshStatus() {
        refreshStatus(null);
    }

    private void refreshStatus(Verdict verdict) {
        try {
            statusExecutor.execute(() -> {
                Status status = new Status();
                status.address = findNetworkAddress();
                status.wifiEnabled = isWifiEnabled();
                Map<String, String> properties = readAndroidProperties();
                status.servicePort = property(properties, "service.adb.tcp.port");
                status.persistentPort = property(properties, "persist.adb.tcp.port");
                status.usbConfig = property(properties, "persist.sys.usb.config");
                status.toolkitPresent = controller.isToolkitServicePresent();
                status.listening = isLocalPortListening(ADB_PORT);
                status.adbEnabled = Settings.Global.getInt(
                        getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    // Polls usually find nothing new; skip rebuilding the views then.
                    if (verdict == null && status.sameAs(lastStatus)) {
                        return;
                    }
                    render(status);
                    if (verdict != null) {
                        showResult(verdict.describe(status));
                        setBusy(false);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            // The activity is being destroyed; network callbacks can still arrive.
        }
    }

    private void render(Status status) {
        lastStatus = status;
        Mode mode = status.mode();

        int color;
        String title;
        String detail;
        if (!status.toolkitPresent) {
            color = COLOR_RED;
            title = "Not available on this unit";
            detail = "The head unit's system service this app needs was not found.";
        } else if (status.listening && status.hasNetwork()) {
            color = COLOR_GREEN;
            title = "Ready to connect";
            detail = mode == Mode.ALWAYS ? "Wi-Fi ADB is on and stays on after reboot."
                    : mode == Mode.UNTIL_REBOOT ? "Wi-Fi ADB is on until the next reboot."
                    : "ADB is answering on port " + ADB_PORT + ".";
        } else if (status.listening) {
            color = COLOR_AMBER;
            title = "Waiting for Wi-Fi";
            detail = "Wi-Fi ADB is on. Connect the head unit to Wi-Fi to use it.";
        } else if (mode != Mode.OFF) {
            color = COLOR_AMBER;
            title = "Starting…";
            detail = "Wi-Fi ADB is set to on but isn't answering yet. If this stays, choose the mode again.";
        } else {
            color = COLOR_GREY;
            title = "Wi-Fi ADB is off";
            detail = "Choose a mode on the right to allow wireless connections.";
        }
        statusDot.setBackground(circle(color));
        headline.setText(title);
        headline.setTextColor(color == COLOR_GREY ? COLOR_TEXT : color);
        headlineDetail.setText(detail);

        if (status.hasNetwork()) {
            addressText.setText(status.address);
            addressText.setTextSize(TEXT_ADDRESS);
            addressText.setTextColor(COLOR_TEXT);
            addressHint.setVisibility(View.GONE);
            commandText.setText(connectCommand(status.address));
            commandBox.setVisibility(View.VISIBLE);
            wifiSettingsButton.setVisibility(View.GONE);
        } else {
            addressText.setText(status.wifiEnabled ? "Not connected to Wi-Fi" : "Wi-Fi is off");
            addressText.setTextSize(TEXT_TITLE);
            addressText.setTextColor(COLOR_AMBER);
            addressHint.setText(status.wifiEnabled
                    ? "Join the same Wi-Fi network as your computer."
                    : "Turn on Wi-Fi and join the same network as your computer.");
            addressHint.setVisibility(View.VISIBLE);
            commandBox.setVisibility(View.GONE);
            wifiSettingsButton.setVisibility(View.VISIBLE);
        }

        if (status.hasNetwork()) {
            wifiRow.set(COLOR_GREEN, "Connected");
        } else {
            wifiRow.set(COLOR_AMBER, status.wifiEnabled ? "Not connected" : "Off");
        }
        listeningRow.set(status.listening ? COLOR_GREEN : mode == Mode.OFF ? COLOR_GREY : COLOR_AMBER,
                status.listening ? "Yes" : "No");
        rebootRow.set(mode == Mode.ALWAYS ? COLOR_GREEN : COLOR_GREY,
                mode == Mode.ALWAYS ? "Stays on" : "Off after reboot");
        approvalRow.set(status.adbEnabled ? COLOR_GREEN : COLOR_AMBER,
                status.adbEnabled ? "Ready" : "May not appear");

        for (ModeOption option : modeOptions) {
            option.setSelected(status.toolkitPresent && option.mode == mode);
        }
        setUsbToggle(status.usbDebuggingAtBoot());
        setControlsEnabled(!busy && status.toolkitPresent);
        if (!status.toolkitPresent && resultBox.getVisibility() != View.VISIBLE) {
            showResult(new Message(COLOR_RED,
                    "This head unit doesn't offer the system service used to switch ADB on,"
                            + " so the controls are disabled."));
        }
    }

    private void setUsbToggle(boolean on) {
        usbToggle.setBackground(rounded(on ? COLOR_ACCENT : COLOR_SURFACE_RAISED,
                on ? COLOR_ACCENT : COLOR_GREY, dp(2), 18));
        ((FrameLayout.LayoutParams) usbToggleThumb.getLayoutParams()).gravity =
                Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START);
        usbToggleThumb.setBackground(circle(on ? COLOR_TEXT : COLOR_GREY));
        usbToggleThumb.requestLayout();
    }

    private void showResult(Message message) {
        resultBar.setBackground(roundedLeft(message.color));
        resultText.setText(message.text);
        resultBox.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        setControlsEnabled(!busy && lastStatus != null && lastStatus.toolkitPresent);
    }

    private void setControlsEnabled(boolean enabled) {
        for (ModeOption option : modeOptions) {
            option.view.setEnabled(enabled);
            option.view.setAlpha(enabled ? 1f : 0.45f);
        }
        usbRow.setEnabled(enabled);
        usbRow.setAlpha(enabled ? 1f : 0.45f);
    }

    // --------------------------------------------------------------- actions

    private void onModeTapped(Mode target) {
        if (busy || lastStatus == null) {
            return;
        }
        boolean healthy = target == Mode.OFF ? !lastStatus.listening : lastStatus.listening;
        if (lastStatus.mode() == target && healthy) {
            return;
        }
        switch (target) {
            case ALWAYS:
            case UNTIL_REBOOT:
                confirmTrustedNetwork(target);
                break;
            case OFF:
                enlarge(new AlertDialog.Builder(this)
                        .setTitle("Turn off Wi-Fi ADB?")
                        .setMessage("Any wireless ADB connection closes now, and it stays off after reboot.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Turn off", (dialog, which) -> applyMode(target))
                        .show());
                break;
        }
    }

    /** Warns before opening ADB to the network, for either "on" mode. */
    private void confirmTrustedNetwork(Mode target) {
        Drawable warning = getDrawable(android.R.drawable.ic_dialog_alert).mutate();
        warning.setTint(COLOR_AMBER);
        String duration = target == Mode.ALWAYS
                ? "Always on: it opens again after every reboot, until you turn it off."
                : "On until reboot: it turns off by itself the next time the unit restarts.";
        enlarge(new AlertDialog.Builder(this)
                .setIcon(warning)
                .setTitle("Use only on trusted Wi-Fi")
                .setMessage("Wi-Fi ADB lets devices on the same Wi-Fi network ask for full"
                        + " control of this head unit. Turn it on only on a network you trust,"
                        + " such as your home Wi-Fi, never public, hotel or shared Wi-Fi."
                        + " Approve only your own computer when the head unit asks.\n\n"
                        + duration)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Turn on", (dialog, which) -> applyMode(target))
                .show());
    }

    private void applyMode(Mode target) {
        setBusy(true);
        showResult(new Message(COLOR_ACCENT, target == Mode.OFF
                ? "Turning Wi-Fi ADB off…" : "Turning Wi-Fi ADB on…"));

        Verdict verdict = status -> {
            if (status.mode() != target) {
                return new Message(COLOR_RED, "That didn't work." + NOT_SUPPORTED_HINT);
            }
            if (target == Mode.OFF) {
                return status.listening
                        ? new Message(COLOR_AMBER, "Turned off, but port " + ADB_PORT
                        + " is still answering. It should close within a few seconds.")
                        : new Message(COLOR_GREEN, "Wi-Fi ADB is off, now and after reboot.");
            }
            if (!status.listening) {
                return new Message(COLOR_AMBER,
                        "Turned on, but ADB isn't answering yet. It usually starts within a few seconds.");
            }
            if (!status.hasNetwork()) {
                return new Message(COLOR_AMBER,
                        "Wi-Fi ADB is on. Connect the head unit to Wi-Fi to use it.");
            }
            if (!status.adbEnabled) {
                return new Message(COLOR_AMBER, "Wi-Fi ADB is on, but USB debugging is off, so"
                        + " the approval prompt may not appear (the computer shows \"unauthorized\")."
                        + " Turn on USB debugging at boot below, then reboot.");
            }
            return new Message(COLOR_GREEN, "Wi-Fi ADB is on. Run the command on your computer,"
                    + " then tap Allow when the head unit asks.");
        };

        FytAdbController.Listener listener = listener(verdict);
        if (target == Mode.ALWAYS) {
            controller.enablePersistent(listener);
        } else if (target == Mode.UNTIL_REBOOT) {
            controller.enableTemporary(listener);
        } else {
            controller.disablePersistent(listener);
        }
    }

    private void confirmUsbDebuggingAtBoot() {
        if (busy || lastStatus == null) {
            return;
        }
        boolean enable = !lastStatus.usbDebuggingAtBoot();
        enlarge(new AlertDialog.Builder(this)
                .setTitle(enable ? "Turn on USB debugging at boot?" : "Turn off USB debugging at boot?")
                .setMessage(enable
                        ? "Use this only if Developer options > USB debugging will not stay on."
                        + " After a reboot USB debugging is on, which lets the head unit ask"
                        + " you to approve your computer."
                        : "USB debugging goes back to the factory setting after the next reboot.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(enable ? "Turn on" : "Turn off",
                        (dialog, which) -> applyUsbDebuggingAtBoot(enable))
                .show());
    }

    private void applyUsbDebuggingAtBoot(boolean enable) {
        setBusy(true);
        showResult(new Message(COLOR_ACCENT, "Saving…"));
        controller.setUsbDebuggingAtBoot(enable, listener(status -> {
            if (status.usbDebuggingAtBoot() == enable) {
                return new Message(COLOR_GREEN, "Saved. Reboot the head unit for it to take effect.");
            }
            return new Message(COLOR_RED, "That didn't work (persist.sys.usb.config is \""
                    + (TextUtils.isEmpty(status.usbConfig) ? "empty" : status.usbConfig) + "\")."
                    + NOT_SUPPORTED_HINT);
        }));
    }

    /** Waits for adbd to restart, then reads everything back and reports the verdict. */
    private FytAdbController.Listener listener(Verdict verdict) {
        return new FytAdbController.Listener() {
            @Override
            public void onRequestSent() {
                runOnUiThread(() -> root.postDelayed(() -> refreshStatus(verdict), 2500L));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    showResult(new Message(COLOR_RED, message));
                    setBusy(false);
                });
            }
        };
    }

    private void copyConnectCommand() {
        if (lastStatus == null || !lastStatus.hasNetwork()) {
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("adb connect command",
                connectCommand(lastStatus.address)));
        copyButton.setText("Copied ✓");
        copyButton.removeCallbacks(resetCopyLabel);
        copyButton.postDelayed(resetCopyLabel, 2000L);
    }

    private static String connectCommand(String address) {
        return "adb connect " + address + ":" + ADB_PORT;
    }

    private void openWifiSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception error) {
            showResult(new Message(COLOR_AMBER, "Open Wi-Fi from the head unit's Settings app."));
        }
    }

    // ------------------------------------------------------------- view kit

    /** Brings a shown dialog's text up to the screen's sizes. */
    private void enlarge(AlertDialog dialog) {
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextSize(TEXT_BODY);
        }
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
            if (dialog.getButton(which) != null) {
                dialog.getButton(which).setTextSize(TEXT_SMALL);
            }
        }
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private TextView sectionLabel(String value) {
        TextView view = text(value, TEXT_SMALL, COLOR_TEXT_MUTED, true);
        view.setLetterSpacing(0.06f);
        return view;
    }

    private TextView pillButton(String label) {
        TextView button = text(label, TEXT_BODY, COLOR_ACCENT, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(52));
        button.setMinWidth(dp(96));
        button.setPadding(dp(22), 0, dp(22), 0);
        button.setBackground(pressable(rounded(COLOR_SURFACE_RAISED, COLOR_OUTLINE, dp(1), 26)));
        return button;
    }

    private GradientDrawable rounded(int fill, int strokeColor, int strokeWidth, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private GradientDrawable roundedLeft(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        float r = dp(12);
        drawable.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        return drawable;
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private Drawable pressable(Drawable base) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), base, null);
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

    // ---------------------------------------------------------------- probes

    /** IPv4 address of the Wi-Fi (or Ethernet) network, or "" when not connected. */
    private String findNetworkAddress() {
        try {
            ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
            for (Network network : connectivity.getAllNetworks()) {
                NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
                if (caps == null
                        || !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                    continue;
                }
                LinkProperties properties = connectivity.getLinkProperties(network);
                if (properties == null) {
                    continue;
                }
                for (LinkAddress linkAddress : properties.getLinkAddresses()) {
                    InetAddress address = linkAddress.getAddress();
                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // Shown as "not connected".
        }
        return "";
    }

    private boolean isWifiEnabled() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            return wifi != null && wifi.isWifiEnabled();
        } catch (Exception ignored) {
            return true;
        }
    }

    /** All system properties from one getprop run, as key -> value. */
    private static Map<String, String> readAndroidProperties() {
        Map<String, String> properties = new HashMap<>();
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getprop")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                // Each line reads "[key]: [value]".
                String line;
                while ((line = reader.readLine()) != null) {
                    int split = line.indexOf("]: [");
                    if (line.startsWith("[") && split > 0 && line.endsWith("]")) {
                        properties.put(line.substring(1, split),
                                line.substring(split + 4, line.length() - 1).trim());
                    }
                }
            }
        } catch (Exception ignored) {
            // Missing properties read as "".
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return properties;
    }

    private static String property(Map<String, String> properties, String key) {
        String value = properties.get(key);
        return value == null ? "" : value;
    }

    private static boolean isLocalPortListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
