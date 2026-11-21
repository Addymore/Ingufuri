# Ingufuri

**Ingufuri** is a root-powered biometric app locker for **Android 17 / One UI 9** (built for Samsung Galaxy A57 and compatible rooted Android 13+ devices), forked from [hxreborn/biometric-app-lock](https://github.com/hxreborn/biometric-app-lock) (`v1.12.0` / `1036-1.12.0`).

It preserves all features and functionality of Biometric App Lock while adding a **Root-First Lock Engine** (no Xposed required—works directly with **KernelSU**, **Magisk**, or **APatch**) and a **modern One UI 9 card-based Material 3 Expressive UI**.

## Highlights

- **Root-Powered App Lock Engine**: Event-driven foreground monitor via root (`logcat -b events` + root `am start` to bypass Android 17 Background Activity Launch restrictions), with automatic boot start and zero-reboot instant config updates.
- **Modern One UI 9 Card UI**: Gradient Hero Protection Card with live status & 1-tap root protection toggle, 2×2 Dashboard Bento Stat & Quick-Toggle Cards (Locked Apps, Re-lock Policy, Self-Lock, Block Screenshots), and generous 24dp One UI 9 rounded card surfaces with tonal borders.
- **Full Feature Parity with Biometric App Lock v1.12.0**:
  - Per-app locking across user profiles (including Dual Messenger / Secure Folder)
  - Global & per-app re-lock delays (Immediate, 30s–30m, Custom, Never)
  - Re-lock on screen off and on task removed from recents
  - Per-app activity filtering (allowlist or lock-only-listed) with recent activity tracking
  - Global & per-app biometric policy (Strongest only, Any enrolled, Screen lock fallback, Confirmation required, Compatibility opaque prompt)
  - App management protection (biometric prompt before app installs & uninstalls)
  - Prevent uninstall & Hide launcher icon
  - Self-lock for Ingufuri's own settings (`FLAG_SECURE`)
  - Block screenshots & Hide notification content
  - Root diagnostics log export & GitHub update checker
  - Optional Xposed (`libxposed` API 101+) compatibility retained

## Requirements

- Android 13–17 (optimized for **One UI 9 / Android 17 / SDK 37**)
- Root access via **KernelSU**, **Magisk**, or **APatch** (grant superuser permission to **Ingufuri**)
- Enrolled biometric (Fingerprint / Face) or device screen lock

## Building

```bash
./gradlew assembleDebug
```

## License & Attribution

Forked from [hxreborn/biometric-app-lock](https://github.com/hxreborn/biometric-app-lock) (`v1.12.0`).  
Licensed under **GPL-3.0-only** — see [LICENSE](LICENSE).
