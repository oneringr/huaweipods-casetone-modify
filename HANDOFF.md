# 项目交接

## 当前 App：独立蓝牙下发

应用名称为“开盖自定义”。包名为 `oneringr.huaweipods.modify`。
最低 Android 10。界面使用 Miuix 0.9.4。
App 通过公开 RFCOMM 接口连接耳机，自行打包并下发声音。无需 Root。
不读取华为智慧音频私有目录，也不操作华为智慧音频界面。

2026-11-12，FreeClip 2 协议 2 的独立下发通过用户听感确认。
正式签名版再次通过界面应用“钢管”，收到盒端写入完成和选中回包。
随后恢复“舒展”，设置回包为 ID 0、选择标记 0。
对应记录为 `evidence/release-preset-upload.log` 和 `release-default-restore.log`。
正式 APK 已安装到本次验收手机。测试音效库条目和测试下载文件已清除。

### 蓝牙约束

- 使用安全 RFCOMM SPP UUID `00001101-0000-1000-8000-00805f9b34fb`。
- Android 12 起需要 `BLUETOOTH_CONNECT`。从已配对设备中选择，不扫描设备。
- 双耳须在盒内，盒盖保持打开。
- 其他耳机管理工具的后台连接可能占用通道。仅退出界面未必释放连接。
- App 每次操作建立短连接，结束后关闭。连接失败会重试一次。
- 状态卡片“设备已就绪”是最近一次检测结果，不表示保持长连接。

### 协议容易出错的地方

- 能力查询必须发送 `2bb30101010e0100150100`。首个订阅值为 1。
- FreeClip 2 返回协议 2。协议 1 分支已实现，尚未实机验证。
- MBB 帧使用 `5a + BE16正文长度 + 控制字节 + 内容 + BE16 CRC`。
- CRC 为 CCITT，多项式 `0x1021`，初值 0。
- 分片控制字节为 1、2、3。流读取必须处理半帧、连续帧和分片重组。
- TLV 扩展长度按 7 位拼接，不能按普通 8 位整数解析。
- 自定义音复用 `Whistle`，ID 43。恢复默认使用 ID 0、选择标记 0。
- 音频头为 48 字节。头 CRC 不包含采样率字段。
- 外层包为大端正文长度、SHA-256、音频头、PCM 和 4 字节 MCU 尾部。
- 协议 2 的 `0903` 请求中，分块位图是字段 3，不是字段 4。
- 进度包含手机到耳机、耳机到盒两段。首段完成不能视为盒端写入完成。
- 正式版不执行调试 Intent。双音测试素材仅在 debug assets 中。

### 音频与存储

1.1.0 使用用户更新后的 6 个音效。原始文件目录未改动。
运行 `python scripts/update_presets.py "用户音效目录"` 可重新转换全部预设。
文件名映射固定在脚本中。预设显示名称与 `AppModel.kt` 同步维护。
预设区使用共用增益，范围为 0–200%，保存到偏好设置。
所有增益滑杆以 100% 为中点，向左衰减，向右放大，并在中点吸附。
试听和下发共用增益后的 PCM。16 位样本超出范围时限幅。
改变预设增益会取消当前预设的选中标记，需重新应用。
处理页的增益独立于预设增益。

### 当前界面约束（1.2.0）

- 主 Activity 锁定竖屏。
- 启动图标为打开盒盖、露出双耳的充电盒，并带有音符。
- 盒身保留华为花瓣标志。
- 标志路径来自华为官网 favicon SVG，来源见 README。
- 提示使用 Miuix SnackbarHost 和 Snackbar，保留强调色文字和浅底。
- 进度使用 Miuix FloatingToolbar 和 LinearProgressIndicator。轨道透明。
- 两者使用 Scaffold 的悬浮槽位，不占用页面内容高度。
- 确认、外观、排序和导出格式弹窗的按钮统一横排。
- 设备选择窗口的标题和刷新按钮固定，仅设备列表滚动。
- “我的”页面将 Miuix 收入“开放源代码许可”折叠项。
- 两处增益均复用 `GainSlider`。手势和无障碍操作仍使用 Miuix Slider。
- 增益轨道从中点填充，避免误读为从零开始的普通进度条。
- “钢管”放在预设列表最后。处理页统一使用“增益”名称。

### 更新发布

GitHub 仓库为 `https://github.com/oneringr/huaweipods-casetone-modify`。
点击“我的”页版本号，才会查询公开 API `repos/oneringr/huaweipods-casetone-modify/releases/latest`。
只比较正式版本，支持带 v 前缀的数字版本。新版本用按钮跳转到 Release 页面。
查询失败通过悬浮提示显示。不会自动下载或自动安装 APK。
APK 需要 INTERNET 权限。蓝牙与音频处理不依赖网络。
发布流程使用 gh cli。签名文件、口令、设备证据和反编译产物不提交。

1.2.0 真机已核对删除、外观和导出格式的横排按钮。
600 秒音频导入完成，悬浮进度位于底部导航栏上方，处理页显示“增益”。
GitHub 尚无 Release 时，真机已显示“GitHub 暂无发布版本”。
此次测试临时媒体和解码缓存已删除。用户原有音效库索引保持一致。
FloatingToolbar 需要增加 `NavigationBarDefaults.ItemHeight` 的底部留白。
Miuix 0.9.4 的 Scaffold 悬浮工具栏位置只扣除系统底部边距，不会自动避开 NavigationBar。

1.1.0 已在真机覆盖安装。用户原有音效库索引在安装前后逐字节一致。
预设增益实测左右滑动显示 43% 和 156%。处理页显示 42% 和 160%。
两处均已复位到 100%。横屏请求下，系统仍报告竖屏和旋转 0。
设备弹窗、许可折叠、许可正文、6 个预设、强调色提示和启动图标均已查看实机界面。
蓝牙状态检测成功。此次修改未改动传输协议，也未替换盒内当前音效。
验证记录为 `evidence/v110-*.xml`、`v110-*.png` 和 `dist/build-verification-1.1.0.json`。

### 音频实现

长音频先解码到缓存 PCM，再分块裁切、调节增益和导出。
M4A 使用系统 AAC 编码器，WAV 为 PCM16。
音效库限制为 6 秒，并转换为 16 kHz 单声道 PCM16。
精确裁切以采样帧为准。M4A 文件时长可能包含 AAC 编码器补齐。
音效库使用 UUID 文件名和 AtomicFile 索引。排序、重命名、删除不修改导入原文件。

### 构建与验证

- 使用 Android Studio 的 JDK 21。构建命令见 `README.md`。
- 签名目录由 `HUAWEIPODS_SIGNING_DIR` 指定，默认使用用户指定的外部目录。
- 不在项目中保存密钥或口令。发布环境中 Debug 和 Release 均使用外部个人签名。
- 未配置外部密钥时，Debug 使用默认调试签名，Release 不签名。
- 6 项协议测试、1 项增益测试和 1 项版本比较测试通过。真机音频集成测试通过。
- 真机测试覆盖 65 秒视频、裁切、增益、两种导出格式、6 秒边界和音效库。
- 1.1.0 Lint 为 0 错误、15 个提示。包含用户要求的竖屏锁定提示。
- Android 16 和 FreeClip 2 固件 7.0.0.526 已验收。其他系统版本和耳机型号尚未实机验收。
- 运行 connectedDebugAndroidTest 会覆盖安装调试版。交付前须重新安装正式 APK。

## 早期已验证方法：Root 缓存替换

2026-11-12，已在 FreeClip 2 上完成自定义开盒声音测试。
手机运行 Android 16。华为智慧音频版本为 2.0.7.321。
耳机型号为 T0027/T0027C。固件版本为 7.0.0.526。

方法是替换 App 私有缓存中的 `Whistle.pcm`，再在官方界面选中“口哨”。
App 会重新打包并下发音频。无需修改 APK 或耳机固件。
测试音为低音量双音，长度为 0.85125 秒。
App 下发完成并选中“口哨”。用户合盖再开盖后，确认听到了两个短音。
原始文件已还原，原始口哨已重新下发，界面已选回“舒展”。

## 权限与操作约束

- 以 `adb shell id` 判断权限。`su` 不存在不代表 ADB 没有 root。
- 当前 shell 为 `uid=0`，SELinux 上下文为 `u:r:ksu:s0`。
- 原文件属主和属组均为 `10428`，模式为 `600`。换安装后 UID 可变化。
- `adb push` 会改变文件属主和权限。先推送到单个临时文件，再用 `cat 临时文件 > 目标文件` 覆盖现有文件。
- 此设备上，向 `adb shell -T` 的标准输入传 PCM 会被截断。不要用此方式传二进制。
- `scripts/replace_whistle.py` 会先备份，再写入并检查内容及属主权限。
- 文字区域用于试听。右侧圆圈用于下发和选中。
- 两耳须在盒内，盒盖保持打开。以官方页面提示为准。
- 文件还原后，需要再次下发原始口哨，才能覆盖设备里已有的自定义音频。
- App 更新资源包时，缓存文件可能被覆盖。需要重新替换并下发。
- 本方法复用“口哨”入口，界面名称仍为“口哨”。

## 文件与格式

目标文件：
`/data/user/0/com.huawei.smartaudio/files/00000A/promptTone/Whistle.pcm`

原始文件为 27240 字节。
SHA-256：`57cfe5150259327423d1a7dacea2fa17e0b72e49ced338a01acc965ade89a4e9`。

音频为 16000 Hz、单声道、16 位小端 PCM。没有 WAV 文件头。
原生录音界面上限为 6 秒。尚未测试更长音频。
工具按此上限限制为 192000 字节。
默认“舒展”的 ID 为 0，走默认音分支。不要通过替换它来做上传测试。
“口哨”ID 为 43，名称为 `Whistle`，走音频上传分支。

## 代码入口

- `decompiled/sources/p166cp/C9168r.java`：原类 `cp.r`，提示音 Presenter。
  `m33461Dn` 读取 PCM，`mo33526al` 选择传输分支，`m33529ho` 申请上传。
- `decompiled/sources/com/huawei/audiodevicekit/prompttone/util/C6607g.java`：
  原类 `prompttone.util.g`，生成音频头、CRC、SHA-256 和传输内容。
- `decompiled/sources/p021ap/C2105g.java`：原类 `ap.g`，两种上传协议与回包解析。
- `decompiled/sources/p038bp/C2267c.java`：原类 `bp.c`，提示音设置与入口能力配置。
- `decompiled/sources/com/huawei/audiodevicekit/prompttone/util/C6605e.java`：
  原类 `prompttone.util.e`，录音采样格式。

jadx 开启了反混淆。磁盘路径与 APK 原类名不同。
整包反编译有 80 处错误。上述主要研究文件没有未反编译方法占位。

## 原生自定义入口

`ConfigBean.isSupportCustomTone()` 控制录音入口。
内置 `config_ZAAS.json` 的版本为 3，未设置此字段，默认关闭。
设备缓存中的同名配置版本为 2，也未设置此字段。
云配置只有比内置版本高、且满足 `minAppVersion` 时才被采用。
仅修改较低版本缓存中的字段，不足以证明入口会解锁。
未修改此配置，也未验证原生录音入口。
已验证的文件替换方案不依赖这个开关。

## 证据与工具

- `evidence/device-backup/`：设备原始口哨、提示音表和产品配置。
- `evidence/custom-two-note.pcm`：实测通过的自定义双音。
- `evidence/ui-custom-selected.xml`、`custom-select-2s.png`：自定义音下发记录。
- `evidence/ui-original-whistle-resent.xml`：原始口哨重新下发后的选中记录。
- `evidence/ui-final-restored.xml`：最终默认音选中记录。
- `scripts/replace_whistle.py`：实际文件替换和还原工具。
- `scripts/pack_tone.py`：离线打包研究工具。实际替换流程不需要手工生成包。
- `research/redirect_whistle.frida.js`：早期注入方案草稿，仅做语法检查。
  当前 App 已独立下发，通常无需使用注入或文件替换。

原始 APK SHA-256：
`38226563e93a3e38e1e7490e0851fa1a11e318c281f6d8d977bbc7158e2123a4`。
界面 XML 中的设备别名、地址和序列号已脱敏。
