# 照片整理 · Photo Organizer

[![CI](https://github.com/lswlc33/photo/actions/workflows/ci.yml/badge.svg)](https://github.com/lswlc33/photo/actions/workflows/ci.yml)
[![Pages](https://github.com/lswlc33/photo/actions/workflows/pages.yml/badge.svg)](https://github.com/lswlc33/photo/actions/workflows/pages.yml)

本地相册整理工具。扫描设备上的照片与视频，帮助逐张做出取舍，检出重复项、相似画面、截屏与大文件，并在本机完成压缩重编码。

全部处理都在设备上进行：应用**没有申请网络权限**，任何照片、缩略图或统计数据都不会离开设备。

介绍页：<https://lswlc33.github.io/photo/> · 直接下载：[nightly 预发布](https://github.com/lswlc33/photo/releases/tag/nightly)

## 界面

| 首页 | 整理 | 工具 | 关于 |
|---|---|---|---|
| ![首页](assets/screens/home.png) | ![整理](assets/screens/organize.png) | ![工具](assets/screens/tools.png) | ![关于](assets/screens/about.png) |

## 功能

**首页** 媒体总量、照片/视频/截屏的数量与体积、可回收空间估算，以及索引状态与部分授权提示。

**整理** 三种模式：

- **智能模式** —— 按"收益"排队：先问重复副本，再问截屏，再问大文件，最后是其余项目。
- **定向模式** —— 按相册、日期区间、类型（照片/视频/动态照片/截屏）与最小体积筛选后再评审。
- **手动模式** —— 分组网格，支持批量选择、统一标记与右侧快速滚动条。按日期排序时按天分组；按大小排序时先按月份分组，每月内部大文件在前——这样"最占空间的那些照片"才落在同一屏里，而不是被打散成一条全库长队。

评审结果分为「已保留」与「已弃置」两个集合，可随时回看。删除一律通过系统确认对话框执行，应用自身从不直接删除文件。另可把任意项目归入自建的「逻辑相册」，它只记录归属关系，不移动文件。

**工具**

- **精确重复** —— 先按字节大小分桶，只对同尺寸候选做 SHA-256，因此普通图库几乎不需要读盘。
- **相似照片** —— 感知哈希（dHash）检出连拍、重新编码或缩放后的同一画面。该项按需启动，因为需要解码每一张图片；过程可随时取消，结果会缓存到下次启动。
- **截屏** / **大文件** —— 阈值可在 5/10/20/50/100 MB 间选择。
- **媒体处理** —— 图片可转 JPEG/WebP/PNG、限制长边、调整质量、保留或清除 Exif；视频可转 1080p/720p/480p、限制码率、仅保留视频或仅提取音频。**源文件永不修改**，产物写入 `Pictures|Movies|Music/Photo Organizer`，并且在体积反而变大时自动放弃。

**设置** 主题（跟随系统/浅色/深色）、滑动动画、删除前确认、默认排序、图片与视频的默认质量、元数据处理，以及索引范围（全部相册／排除指定相册／仅指定相册）。「关于」页除版本信息外，还列出应用做什么、不做什么，以及用到的每一个第三方开源项目及其许可证。

## 下载与安装

当前版本 **v8.0**（`versionCode 8`）。

发布页上的 APK 是 **nightly 构建**：代码压缩、资源压缩和「不可调试」都和 release 版一致（约 5 MB），唯一的区别是用 Android SDK 在本机生成的 debug 密钥签名 —— 仓库里不放 release 密钥。所以它可以直接安装，安装时系统会问一次是否允许安装未知来源的应用。

master 每次推送都会自动构建，并把新的 APK 刷新到同一个 [nightly 预发布](https://github.com/lswlc33/photo/releases/tag/nightly)。连续两次构建用的是同一把 debug 密钥，所以新版可以直接覆盖安装，不必先卸载。

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
./gradlew :app:assembleNightly   # 构建发布页上那个 APK
```

Windows 的 PowerShell / cmd 下用 `.\gradlew.bat`。

版本号在 `gradle.properties` 的 `photoVersionCode` / `photoVersionName` 里声明。

三个构建变体：`debug` 是本地开发用的，体积大且可调试；`release` 走完整的代码压缩，但仓库里没有签名配置，产出的是未签名 APK，装不上；`nightly` 与 `release` 的配置相同，只是用本机的 debug 密钥签名，因此既小又能安装，发布页上的就是它。

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

## 提交约定

**提交信息一律用中文撰写**，标题和正文都是。标题是一句动词开头、点明范围的中文短句，例如「给媒体扫描加上权限状态」「修复仪表盘的空状态」；标识符、路径、命令和库名保留原文，例如「把 `ReviewDecisionStore` 的重放改成最后一行生效」。正文写清改了什么、为什么，以及跑过哪些验证命令。

每个完成的改动单独成一个提交，并且每个提交都要能独立编译。这两条都是机器检查的，克隆后启用一次仓库自带的钩子即可：

```bash
git config core.hooksPath .githooks
```

- `commit-msg` —— 标题里没有中文就拒掉提交，没有跳过开关。
- `pre-commit` —— 跑 `:app:test`，它同时编译主源码与单元测试。
- `tools/verify.sh` —— 完整门禁：test + lint + assembleNightly，和 CI 跑的是同样三步。

远端 `ci.yml` 会把本次推送范围内的每条提交信息重新检查一遍，因为没启用过本地钩子的克隆没有本地门禁；两边跑的是同一个脚本，也可以自己先检查：

```bash
tools/check-commit-language.sh --file .git/COMMIT_EDITMSG    # 刚写好的那一条
tools/check-commit-language.sh --range origin/master..HEAD   # 还没推的那些
```

## 自动构建

| workflow | 触发 | 做什么 |
|---|---|---|
| `ci.yml` | 每次推送、PR | 检查提交信息语言，跑 test + lint + assembleNightly；master 上再把同一个 APK 刷新到 `nightly` 预发布 |
| `pages.yml` | `index.html` / `assets/` 变化 | 部署介绍页 |

两个 workflow 都只用运行时自带的 `GITHUB_TOKEN`，不需要额外配置密钥。

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

动手改代码前请阅读 `AGENTS.md`（代码风格、MIUIX 组件规范、提交约定）与 `CLAUDE.md`（架构说明：状态归属、两级导航、索引与指纹管线、需要注意的不变量）。
