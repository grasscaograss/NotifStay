# NotifStay（通知常驻）

让锁屏通知在解锁后依然保留在锁屏界面（类比 iOS 的通知逻辑），不因解锁而消失。

## 功能
- 解锁后再锁屏时，通知按时间顺序重新显示在锁屏
- 通过「链式 snooze + DND 亮屏压制」实现：不自动亮屏、不乱序、无悬浮层/无障碍覆盖
- 新消息照常亮屏提醒

## 构建
需要 JDK 21 + Android SDK 37。

```bash
./gradlew assembleRelease
```

## 发布
推送 `v` 开头的 tag（例如 `v1.4.1`）会自动触发 GitHub Actions 构建并创建 Release。

如需产出**已签名** APK，在仓库 Settings → Secrets and variables → Actions 中配置：

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_BASE64` | `release.jks` 的 base64 内容 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | key 别名 |
| `KEY_PASSWORD` | key 密码 |

未配置 Secrets 时也能构建（产出未签名 APK），方便先跑通流程。
