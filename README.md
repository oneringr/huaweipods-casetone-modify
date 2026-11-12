<a id="readme-top"></a>

<div align="center">
  <a href="https://github.com/oneringr/huaweipods-casetone-modify">
    <img src="images/logo.svg" alt="开盖自定义" width="96" height="96">
  </a>
  <h1>开盖自定义</h1>
  <p>为耳机充电盒换一段开盖声音。</p>
  <p>
    <a href="https://github.com/oneringr/huaweipods-casetone-modify/releases/latest"><strong>下载最新版</strong></a>
    &nbsp; / &nbsp;
    <a href="https://github.com/oneringr/huaweipods-casetone-modify/issues/new">反馈问题</a>
    &nbsp; / &nbsp;
    <a href="https://github.com/oneringr/huaweipods-casetone-modify/issues">功能建议</a>
  </p>
  <p>
    <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white" alt="Android 10 及以上">
    <a href="https://github.com/oneringr/huaweipods-casetone-modify/releases/latest"><img src="https://img.shields.io/github/v/release/oneringr/huaweipods-casetone-modify" alt="最新版本"></a>
  </p>
</div>

<details>
  <summary>目录</summary>
  <ol>
    <li><a href="#about-the-project">项目简介</a></li>
    <li><a href="#getting-started">安装</a></li>
    <li><a href="#usage">使用方法</a></li>
    <li><a href="#building">本地构建</a></li>
    <li><a href="#contributing">反馈与贡献</a></li>
    <li><a href="#acknowledgments">致谢</a></li>
  </ol>
</details>

<a id="about-the-project"></a>

## 项目简介

开盖自定义是一款 Android 耳机开盖音效工具。通过蓝牙直接向耳机下发声音，无需 Root。

| 页面 | 功能 |
| --- | --- |
| 设置 | 恢复默认音效、选择 6 种预设、调节预设增益、应用音效库中的声音 |
| 处理 | 提取视频音轨、波形裁切、精确时间输入、试听、调节增益、导出 M4A/WAV |
| 我的 | 试听、重命名、排序、删除音效、保存到手机、检查更新 |

支持 Android 10 及以上。已验证 FreeClip 2，耳机固件为 7.0.0.526，手机系统为 Android 16。

### 技术栈

[Kotlin](https://kotlinlang.org/)、[Jetpack Compose](https://developer.android.com/compose) 和 [Miuix](https://github.com/compose-miuix-ui/miuix)。音频解码与导出使用 Android 系统媒体接口。

<a id="getting-started"></a>

## 安装

从 [Releases](https://github.com/oneringr/huaweipods-casetone-modify/releases/latest) 下载 `casetone-版本号.apk`，安装到手机。

首次使用时，按提示允许“附近设备”权限，再选择已配对的耳机。

<a id="usage"></a>

## 使用方法

### 应用开盖音效

1. 在系统蓝牙设置中配对耳机。
2. 将双耳放入充电盒，保持盒盖打开。
3. 关闭其他正在连接耳机的管理工具。
4. 在“设置”页选择耳机，点击音效右侧圆圈。
5. 等待写入完成，合盖再开盖试听。

播放按钮用于手机试听。点击“默认”可恢复“舒展”。

### 制作自己的音效

在“处理”页导入音频或视频，拖动波形边界或输入时间裁切片段。
增益以 100% 为中点，向左减小、向右增大，范围为 0–200%。

处理结果可保存为 M4A 或 WAV。添加到音效库的片段不得超过 6 秒。
在“设置”页应用已保存的音效，即可用作开盖声音。

预设增益同时作用于试听和下发。调节后需重新应用音效。

### 管理音效与检查更新

在“我的”页试听、排序或管理音效。长按拖动手柄可调整顺序，菜单中可重命名、保存到手机或删除。

点击版本号检查更新。有新版时，点击“去下载”打开对应 GitHub 发布页。

<a id="building"></a>

## 本地构建

需要 JDK 21、Android SDK 37 和 Git。也可使用 Android Studio 打开项目。

```powershell
git clone https://github.com/oneringr/huaweipods-casetone-modify.git
cd huaweipods-casetone-modify
.\gradlew.bat :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`。macOS 和 Linux 使用 `./gradlew`。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease
```

签名配置通过 `HUAWEIPODS_SIGNING_DIR` 读取外部文件，见 [构建配置](app/build.gradle.kts)。未配置外部密钥时，Debug 使用默认调试签名，Release 生成未签名 APK。

<a id="contributing"></a>

## 反馈与贡献

通过 [Issues](https://github.com/oneringr/huaweipods-casetone-modify/issues) 反馈问题或提出建议。设备连接问题请附上耳机型号、固件版本、Android 版本和操作步骤。

代码修改可提交 Pull Request。作者：[OneRingR](https://github.com/oneringr)。

<a id="acknowledgments"></a>

## 致谢

- [Miuix](https://github.com/compose-miuix-ui/miuix)：界面组件，采用 [Apache License 2.0](app/src/main/assets/licenses/Miuix-Apache-2.0.txt)。
- [Best-README-Template](https://github.com/othneildrew/Best-README-Template)：README 结构参考。
- [华为官网标志](https://www.huawei.com/-/media/htemplate-home/1.0.1.20251205144752/components/assets/img/favicon-logo.svg)：图标中的华为花瓣标记。

<p align="right"><a href="#readme-top">返回顶部</a></p>
