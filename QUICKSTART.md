# Quick start

Ten minutes from a stock ATOTO S8G1104MS to an `adb shell` over Wi-Fi. Park
the car; the head unit needs to stay on (ACC on) the whole time.

## You need

- The head unit and a computer on the **same private Wi-Fi network** (not a
  guest network, no client isolation). A phone hotspot works.
- `adb` on the computer, from
  [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools).
  If you would rather not set it up yourself, an AI coding agent such as
  [Claude Code](https://claude.com/claude-code) can install it and make the
  connection for you; see [Let Claude Code connect](#let-claude-code-connect).
- **`atoto-s8g1104ms-wifi-adb.apk`** from the
  [latest release](https://github.com/atoqe/atoto-s8g1104ms-wifi-adb/releases/latest).

## 1. Install the app on the head unit

Pick whichever is easiest:

- **USB stick.** Copy the APK to a FAT32 stick, plug it into the head unit, open
  it in the file manager.
- **Head-unit browser.** Open the release page and download the APK.

Android asks to allow installs from that source. Allow it for this install,
then turn it back off.

Upgrading from 1.1.0? Version 1.2.0 is a separate app (new package name and
signing key), so 1.1.0 stays installed next to it. Uninstall the old
`S8 Wi-Fi ADB (1104MS)` 1.1.0 (and upstream's `ATOTO Wi-Fi ADB`, if present)
to avoid two look-alike icons. Uninstalling does not turn Wi-Fi ADB off: the
setting lives in system properties, not in the app.

## 2. Turn on Wi-Fi ADB

1. Open **S8 Wi-Fi ADB (1104MS)**.
2. The status panel should read `FYT ToolkitService: FOUND` and show a
   `Head-unit IPv4` address. No address means Wi-Fi is not connected yet.
3. Tap **ENABLE PERSISTENT WI-FI ADB** and confirm. (Use **ENABLE FOR THIS BOOT
   ONLY** if you want it gone after the next restart.)
4. After a few seconds the status line says one of:

   | Status line | Meaning |
   | --- | --- |
   | `Wi-Fi ADB is listening on port 5555 ...` | Done. Go to step 3. |
   | `... but USB debugging is OFF ...` | ADB is listening, but the RSA prompt will not appear. See [USB debugging](#usb-debugging-is-off). |
   | `Port set to 5555 but adbd is not listening yet` | Tap **REFRESH STATUS** after a few seconds. |
   | `FAILED: ...` | This firmware does not accept the request. Stop and [report it](README.md#compatibility-reports). |

## 3. Connect from the computer

The status panel's `Computer command` line shows the exact command. Run it on
the computer:

```sh
adb connect HEAD_UNIT_IP:5555
```

The head unit asks **Allow USB debugging?** with your computer's RSA key
fingerprint. Tick **Always allow from this computer** and tap **Allow**. Then:

```sh
adb devices -l
adb -s HEAD_UNIT_IP:5555 shell
```

`device` in the list means you are in. `unauthorized` means the prompt was not
approved (or never appeared).

### Let Claude Code connect

Instead of typing the `adb` commands yourself, you can hand the computer side
to [Claude Code](https://claude.com/claude-code) (or a similar AI coding agent
that can run terminal commands). It still uses `adb`, but it installs Platform
Tools if they are missing (on macOS, `brew install --cask
android-platform-tools`), runs the commands, and reads the results for you. Ask
it something like:

> Install adb if it isn't installed, then connect to my head unit at
> HEAD_UNIT_IP:5555 over Wi-Fi and check that `adb devices` shows it as
> `device`.

Use the IP address from the app's status panel. Approve each command it asks to
run, and tap **Allow** on the head unit when the RSA prompt appears: the agent
cannot do that part. Once connected, you can keep asking it to run `adb`
commands for you, such as installing APKs or reading logs. ADB gives whatever
runs it full control of the head unit, so read [SECURITY.md](SECURITY.md) and
review what the agent proposes before approving it.

## USB debugging is off

Android only shows the RSA prompt when USB debugging is on, even for Wi-Fi ADB.

1. Settings → About → tap **Build number** seven times to unlock Developer
   options, then turn on **Developer options → USB debugging**.
2. If that toggle will not stay on, tap **TURN ON USB DEBUGGING AT BOOT** in the
   app and reboot the head unit. It sets `persist.sys.usb.config=adb`, which
   Android 10 reads at boot.

## After a reboot or sleep

Persistent mode survives reboots and ACC off/on. The unit's IP address can
change (a DHCP reservation in your router keeps it fixed), and Wi-Fi takes
30–60 s to come back after the screen turns on. Then just run
`adb connect HEAD_UNIT_IP:5555` again. On networks that support mDNS,
`adb mdns services` lists the unit's address.

If `adb` stops answering after a firmware update, reopen the app and tap
**ENABLE PERSISTENT WI-FI ADB** again.

## Turn it off

Tap **DISABLE WI-FI ADB**. The status line should read
`Wi-Fi ADB is off, now and after reboot.` If you turned on USB debugging at
boot, tap **TURN OFF USB DEBUGGING AT BOOT** as well.

More detail and troubleshooting: [docs/INSTALL.md](docs/INSTALL.md). Before
leaving ADB on, read [SECURITY.md](SECURITY.md).
