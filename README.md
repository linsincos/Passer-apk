# Aira Mobile

Aira Mobile 是从 Passer 独立出来的 Android 原生 Agent。它不依赖 Windows、
ADB、scrcpy 或 Passer 进程，模型请求由手机直接发送到用户选择的服务商。

## 当前能力

- DeepSeek、OpenAI、Anthropic 三种模型服务商
- Android Keystore 加密保存 API Key
- 本机对话历史与长期记忆
- 最多 8 轮的“模型规划 → 手机工具 → 结果回填”Agent 循环
- 读取当前时间、设备型号、Android 版本、语言和电量
- 经用户逐次确认后：
  - 打开网页
  - 打开系统分享面板
  - 打开邮件编辑页（不自动发送）
  - 打开闹钟确认页
  - 打开日程编辑页（不自动保存）
  - 打开地图搜索
- 无任意读屏、后台点击、支付、删除、安装、短信/联系人/相册访问

## 为什么没有“自动控制其他 App”

Google Play 对 AccessibilityService 有严格限制：普通助手不得借助无障碍服务
自主规划并执行跨 App 操作。当前版本只使用 Android 官方 Intent，并在每次跨 App
动作前显示确认，因此更适合作为可发布版本。若仅供自己侧载，可另做实验性控制插件，
但应与公开发行版分开。

## 构建

1. 安装最新版稳定版 Android Studio。
2. 用 Android Studio 打开本目录。
3. 安装 Android SDK 36。
4. 等待 Gradle 同步后，选择 `Build > Build APK(s)`。
5. 调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

工程要求 JDK 17+、Android Gradle Plugin 8.12.0，最低支持 Android 8.0
（API 26）。

## 首次使用

打开 App 后阅读隐私说明，在设置中选择服务商、模型并填写自己的 API Key。
Key 留空保存时不会覆盖已有 Key；“清除 Key”会删除本机密文。

默认模型：

- DeepSeek：`deepseek-chat`
- OpenAI：`gpt-4.1-mini`
- Anthropic：`claude-sonnet-4-6`

## 隐私与安全

- App 不申请短信、联系人、相册、麦克风、定位、通知读取或无障碍权限。
- API Key 使用 Android Keystore 的 AES/GCM 密钥加密。
- 对话保存在 App 私有目录；Android 备份已关闭。
- 网络安全配置禁止明文 HTTP。
- 外部网页和工具结果在 Agent 提示中被标记为不可信数据。
- App 不提供自动发送邮件、自动保存日程、支付、删除或任意脚本执行工具。
