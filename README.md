# 照片整理 · Photo Organizer

本地相册整理工具。扫描设备上的照片与视频，帮助逐张做出取舍，检出重复项、相似画面、截屏与大文件，并在本机完成压缩重编码。

全部处理都在设备上进行：应用**没有申请网络权限**，任何照片、缩略图或统计数据都不会离开设备。

## 功能

**首页** 媒体总量、照片/视频/截屏的数量与体积、可回收空间估算，以及索引状态与部分授权提示。

**整理** 三种模式：

- **智能模式** —— 按"收益"排队：先问重复副本，再问截屏，再问大文件，最后是其余项目。
- **定向模式** —— 按相册、日期区间、类型（照片/视频/动态照片/截屏）与最小体积筛选后再评审。
- **手动模式** —— 日期分组网格，支持批量选择、统一标记与右侧快速滚动条。

评审结果分为「已保留」与「已弃置」两个集合，可随时回看。删除一律通过系统确认对话框执行，应用自身从不直接删除文件。另可把任意项目归入自建的「逻辑相册」，它只记录归属关系，不移动文件。

**工具**

- **精确重复** —— 先按字节大小分桶，只对同尺寸候选做 SHA-256，因此普通图库几乎不需要读盘。
- **相似照片** —— 感知哈希（dHash）检出连拍、重新编码或缩放后的同一画面。该项按需启动，因为需要解码每一张图片；过程可随时取消，结果会缓存到下次启动。
- **截屏** / **大文件** —— 阈值可在 5/10/20/50/100 MB 间选择。
- **媒体处理** —— 图片可转 JPEG/WebP/PNG、限制长边、调整质量、保留或清除 Exif；视频可转 1080p/720p/480p、限制码率、仅保留视频或仅提取音频。**源文件永不修改**，产物写入 `Pictures|Movies|Music/Photo Organizer`，并且在体积反而变大时自动放弃。

**设置** 主题（跟随系统/浅色/深色）、滑动动画、删除前确认、默认排序、图片与视频的默认质量、元数据处理，以及索引范围（全部相册／排除指定相册／仅指定相册）。

## 环境要求

| 项目 | 版本 |
|---|---|
| Android | 13 及以上（minSdk 33） |
| JDK | 17 |
| 编译目标 | compileSdk / targetSdk 37 |
| 界面 | Compose + [MIUIX](https://compose-miuix-ui.github.io/miuix/) + [Kyant Backdrop](https://kyant.gitbook.io/backdrop) |
| 视频转码 | Media3 Transformer（设备硬件编解码器，无原生依赖，全 ABI 可用） |

## 构建与运行

使用 Gradle wrapper，不要用本机安装的 Gradle：

```bash
./gradlew :app:assembleDebug     # 构建 debug APK
./gradlew :app:installDebug      # 安装到已连接的设备
./gradlew :app:assembleRelease   # 构建 release APK（签名见下）
```

Windows 的 PowerShell / cmd 下用 `.\gradlew.bat`。

release 签名凭据从环境变量 `PHOTO_RELEASE_STORE_FILE` / `_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD` 读取，或从 `~/.android/photo-organizer-release.properties` 读取。凭据缺失时 `release` 签名配置不会创建，构建产出未签名 APK 而不会失败。

## 测试

```bash
./gradlew :app:test    # JVM 单元测试
./gradlew :app:lint    # Android lint
```

只跑某个类或某个方法：

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.photoorganizer.media.SmartQueueTest'
./gradlew :app:testDebugUnitTest --tests '*SmartQueueTest.sortsEachBucketByDescendingSize'
```

仪器测试会真实调用编解码器并写入 MediaStore，需要设备或模拟器：

```bash
./gradlew :app:connectedDebugAndroidTest
```

分析类逻辑刻意写成不依赖 Android 的纯 Kotlin，Android 部分作为 lambda 注入，因此绝大部分核心逻辑可以在 JVM 上测试。新增算法请沿用这个模式。

## 代码结构

单模块 `:app`，包名 `com.example.photoorganizer`：

| 目录 | 职责 |
|---|---|
| `media/` | 领域层：MediaStore 索引、权限状态、筛选与范围、重复/相似分析、指纹缓存。尽量不引入 Compose 与 Android 依赖 |
| `processing/` | 图片与视频重编码，以及 MediaStore 产物写入 |
| `ui/` | 主题、系统内边距、玻璃底栏，以及页面骨架与通用组件 |
| `screens/` | 每个功能区一个包：`dashboard`、`organize`、`review`、`tools`、`settings` |
| `PhotoOrganizerApp.kt` | 应用状态根：偏好、主题、权限、评审结果、页面与详情导航 |

界面文案中文优先。新增任何用户可见文本都必须同时写入 `values/strings.xml` 与 `values-zh-rCN/strings.xml`。

## 参与开发

动手前请阅读：

- **`AGENTS.md`** —— 代码风格、MIUIX 组件使用规范、提交约定、依赖库文档索引。
- **`CLAUDE.md`** —— 架构说明：状态归属、两级导航、索引与指纹管线、需要注意的不变量。

