# NotifStay 通知常驻

[![Release](https://img.shields.io/github/v/release/grasscaograss/NotifStay)](https://github.com/grasscaograss/NotifStay/releases)
[![Build](https://github.com/grasscaograss/NotifStay/actions/workflows/release.yml/badge.svg)](https://github.com/grasscaograss/NotifStay/actions)
[![License](https://img.shields.io/github/license/grasscaograss/NotifStay)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-brightgreen)](https://developer.android.com/about/versions/oreo)

> Keep lock-screen notifications alive across unlock — just like iOS.
> 让锁屏通知在解锁后依然保留，不再因为解锁而消失。

---

## Introduction / 简介

On HyperOS / MIUI, notifications are cleared from the lock screen once the phone is unlocked, even though they still exist in the notification shade. **NotifStay** re-delivers those notifications back to the lock screen when the device is locked again, so important messages stay visible.

在 HyperOS / MIUI 上，手机解锁后锁屏通知会被清掉（通知栏里其实还在）。**NotifStay** 会在下次锁屏时把这些通知重新投递回锁屏，让重要消息保持可见、不丢失。

## Features / 功能

- 🔒 Keep lock-screen notifications after unlock — 解锁后锁屏通知常驻
- ⏱ Correct chronological order, newest on top — 按时间倒序排列，最新通知在最上方
- 🌙 No screen wake-ups: re-post wakeups are suppressed via DND — 不自动亮屏（用勿扰压制重投递亮屏）
- 🔔 New notifications still wake the screen as usual — 新消息照常亮屏提醒
- 🧹 Skips system group summaries — 自动跳过分组摘要，保持列表干净
- 🚫 No overlay window / accessibility abuse — 无悬浮层、无无障碍滥用

## How It Works / 工作原理

1. Listen to notifications with `NotificationListenerService`.
2. When the screen turns **off**, briefly enable DND (all categories allowed; only visual / light / full-screen-intent interruptions suppressed) so the re-post never wakes the screen.
3. On lock, **chain-snooze** each active notification with 5 ms offsets (`SNOOZE_MS=400ms`). Chain delivery is the key: batch re-posts get merged by the system and lose order, while staggered re-posts appear in `rankingTime` order — newest on top.
4. Re-posting only works while the keyguard is showing, so the whole flow is triggered on lock.

## Requirements / 环境要求

- Android 8.0+ (API 26+), tested on **Android 17 / HyperOS 4**
- JDK 21, Android SDK 37, Gradle 9.7.1 (wrapper included)

## Build / 构建

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (signed when `keystore.properties` exists, otherwise `app-release-unsigned.apk`).

## Install & Usage / 安装与使用

1. Install the APK and open the app — 安装 APK 并打开应用
2. Grant the four permissions inside the app — 在应用内授权：通知使用权、勿扰访问、忽略电池优化
3. Allow **autostart** in MIUI / HyperOS settings (Settings → Apps → NotifStay → Autostart) — 允许自启动
4. Unlock, then lock again — your notifications stay on the lock screen — 解锁后再次锁屏，通知常驻

## Release / 发布

Pushing a `v*` tag (e.g. `v1.4.1`) triggers GitHub Actions to build the APK and create a GitHub Release automatically.

推送 `v*` 开头的 tag（如 `v1.4.1`）会自动触发 GitHub Actions 构建并创建 Release。

To produce **signed** APKs, add these secrets in **Settings → Secrets and variables → Actions**:

| Secret | Description |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` output |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Without secrets the pipeline still works and publishes an unsigned APK. 未配置 Secrets 时也能构建（产出未签名 APK）。

## Project Structure / 目录结构

```
app/src/main/java/com/jojo/notifpersist/
├── MainActivity.java              # Settings UI / 设置界面
├── NotifListenerService.java      # Core: listener + chain snooze / 核心逻辑
├── PersistForegroundService.java  # Keep-alive service / 保活服务
├── BootReceiver.java              # Auto-start on boot / 开机自启
└── Config.java                    # SharedPreferences config
```

## Privacy / 隐私说明

- All processing is **on-device**; no network, no analytics, no data collection — 所有处理都在本地完成，无网络、无统计、无数据收集
- Notification access is required to read and re-post notifications; data never leaves your device — 需要通知使用权来读取和重投递通知，数据不会离开设备

## License / 许可证

[MIT](LICENSE) © 2026 grasscaograss
