# 项目说明

本项目研究华为 FreeClip 2 的开盒提示音自定义方式。
当前交付为可独立通过蓝牙下发音效的 Android App。使用说明见 `README.md`。

## 模块路径

- `app/src/main/java/oneringr/huaweipods/modify/bluetooth/`：独立蓝牙连接、MBB 数据帧和音效下发。
- `app/src/main/java/oneringr/huaweipods/modify/audio/`：视频音轨提取、PCM 解码、裁切、增益、M4A/WAV 导出。
- `app/src/main/java/oneringr/huaweipods/modify/ui/`：Miuix 三页界面与波形时间轴。
- `app/src/main/java/oneringr/huaweipods/modify/data/`：本地音效库与排序持久化。
- `app/src/main/java/oneringr/huaweipods/modify/update/`：GitHub Release 查询和版本比较。
- `app/src/main/assets/`：6 个预设音效、作者头像和开源许可。
- `app/src/test/`、`app/src/androidTest/`：协议测试和真机音频处理测试。
- `app/build.gradle.kts`：构建和外部签名配置。禁止把签名口令写入仓库。
- `dist/`：正式签名 APK 和签名校验结果。
- `research/`：研究结论、流程图草稿和待实测注入脚本。
- `scripts/`：离线提示音包工具。不会向设备发包。
  `update_presets.py` 按固定名称读取用户音效目录，更新全部 6 个 PCM。
- `evidence/`：原始 APK、官方资源、脱敏界面记录和验证结果。
- `decompiled/sources/`：反编译代码。按 `HANDOFF.md` 中的路径定向查看。
- `decompiled/resources/`：APK 资源。
- `tools/`：本次研究使用的 jadx。

默认跳过反编译的无关模块。修改手机、安装 APK 或下发自定义声音前，先区分研究证据和实机验证。
