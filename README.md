# Aira 

Aira 是由 Passer 而生的 Android 原生 Agent。

## 当前能力

- 轻量聊天界面：去除说明小字，保留居中标题、欢迎快捷卡、无边框助手回复和悬浮输入框
- 自适应状态栏、刘海/挖孔、底部手势区、键盘和窄屏手机布局
- 朴素的 Aira“路径 + 星光 + 完成勾”Android 自适应启动图标
- DeepSeek、OpenAI、Anthropic 三种模型服务商
- 右上角直接进入设置；输入框左下角 `＋` 添加最多 3 个图片或文本附件
- 左上角打开历史对话侧边栏，可新建、载入、重命名或删除对话
- OpenAI/Anthropic 支持图片附件；三种服务商均支持读取文本附件
- Android Keystore 加密保存 API Key
- 多会话历史与长期记忆仅保存在本机；升级时自动迁移原有单会话记录
- 最多 12 轮的“规划 → 手机工具 → 结果验证 → 继续执行”单任务 Agent 闭环
- 读取当前时间、设备型号、Android 版本、语言和电量
- 经用户逐次确认后：
  - 打开网页
  - 打开系统分享面板
  - 打开邮件编辑页（不自动发送）
  - 打开闹钟确认页
  - 打开日程编辑页（不自动保存）
  - 打开地图搜索
- 无任意读屏、后台点击、支付、删除、安装、短信/联系人/相册访问

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
