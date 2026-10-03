# Technical notes

## Why this is ATOTO/FYT-specific

Ordinary Android applications cannot change the protected ADB system
properties. The tested S8 firmware exposes an FYT vendor binder service at:

- Package: `com.syu.ms`
- Service: `app.ToolkitService`
- Toolkit descriptor: `com.syu.ipc.IRemoteToolkit`
- Main-module descriptor: `com.syu.ipc.IRemoteModule`

The app binds explicitly to that service, obtains FYT main module `0`, and uses
the observed main-module command `161` to request these Android properties:

| Action | Requested properties |
| --- | --- |
| Temporary | `service.adb.tcp.port=5555`, then `ctl.restart=adbd` |
| Persistent | `persist.adb.tcp.port=5555`, `service.adb.tcp.port=5555`, then restart |
| Disable | `persist.adb.tcp.port=-1`, `service.adb.tcp.port=-1`, then restart |
| USB debugging at boot | `persist.sys.usb.config=adb` (or `none`); Android 10 sets `adb_enabled` from it at the next boot |

On the S8G1104MS, `com.syu.ms` 25.1119 (APP20251124 / System20251117) was
checked statically: toolkit transaction 1 returns main module 0, and command
161 calls `SystemProperties.set(name, value)` only when both strings are
non-empty. That is why a property cannot be cleared with `""` and the disable
action writes `-1` (adbd treats any port <= 0 as off).

Binder transactions are vendor implementation details, not a stable Android or
ATOTO SDK. A firmware update can change or remove them. The one-way property
transaction confirms only that the binder accepted the request; the status
panel independently reads the resulting properties afterward.

## Deliberately not included

- No root exploit or ATOTO root shell.
- No Magisk integration or boot-image handling.
- No firmware images, vendor APKs, decompiled sources, platform keys, private
  signing keys, local IP addresses, or credentials.
- No background service. After a request the app reads the properties back
  and probes `127.0.0.1:5555` once; it does nothing else in the background.

## Development provenance and limits

This technique was developed through hands-on trial and error against one S8,
inspection of its vendor behavior, repeated parked testing, and substantial
assistance from OpenAI GPT-5.5 and GPT-5.6. The public app is a focused rewrite
of that working bootstrap, not a claim of an official FYT API.

AI-assisted code and reverse-engineered vendor behavior can be wrong. Review
the source, keep a recovery path for the head unit, and report exact firmware
details when a result differs. Compatibility beyond the listed test unit is
unknown until someone verifies it.
