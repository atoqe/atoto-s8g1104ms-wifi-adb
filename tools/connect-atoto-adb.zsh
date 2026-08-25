#!/bin/zsh
set -euo pipefail

# Optional macOS helper for a computer with overlapping 192.168.x.x routes.
# It exposes a loopback-only ADB relay and binds the outbound socket to the
# selected physical interface. Normal setups should use `adb connect IP:5555`.

HEAD_UNIT_IP="${ATOTO_IP:-}"
HEAD_UNIT_PORT="${ATOTO_ADB_PORT:-5555}"
LOCAL_PORT="${ATOTO_LOCAL_PORT:-15555}"
SOURCE_IF="${ATOTO_SOURCE_IF:-en0}"
SOURCE_IP="${ATOTO_SOURCE_IP:-$(ipconfig getifaddr "$SOURCE_IF" 2>/dev/null || true)}"
NCAT="${NCAT:-$(command -v ncat || true)}"
ADB="${ADB:-$(command -v adb || true)}"

if [[ -z "$HEAD_UNIT_IP" ]]; then
  print -u2 "Set ATOTO_IP to the head unit IPv4 address."
  exit 2
fi
if [[ ! "$HEAD_UNIT_IP" =~ '^[0-9]{1,3}(\.[0-9]{1,3}){3}$' ]]; then
  print -u2 "ATOTO_IP must be an IPv4 address."
  exit 2
fi
if [[ ! "$HEAD_UNIT_PORT" =~ '^[0-9]{1,5}$' || ! "$LOCAL_PORT" =~ '^[0-9]{1,5}$' ]]; then
  print -u2 "ADB ports must be numeric."
  exit 2
fi
if [[ ! "$SOURCE_IF" =~ '^[A-Za-z0-9._:-]+$' || -z "$SOURCE_IP" ]]; then
  print -u2 "No usable IPv4 address was found for $SOURCE_IF."
  exit 2
fi
if [[ -z "$NCAT" || -z "$ADB" ]]; then
  print -u2 "ncat (from nmap) and adb must be installed and on PATH."
  exit 2
fi

LOG_FILE="${TMPDIR:-/tmp}/atoto-adb-relay-${LOCAL_PORT}.log"
PID_FILE="${TMPDIR:-/tmp}/atoto-adb-relay-${LOCAL_PORT}.pid"

if ! lsof -nP -iTCP:"$LOCAL_PORT" -sTCP:LISTEN -t >/dev/null 2>&1; then
  nohup "$NCAT" -l 127.0.0.1 "$LOCAL_PORT" -k --sh-exec \
    "/usr/bin/nc -b $SOURCE_IF -s $SOURCE_IP $HEAD_UNIT_IP $HEAD_UNIT_PORT" \
    </dev/null >"$LOG_FILE" 2>&1 &!
  print $! >"$PID_FILE"
  sleep 1
fi

"$ADB" disconnect "127.0.0.1:$LOCAL_PORT" >/dev/null 2>&1 || true
"$ADB" connect "127.0.0.1:$LOCAL_PORT"
"$ADB" -s "127.0.0.1:$LOCAL_PORT" get-state

print "ADB target: 127.0.0.1:$LOCAL_PORT"
print "Relay: $SOURCE_IF/$SOURCE_IP -> $HEAD_UNIT_IP:$HEAD_UNIT_PORT"
