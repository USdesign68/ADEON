# 开屏净化助手（Splash Cleaner）

Kotlin + Jetpack Compose 原生 Android 工程基线，按项目提供的三份文档建立。目标测试平台为 Android 16（API 36），最低兼容 Android 9（API 28）。

> 当前交付是 **v0.1 开发基座**，不是可发布的完整产品。W0 真机门控尚未执行，因此本版本不自动点击任何第三方应用控件，也不内置未经验证的广告规则。

## 技术栈

- Kotlin 2.0.21、Android Gradle Plugin 8.10.1、Gradle 8.11.1
- Jetpack Compose + Material 3
- Room 2.6.1（五张表：`app_cfg`、`hit`、`report`、`daily_aggregate`、`rule_package`）
- AccessibilityService（只订阅 `TYPE_WINDOW_STATE_CHANGED` 与 `TYPE_WINDOW_CONTENT_CHANGED`）
- Jackson JSON5-compatible reader（schema 3）
- WorkManager 更新任务骨架
- Ed25519 验签组件（公钥注入；尚未配置线上公钥和 CDN）

## Android Studio 导入

1. 安装 Android Studio（建议使用支持 Android 16 / API 36 的稳定版）和 JDK 17。
2. 安装 Android SDK Platform 36、Android SDK Build-Tools 36。
3. 解压本工程，在 Android Studio 中选择 **Open**，打开 `SplashCleaner` 根目录。
4. 等待 Gradle Sync；首次同步需要网络下载 Gradle 与 Maven 依赖。
5. 连接 Android 16 真机，开启开发者选项和 USB 调试，运行 `app` 的 debug 配置。
6. 首次进入应用可先体验首页、逐应用授权和 23 项体检记录界面。

命令行构建（配置好 Android SDK 后）：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Windows：

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
```

## 当前已实现

- Compose 首页、应用管理、广告体检、设置四个主页面。
- 首次无障碍授权入口；服务声明仅订阅两类窗口事件。
- 逐应用授权，默认关闭；服务在读取目标窗口树之前检查用户授权。
- 受限节点树采集：深度 12、最多 400 节点、800ms 预算；标记 `opaque`。
- 屏幕 `text` / `contentDescription` 仅存在于单次回调的内存快照中；数据库模型没有屏幕文本字段。
- 规则 schema 3 JSON5 解析、选择器基础匹配、锚定正则检查与动作类型数据模型。
- 安全闸门基础组件：前台包一致、金融/支付/政务/系统包保护、敏感词拦截。
- Room 五表、23 项体检清单、评分和 A/B/C/D 定级逻辑。
- Ed25519 验签组件接口和 WorkManager 更新任务骨架。

## 尚未实现 / 不可宣称已验收

- W0 PoC：8–10 台设备、10 个目标 App、100 次冷启动；识别率 ≥90%、P95 <300ms、误点 0 次。
- 自动点击动作执行、动作后 300ms 校验、可点击祖先上溯、冷却与频次状态机。
- 目标 App 版本区间比较器、完整规则包 schema 校验、签名公钥正式配置、CDN、ETag、stable/beta、原子更新与回滚。
- 跨应用误触检测、500ms 悬浮确认条和一键返回。
- OCR、截图像素采样、PDF/长图证据报告、自动检测结果采集。
- 匿名遥测、规则失效告警、Shizuku、完整崩溃报告与隐私清除流程。
- OEM 受限制设置的可靠识别、Android 16 前台服务与通知策略真机回归。

这些功能按需求文档 W0–W10 分阶段推进。W0 不通过前不启用自动点击，不将示例规则当作生产规则。

## 隐私与安全边界

- 目标应用默认不处理，用户必须逐个打开授权开关。
- 任何规则都不能覆盖安全闸门。
- 不做 HTTPS 解密、不安装根证书、不注入目标进程、不读取目标应用私有数据。
- 不把屏幕文本或 contentDescription 写入日志、Room 或网络请求。
- 体检项默认是“未验证”；用户手动记录状态不等于自动化检测结果。

## 目录结构

```text
app/src/main/java/com/splashcleaner/
  data/       Room entities, DAO, database
  domain/     Rule models, parser, safety gate, inspection scoring
  service/    AccessibilityService, bounded node collector, FGS shell
  ui/         MainActivity + Compose screens
  worker/     Rule update worker shell
docs/
  source-references/  本次开发依据的三份原始 HTML 文档
```

## 版本

- `versionName`: 0.1.0
- `versionCode`: 1
- `applicationId`: `com.splashcleaner`
- `targetSdk`: 36
- `minSdk`: 28


## 上传到 GitHub

### 网页上传（不使用命令行）

1. 在 GitHub 创建一个新的空仓库，例如 `SplashCleaner`。
2. 解压本压缩包，进入 `SplashCleaner` 项目根目录。
3. 打开 GitHub 仓库，选择 **Add file → Upload files**。
4. 将项目根目录中的文件和文件夹拖入上传区域（包括 `.gitignore`、`README.md`、`app/`、`gradle/`、`docs/` 等）。
5. 在页面底部填写提交说明，例如 `Initial Android project upload`，点击 **Commit changes**。

注意：不要只把 ZIP 压缩包本身上传到仓库；应先解压，再上传其中的项目文件。不要上传 `local.properties`、密钥文件、签名文件或包含个人信息的配置。

### 使用 Git 命令上传

在项目根目录执行（将地址替换为你自己的 GitHub 仓库地址）：

```bash
git init
git add .
git commit -m "Initial Android project upload"
git branch -M main
git remote add origin https://github.com/你的用户名/SplashCleaner.git
git push -u origin main
```
