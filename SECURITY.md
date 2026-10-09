# Security notes

This app deliberately exposes Android Debug Bridge on TCP port 5555. ADB is a
powerful administrative interface.

- Use the feature only on a trusted, private Wi-Fi network. The app shows a
  trusted Wi-Fi warning each time you turn it on.
- Confirm that Android displays an RSA authorization prompt and approve only a
  computer you control.
- Never expose port 5555 through a router, public IP, VPN exit, or port forward.
- Prefer **On until reboot** when persistence is not required.
- Choose **Off** in the app when finished.
- Treat a head unit that accepts ADB without RSA approval as unsafe and disable
  the feature immediately.

Please report security issues privately through GitHub's security-advisory
feature rather than opening a public issue.
