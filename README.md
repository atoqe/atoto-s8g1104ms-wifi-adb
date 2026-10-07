# ATOTO S8G1104MS Wi-Fi ADB

[![Android build](https://github.com/atoqe/atoto-s8g1104ms-wifi-adb/actions/workflows/android.yml/badge.svg)](https://github.com/atoqe/atoto-s8g1104ms-wifi-adb/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A small bootstrap app that turns on Android Debug Bridge (ADB) over Wi-Fi on an
ATOTO **S8G1104MS** head unit. It asks the FYT vendor service that already
ships in the firmware to open port 5555; it needs **no root, no Magisk, no
patched boot image, and no working USB ADB**.

This is a fork of [lrehmann/atoto-adb-via-wifi](https://github.com/lrehmann/atoto-adb-via-wifi),
which found the method on an S8G2A74MS. This fork fixes the disable action,
verifies every request on the unit, and is checked against the S8G1104MS
firmware. All credit for the original technique goes to lrehmann.

> [!WARNING]
> Experimental, vendor-specific software. Not an official ATOTO tool. It uses
> an undocumented FYT binder interface that a firmware update can change or
> remove. ADB gives a paired computer full control of the head unit: read
> [SECURITY.md](SECURITY.md) first.

## Tested on

| Model | Android | Firmware | Result |
| --- | --- | --- | --- |
| S8G1104MS | 10 (API 29) | incremental `46117`, `com.syu.ms` 25.1119 (APP20251124 / System20251117) | Works (this fork) |
| S8G2A74MS | 10 | `QP1A.190711.020`, incremental `33515` | Works (upstream 1.0.0) |

Other S8 models and FYT units may work: the app checks each step and tells you
exactly what did not apply. Please report results (see [Compatibility reports](#compatibility-reports)).

## Quick start

Ten minutes from a stock ATOTO S8G1104MS to an `adb shell` over Wi-Fi. Park
the car; the head unit needs to stay on (ACC on) the whole time.

### You need

- The head unit and a computer on the **same private Wi-Fi network** (not a
  guest network, no client isolation). A phone hotspot works.
- [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools)
  (`adb`) on the computer.
- **`atoto-s8g1104ms-wifi-adb.apk`** from the
  [latest release](https://github.com/atoqe/atoto-s8g1104ms-wifi-adb/releases/latest).

### 1. Install the app on the head unit

Pick whichever is easiest:

- **USB stick.** Copy the APK to a FAT32 stick, plug it into the head unit, open
  it in the file manager.
- **Head-unit browser.** Open the release page and download the APK.
- **Local web server.** On the computer, in the folder holding the APK:

  ```sh
  python3 -m http.server 8765
  ```

  then browse to `http://COMPUTER_IP:8765/atoto-s8g1104ms-wifi-adb.apk` on the
  head unit.

Android asks to allow installs from that source. Allow it for this install,
then turn it back off.

Upgrading from 1.1.0? Version 1.2.0 is a separate app (new package name and
signing key), so 1.1.0 stays installed next to it. Uninstall the old
`S8 Wi-Fi ADB (1104MS)` 1.1.0 (and upstream's `ATOTO Wi-Fi ADB`, if present)
to avoid two look-alike icons. Uninstalling does not turn Wi-Fi ADB off: the
setting lives in system properties, not in the app.

### 2. Turn on Wi-Fi ADB

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
   | `FAILED: ...` | This firmware does not accept the request. Stop and [report it](#compatibility-reports). |

### 3. Connect from the computer

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

### USB debugging is off

Android only shows the RSA prompt when USB debugging is on, even for Wi-Fi ADB.

1. Settings → About → tap **Build number** seven times to unlock Developer
   options, then turn on **Developer options → USB debugging**.
2. If that toggle will not stay on, tap **TURN ON USB DEBUGGING AT BOOT** in the
   app and reboot the head unit. It sets `persist.sys.usb.config=adb`, which
   Android 10 reads at boot.

### After a reboot or sleep

Persistent mode survives reboots and ACC off/on. The unit's IP address can
change (a DHCP reservation in your router keeps it fixed), and Wi-Fi takes
30–60 s to come back after the screen turns on. Then just run
`adb connect HEAD_UNIT_IP:5555` again. On networks that support mDNS,
`adb mdns services` lists the unit's address.

If `adb` stops answering after a firmware update, reopen the app and tap
**ENABLE PERSISTENT WI-FI ADB** again.

### Turn it off

Tap **DISABLE WI-FI ADB**. The status line should read
`Wi-Fi ADB is off, now and after reboot.` If you turned on USB debugging at
boot, tap **TURN OFF USB DEBUGGING AT BOOT** as well.

More detail and troubleshooting: [docs/INSTALL.md](docs/INSTALL.md). Before
leaving ADB on, read [SECURITY.md](SECURITY.md).

## What the app does

- Binds to `com.syu.ms/app.ToolkitService` and uses FYT main-module command 161
  to set the ADB port properties, then restarts `adbd`.
- **Persistent** (survives reboots), **this boot only**, and **disable**.
- After every request it reads the properties back and probes
  `127.0.0.1:5555`, then says either that it worked or exactly what did not
  change.
- Shows the head unit's IPv4 address, the current and persistent ADB ports,
  whether `adbd` is listening, and whether USB debugging (`adb_enabled`) is on.
- **USB debugging at boot**: a fallback for units where the Developer options
  toggle will not stay on. Without USB debugging, Android never shows the RSA
  prompt and `adb` stays `unauthorized`.
- Copies the `adb connect` command to the clipboard.

No background service, no network requests (the only socket is the loopback
probe), no analytics. The APK contains no firmware, vendor code, keys, or
addresses.

## Changes from upstream 1.0.0

- **Disable fix.** FYT command 161 silently ignores empty values, so 1.0.0's
  disable left `persist.adb.tcp.port=5555` in place and ADB came back after
  the next reboot. This fork clears it with `-1`.
- **Verified results** instead of "request sent" (adds the `INTERNET`
  permission, used only for the loopback probe).
- **USB debugging status** and the **USB debugging at boot** fallback
  (`persist.sys.usb.config=adb`, or back to `none`).
- Builds with current Android Studio: AGP 9.4.1, Gradle 9.8.0, compileSdk 37.
- Own package name `com.atoqe.atoto.wifiadb` and a release signing key (from
  1.2.0), so it installs alongside upstream instead of clashing with it.
- App label `S8 Wi-Fi ADB (1104MS)`, version `1.2.0-s8g1104ms`.

Release 1.1.0 of this fork still used upstream's package name and a debug key.
1.2.0 and later install as a separate app and update each other in place.

## Build from source

Requirements: JDK 17 or newer (Android Studio's bundled JDK works) and the
Android SDK with platform 37.

```sh
git clone https://github.com/atoqe/atoto-s8g1104ms-wifi-adb.git
cd atoto-s8g1104ms-wifi-adb
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK is written to
`app/build/outputs/apk/debug/atoto-s8g1104ms-wifi-adb-debug.apk`. On macOS
without a system JDK:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

Release signing reads `ATOTO_SIGNING_STORE`, `ATOTO_SIGNING_STORE_PASSWORD`,
`ATOTO_SIGNING_KEY_ALIAS` and `ATOTO_SIGNING_KEY_PASSWORD` from the
environment; no keystore is committed. With those set, `./gradlew
assembleRelease` writes a signed
`app/build/outputs/apk/release/atoto-s8g1104ms-wifi-adb-release.apk`.

Releases are built by CI: pushing a `v*` tag runs the `release` job in
[.github/workflows/android.yml](.github/workflows/android.yml), which signs the
APK with the repository's `RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`
secrets and attaches it to that tag's GitHub release (creating a draft
release if none exists). Builds from forks or without the secrets fail
rather than ship an unsigned APK.

## Compatibility reports

Open an issue with the model, Android version, build display, and incremental
shown at the top of the app, plus the status line after you pressed a button.
Do not post serial numbers, Wi-Fi names, or passwords.

## Documentation

- [Install and troubleshooting guide](docs/INSTALL.md)
- [Technical notes on the FYT interface](docs/TECHNICAL_NOTES.md)
- [Security notes](SECURITY.md)
- [Android Debug Bridge documentation](https://developer.android.com/tools/adb)

## Provenance

The original technique and app are lrehmann's; their README describes that
work as hands-on trial and error on an S8, developed with help from OpenAI
GPT-5.5 and GPT-5.6. This fork's S8G1104MS changes were made with Claude
(Anthropic) and checked against the S8G1104MS firmware's `com.syu.ms`. The FYT
binder interface is observed behaviour, not a documented API: review the
source and keep a recovery path.

## License and affiliation

MIT, the same license as the original project; see [LICENSE](LICENSE). Not
affiliated with or endorsed by ATOTO, FYT, Google, OpenAI, or Anthropic.
Product and company names belong to their respective owners.
