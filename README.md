# 开盖自定义

Android 开盒音效工具。App 自己连接耳机并下发音效，无需 Root。

## 安装与使用

- 当前版本：1.2.0。本地安装包：`dist/开盖自定义-1.2.0.apk`。
- [GitHub 下载](https://github.com/oneringr/huaweipods-casetone-modify/releases/latest)。
- 包名：`oneringr.huaweipods.modify`。最低 Android 10。
- 在系统蓝牙中配对耳机，允许本 App 使用“附近设备”。
- 在“设置”中选择耳机。双耳放回盒内，保持开盖。
- 关闭其他正在连接耳机的管理工具，再点音效右侧圆圈。
- 等待写入完成，合盖再开盖即可试听。
- 点“默认”可恢复“舒展”。

“设备已就绪”表示最近一次检测成功。App 在每次操作时重新建立连接，操作结束后释放连接。

## 三个页面

| 页面 | 功能 |
| --- | --- |
| 设置 | 默认音效、6 种预设、预设增益、我的音效库、自定义音乐 |
| 处理 | 导入音频或视频、提取音轨、波形裁切、精确时间输入、缩放、试听、0–200% 音量、M4A/WAV 导出 |
| 我的 | 音效试听、保存到手机、按名称或添加时间排序、拖动排序、重命名、删除、作者信息、开源许可、浅色和深色外观 |

6 个预设依次为 Ciallo～(∠・ω )⌒☆、MAN!、WHAT CAN I SAY、曼波、遥遥领先和钢管。
所有增益条以 100% 为中点。左滑减小，右滑增大，范围为 0–200%。
预设增益同时作用于试听和下发，修改后需重新应用。
预设增益与处理页音量分别控制。App 保持竖屏。
选择耳机时，标题和刷新按钮固定，仅设备列表滚动。
提示使用 Miuix Snackbar。进度使用 Miuix FloatingToolbar 和 LinearProgressIndicator。
两者悬浮在页面上方，不压缩内容区域。进度轨道透明。
确认、外观、排序和导出格式弹窗的操作按钮横排。
Miuix 许可可在“我的 → 开放源代码许可”中展开查看。

## 检查更新

点击“我的”页中的版本号，查询 `oneringr/huaweipods-casetone-modify` 的最新正式 Release。
有新版本时，点击“去下载”打开对应 GitHub 发布页。
版本按数字比较，例如 `1.10.0` 高于 `1.2.0`。
App 仅在用户点击时联网检查。音频处理和蓝牙下发无需互联网。

处理页允许导出长音频。添加到开盒音效库前，选区必须在 6 秒以内。
音效库统一保存为 16000 Hz、单声道、16 位 PCM。保存到手机时封装为 WAV。
普通处理导出保留源采样率。M4A 使用 AAC，可能带有编码器补齐的少量尾部时长。
视频和音频格式由手机系统解码器决定。M4A 导出支持单声道和双声道，其他声道可导出 WAV。
长音频使用缓存文件流式处理。导入时需要足够的手机存储空间。

## 已完成的验证

2026-11-12，在 Android 16 手机和 FreeClip 2 上完成验证。
耳机型号为 T0027/T0027C，固件为 7.0.0.526。

- 独立蓝牙下发自定义双音成功。用户合盖再开盖，确认听到两个短音。
- 正式签名版通过界面下发“钢管”成功，收到充电盒写入完成和选中回包。
- 正式签名版恢复默认“舒展”成功，回包的音效 ID 为 0，选择标记为 0。
- 6 项协议测试、1 项增益测试和 1 项版本比较测试通过。
  增益测试覆盖衰减、放大、静音和限幅。版本测试覆盖新版本、同版本和旧版本。
- 真机音频集成测试通过：65 秒视频提取音轨、裁切、增益、M4A/WAV 导出、6 秒边界和音效库读写。
- 系统文件选择器导入、精确裁切、调节音量、添加音效库、M4A 保存和删除测试条目通过实际界面操作。
- 代码 Lint 无错误。提示涉及竖屏锁定、目标版本、依赖更新、KTX 写法和备份配置。
- APK 签名证书与指定密钥一致。校验值见本地 `dist/build-verification-1.2.0.json`。
- 1.1.0 已覆盖安装。原有音效库索引保持一致。两处增益、竖屏、提示、图标和许可入口已核对实机界面。

FreeClip 2 使用协议 2，该分支已真机验证。协议 1 已实现，尚未真机验证。
Android 10–15 和其他耳机型号尚未完成真机验收。

## 构建

使用 Android Studio 自带 JDK 21、Android SDK 37 和 Gradle Wrapper。
构建依赖版本固定在 Gradle 文件中。

PowerShell：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease --console=plain
```

本机已缓存依赖时可添加 `--offline`。
真机音频测试命令为 `:app:connectedDebugAndroidTest`。该任务会安装调试版，完成后应重新安装正式 APK。

签名文件从外部目录读取，默认目录为 `D:\存档\安卓开发`。
可通过进程环境变量 `HUAWEIPODS_SIGNING_DIR` 指定目录。
目录内需要 `sign-key.jks` 和 `config.txt`。
配置字段为 `storePassword`、`keyAlias`、`keyPassword`。
不要提交密钥或口令。
没有外部密钥时，Debug 使用 Android 默认调试签名，Release 生成未签名 APK。
发布版使用作者自己的外部签名。

## 代码和证据

- 模块入口见 `AGENTS.md`。
- 协议细节与开发约束见 `HANDOFF.md`。
- 正式版下发记录：`evidence/release-preset-upload.log`。
- 默认恢复记录：`evidence/release-default-restore.log`。
- 真机音频测试：`evidence/audio-device-test.txt`。
- 早期 Root 缓存替换研究见 `research/RESULTS.md`。

界面使用 [Miuix 0.9.4](https://github.com/compose-miuix-ui/miuix)。Apache 2.0 许可文本随 APK 提供。
作者页展示 [OneRingR](https://github.com/oneringr) 的公开头像、名称和链接。
6 个预设来自用户提供的 `D:\backup\Music\音效`。
运行 `python scripts/update_presets.py "D:\backup\Music\音效"` 可重新转换。
启动图标的华为花瓣路径来自[华为官网标志 SVG](https://www.huawei.com/-/media/htemplate-home/1.0.1.20251205144752/components/assets/img/favicon-logo.svg)。
