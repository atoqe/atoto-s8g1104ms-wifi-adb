# Quick start

Ten minutes from a stock ATOTO S8G1104MS to an `adb shell` over Wi-Fi, using
app version **1.3.0**. Park the car; the head unit needs to stay on (ACC on)
the whole time.

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

**Upgrading?**

- From **1.2.x**: just install 1.3.0. It updates the app in place and keeps
  your current Wi-Fi ADB setting.
- From **1.1.0**: 1.2.0 and later are a separate app (new package name and
  signing key), so 1.1.0 stays installed next to it. Uninstall the old
  `S8 Wi-Fi ADB (1104MS)` 1.1.0 (and upstream's `ATOTO Wi-Fi ADB`, if present)
  to avoid two look-alike icons. Uninstalling does not turn Wi-Fi ADB off: the
  setting lives in system properties, not in the app.

## 2. Turn on Wi-Fi ADB

Open **S8 Wi-Fi ADB (1104MS)**. Everything is on one screen: the status on the
left, the controls on the right.

![The app: status and address on the left, mode and troubleshooting on the right](docs/screenshots/main-screen.png)

1. Check **HEAD UNIT ADDRESS** on the left. It shows the unit's IP address. If
   it says **Not connected to Wi-Fi** or **Wi-Fi is off**, tap **Open Wi-Fi
   settings** and join the same network as your computer; the screen updates
   by itself when Wi-Fi connects.
2. Under **MODE** on the right, tap **Always on**. (Pick **On until reboot**
   instead if you want it gone after the next restart.)
3. A **Use only on trusted Wi-Fi** warning appears. Wi-Fi ADB lets devices on
   the same network ask for full control of the head unit, so only continue on
   a network you trust, such as your home Wi-Fi. Tap **Turn on**.
4. After a few seconds the headline turns green, **Ready to connect**, and a
   message appears under the modes:

   | Message | Meaning |
   | --- | --- |
   | `Wi-Fi ADB is on. Run the command ...` | Done. Go to [Connect from the computer](#3-connect-from-the-computer). |
   | `... but USB debugging is off ...` | ADB is on, but the approval prompt will not appear. See [USB debugging is off](#usb-debugging-is-off). |
   | `Wi-Fi ADB is on. Connect the head unit to Wi-Fi ...` | ADB is on, but the unit has no network. Join Wi-Fi first. |
   | `Turned on, but ADB isn't answering yet ...` | Wait a few seconds; the screen updates by itself (or tap **Refresh**). |
   | `That didn't work ...` | This firmware does not accept the request. Stop and [report it](README.md#compatibility-reports). |

The four **CHECKS** at the bottom left should all have green dots:

| Check | Green means |
| --- | --- |
| **Wi-Fi** | `Connected` |
| **Answering on port 5555** | `Yes`: ADB is listening |
| **After a reboot** | `Stays on` (grey `Off after reboot` is expected with **On until reboot**) |
| **Computer approval prompt** | `Ready`. Amber `May not appear` means USB debugging is off; see [below](#usb-debugging-is-off). |

## 3. Connect from the computer

The app shows the exact command under the address; **Copy** puts it on the
head unit's clipboard. Run it on the computer:

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

Use the IP address the app shows. Approve each command it asks to run, and tap
**Allow** on the head unit when the RSA prompt appears: the agent cannot do
that part. Once connected, you can keep asking it to run `adb` commands for
you, such as installing APKs or reading logs. ADB gives whatever runs it full
control of the head unit, so read [SECURITY.md](SECURITY.md) and review what
the agent proposes before approving it.

## USB debugging is off

Android only shows the RSA prompt when USB debugging is on, even for Wi-Fi ADB.
The app shows this as **Computer approval prompt: May not appear**.

1. Settings → About → tap **Build number** seven times to unlock Developer
   options, then turn on **Developer options → USB debugging**.
2. If that toggle will not stay on, tap **USB debugging at boot** under
   **TROUBLESHOOTING** in the app, confirm, and reboot the head unit. It sets
   `persist.sys.usb.config=adb`, which Android 10 reads at boot.

## After a reboot or sleep

**Always on** survives reboots and ACC off/on. The unit's IP address can
change (a DHCP reservation in your router keeps it fixed), and Wi-Fi takes
30–60 s to come back after the screen turns on. Open the app to see the current
address, then run `adb connect HEAD_UNIT_IP:5555` again. On networks that
support mDNS, `adb mdns services` lists the unit's address.

If `adb` stops answering after a firmware update, reopen the app and tap
**Always on** again.

## Turn it off

Tap **Off** under **MODE** and confirm with **Turn off**. The message should
read `Wi-Fi ADB is off, now and after reboot.` If you turned on USB debugging
at boot, tap **USB debugging at boot** and turn it off as well.

More detail and troubleshooting: [docs/INSTALL.md](docs/INSTALL.md). Before
leaving ADB on, read [SECURITY.md](SECURITY.md).
