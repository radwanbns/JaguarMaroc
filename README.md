# ON TUNNEL (WireGuard + SSH + V2Ray/Xray)

## Build APK with GitHub Actions
1. Upload the whole project folder to your GitHub repository.
2. Open Actions → **Build APK with V2Ray** → Run workflow.
3. When the workflow succeeds, download the **ON-Tunnel-apk** artifact and extract `app-debug.apk`.

The workflow builds the Android Xray core library (`libv2ray.aar`) before building the app. The first build may take several minutes.

## Added channel side panel
- A floating button on the right edge shows/hides the channel panel on both main and log screens.
- Telegram: https://t.me/radouane_tech
- YouTube: https://youtube.com/@radouane_tech
- The panel description and links are stored as AES-GCM ciphertext in `PromoSecrets.kt`.
- Release builds have R8 minification/obfuscation enabled. The current GitHub Actions workflow builds a debug APK, which is not R8-obfuscated. To get the obfuscated version, run `gradle assembleRelease` in an Android/Gradle environment and sign the release APK before distributing it.

**Security note:** no client-side APK can keep a URL truly secret if the decryption key is bundled inside the app. AES-GCM obfuscates strings at rest, but a determined person can recover the key or observe links when opened. Do not store passwords, private keys, or other secrets this way.

## Protocols
- **WireGuard:** paste a WireGuard `.conf` configuration; this uses the existing WireGuard VPN backend.
- **SSH:** enter server host, port, username, password and optional payload; existing SSH/SOCKS behaviour is retained.
- **V2Ray/Xray:** add a `vless://`, `vmess://` or `trojan://` link. It starts a local SOCKS5 proxy at `127.0.0.1:10808`; see the existing app implementation for routing limitations.

## Notes
- Only use server profiles you are authorized to use.
- The V2Ray core is built from the upstream AndroidLibXrayLite source at build time.
