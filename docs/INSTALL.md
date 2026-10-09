# Installation and connection guide

## What you need

- An ATOTO S8G1104MS (tested: Android 10, incremental `46117`) or a closely
  related FYT head unit. The original method was tested on an S8G2A74MS
  running Android 10, build `QP1A.190711.020`, incremental `33515`.
- The head unit and computer on the same trusted Wi-Fi network.
- Android SDK Platform Tools (`adb`) on the computer.
- `atoto-s8g1104ms-wifi-adb.apk` from this repository's latest GitHub release.

USB debugging through the two accessible ATOTO USB leads did not enumerate as
an ADB device in our setup. USB mode choices such as File Transfer, MIDI, PTP,
and No data transfer did not correct it. This is why the bootstrap is performed
entirely from the head unit, followed by ADB over Wi-Fi.

## 1. Install the bootstrap APK without ADB

Choose one method:

1. Open the latest GitHub release in the head-unit browser, download
   `atoto-s8g1104ms-wifi-adb.apk`, and open it.
2. Copy the APK to removable storage and open it with the ATOTO file manager.

Android may ask you to allow installs from the browser or file manager. Enable
that permission only for the installation, then turn it back off.

From 1.2.0 the app's package name is `com.atoqe.atoto.wifiadb`. Upstream's
`ATOTO Wi-Fi ADB` and this fork's 1.1.0 (both `com.lrehmann.atoto.wifiadb`)
are separate apps that stay installed alongside it; uninstall them to avoid
confusion. Uninstalling an older copy does not turn Wi-Fi ADB off, because the
setting is stored in system properties.

## 2. Enable Wi-Fi ADB

1. Connect the head unit to a trusted Wi-Fi network.
2. Open **S8 Wi-Fi ADB (1104MS)**.
3. Confirm that the app does not say **Not available on this unit**.
4. Under **MODE**, tap **Always on**. Use **On until reboot** if you do not
   want the setting to survive a reboot.
5. Wait a few seconds. The app reads the properties back and probes port 5555,
   then shows **Ready to connect** or exactly what did not apply. The screen
   keeps itself up to date while it is open.
6. Note the IP address shown under **HEAD UNIT ADDRESS** (or **Not connected
   to Wi-Fi** if the unit has no network yet).

Choosing a mode sends the FYT property requests and restarts `adbd`. It does not
root the unit, patch a boot image, modify a firmware partition, or install a
system application.

## 3. Connect from the computer

Install current Android SDK Platform Tools, then run:

```sh
adb connect HEAD_UNIT_IP:5555
adb devices -l
```

Approve the computer's RSA fingerprint on the head unit. The device should
change from `unauthorized` to `device`. A shell is then available with:

```sh
adb -s HEAD_UNIT_IP:5555 shell
```

## 4. Verify persistence

After enabling the persistent option, reboot the head unit normally. Wait for
Wi-Fi to reconnect, then repeat `adb connect HEAD_UNIT_IP:5555`. The IP may
change after a reboot; a DHCP reservation makes repeated development easier.

## Troubleshooting

### Play Protect: `Unsafe app blocked`

Google Play Protect blocks installing apps built for an older Android version.
1.1.0 and 1.2.0 targeted Android 9 and can be blocked this way (`adb install`
reports `INSTALL_FAILED_VERIFICATION_FAILURE`). Install 1.2.1 or later, which
targets Android 10.

### `connection refused`

- Reopen the app and tap **Always on** again.
- Confirm **Answering on port 5555** reads **Yes**.
- Confirm both devices are on the same non-guest network.
- Disable wireless/client isolation in the access point.
- Check the computer firewall and use the head unit's current IP address.

### `unauthorized`

Unlock or foreground the head unit and approve the RSA prompt.

The prompt only appears when Android's USB debugging is on, even for Wi-Fi
ADB. Check `Android USB debugging (adb_enabled)` in the app. If it says `OFF`,
turn on Developer options → USB debugging (tap Build number seven times to
unlock Developer options). If that toggle will not stay on, tap **TURN ON USB
DEBUGGING AT BOOT** and reboot; it sets `persist.sys.usb.config=adb`, which
Android 10 copies into `adb_enabled` at boot.

If USB debugging is on and no prompt is visible, try:

```sh
adb disconnect HEAD_UNIT_IP:5555
adb kill-server
adb start-server
adb connect HEAD_UNIT_IP:5555
```

Do not delete an existing trusted ADB key unless you understand that every
Android device previously paired with that key will ask again.

### The app says `FAILED: ADB port properties did not change`

The FYT service accepted the request but the property stayed the same: this
firmware either ignores command 161 or refuses that property. Do not keep
retrying. Report the firmware line shown at the top of the app.

### `FYT ToolkitService: NOT FOUND` or bind failure

The firmware is not compatible with this technique, the vendor service name
changed, or the service no longer permits an ordinary app to bind. Do not keep
pressing the button and do not flash another model's firmware. Open a GitHub
issue with the ATOTO model, Android version, build display, and incremental
value shown in the app. Do not post serial numbers or Wi-Fi credentials.

### Mac has several interfaces on the same subnet

macOS can choose the wrong source interface when Ethernet, Wi-Fi, a VPN, or a
VM all use overlapping addresses. The optional relay in `tools/` binds the
outbound connection to a specific interface:

```sh
brew install nmap
ATOTO_IP=HEAD_UNIT_IP ATOTO_SOURCE_IF=en0 \
  ./tools/connect-atoto-adb.zsh
adb -s 127.0.0.1:15555 shell
```

Change `en0` to the interface that actually reaches the head unit.

## Disable it

Open the app and tap **Off** under **MODE**. The active connection closes when
`adbd` restarts, and the app reports `Wi-Fi ADB is off, now and after reboot.`
Both ports then read `-1`. (Upstream 1.0.0 tried to clear the persistent port
with an empty value, which this firmware ignores, so ADB came back after the
next reboot; this fork writes `-1`.)

If you turned on USB debugging at boot, switch **USB debugging at boot** off
too.
