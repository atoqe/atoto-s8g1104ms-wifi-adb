# ATOTO S8 ADB via Wi-Fi

[![Android build](https://github.com/lrehmann/atoto-adb-via-wifi/actions/workflows/android.yml/badge.svg)](https://github.com/lrehmann/atoto-adb-via-wifi/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A small bootstrap app that enables legacy, RSA-protected Android Debug Bridge
over Wi-Fi on a compatible ATOTO S8/FYT head unit. It uses the vendor FYT
service already present in the firmware; it does **not** need root, Magisk, a
patched boot image, or a working USB ADB connection.

> [!WARNING]
> This is experimental, vendor-specific software. The underlying method was
> tested on one ATOTO S8G2A74MS running Android 10, build
> `QP1A.190711.020`, incremental `33515`.
> It is not an official ATOTO tool and may not work on another S8 or firmware.

## S8G1104MS variant (this copy)

This copy is adapted for an ATOTO **S8G1104MS**. The FYT bridge was checked
statically against `com.syu.ms` 25.1119 from the S8G1104MS firmware
(APP20251124 / System20251117): toolkit transaction 1 returns module 0
(`s0.i`), and its command 161 calls `SystemProperties.set(name, value)`, but
only when both strings are non-empty.

Changes from 1.0.0:

- **Disable fix**: the persistent port is cleared with `-1`, not `""`. The
  firmware silently ignores empty values, so 1.0.0's disable left
  `persist.adb.tcp.port=5555` in place.
- **Verified results**: after each request the app reads the properties back
  and probes `127.0.0.1:5555`, then reports success or exactly what did not
  apply (adds the `INTERNET` permission for that loopback probe only).
- **USB debugging status**: shows `adb_enabled`. When it is off, Android never
  shows the RSA prompt, so `adb` stays `unauthorized`.
- **USB debugging at boot** fallback: sets `persist.sys.usb.config=adb` (or
  back to `none`); Android 10 turns `adb_enabled` on from it at the next boot.
- Build: AGP 9.4.1, Gradle 9.8.0, compileSdk 37, so it builds with the JDK
  bundled in current Android Studio.

## Why Wi-Fi ADB?

On our S8, neither accessible USB lead enumerated as an ADB device on the
computer. Changing Android's default USB mode between File Transfer, USB
tethering, MIDI, PTP, and No data transfer did not solve it. A manually
installed helper APK could, however, ask the FYT system service to start `adbd`
on TCP port 5555. That became the reliable bootstrap path.

## Quick start

1. Download `atoto-adb-via-wifi.apk` from the
   [latest release](https://github.com/lrehmann/atoto-adb-via-wifi/releases/latest).
2. Install it directly on the head unit using its browser, file manager, or a
   local HTTP server. No prior ADB connection is required.
3. Put the computer and head unit on the same trusted Wi-Fi network.
4. Open **ATOTO Wi-Fi ADB** and confirm `FYT ToolkitService: FOUND`.
5. Press **Enable persistent Wi-Fi ADB** (or the current-boot option).
6. On the computer, run:

   ```sh
   adb connect HEAD_UNIT_IP:5555
   adb devices -l
   ```

7. Approve the computer's RSA fingerprint on the head unit.

See the [complete installation and troubleshooting guide](docs/INSTALL.md).

## What the app does

- Binds to `com.syu.ms/app.ToolkitService` on compatible FYT firmware.
- Requests port 5555 temporarily or persistently through FYT main-module
  command 161.
- Restarts `adbd` through the same vendor property bridge.
- Shows the head unit's IPv4 address and current/persistent ADB properties.
- Provides a disable action that clears the persistent port and restarts ADB.

The app has no background service and makes no network requests. The APK ships
without firmware blobs, vendor APKs, decompiled code, credentials, private
keys, or unit-specific network addresses. Read the
[technical notes](docs/TECHNICAL_NOTES.md) for the exact observed interface.

## Security

ADB gives a trusted computer extensive access to the head unit. Use this only
on a private network, never forward TCP port 5555 to the internet, approve only
an RSA key you recognize, and disable persistent ADB when it is no longer
needed. Read [SECURITY.md](SECURITY.md) before enabling it.

## Build from source

Requirements: JDK 17 and an Android SDK containing API 35.

```sh
git clone https://github.com/lrehmann/atoto-adb-via-wifi.git
cd atoto-adb-via-wifi
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK will be written to
`app/build/outputs/apk/debug/app-debug.apk`. Official release APKs are signed
outside the repository; the signing keystore and credentials are not committed.

## Helpful resources

- [Android Debug Bridge documentation](https://developer.android.com/tools/adb)
- [Android SDK Platform Tools releases](https://developer.android.com/tools/releases/platform-tools)
- [Detailed install/troubleshooting guide](docs/INSTALL.md)
- [Technical FYT notes](docs/TECHNICAL_NOTES.md)

## Provenance and disclosure

Much of the original investigation was hands-on trial and error on the target
S8. The working bootstrap was iteratively developed and analyzed with extensive
assistance from OpenAI GPT-5.5 and GPT-5.6. This public repository is a focused,
sanitized rewrite of that work.

That history is important: the FYT binder interface is observed behavior, not a
documented public API. AI-assisted code and reverse-engineered behavior should
be reviewed and independently tested. Please include exact model, Android
version, build display, and incremental number in useful compatibility reports.

## License and affiliation

MIT licensed. Not affiliated with or endorsed by ATOTO, FYT, Google, or OpenAI.
Product and company names belong to their respective owners.
