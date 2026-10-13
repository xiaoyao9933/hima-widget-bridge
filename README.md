# 智行快捷 · HimaWidgetBridge

通过原版鸿蒙智行的小组件，为 Android 手机提供「切换车锁」和「后备箱」快捷入口，可用于桌面快捷项、手机 AI 键、系统场景及可写 NFC 贴纸。

这是独立辅助应用，包名为 `com.eric.himawidgetbridge`。使用时需要安装原版鸿蒙智行、登录自己的账号，并完成车辆小组件绑定。车辆状态、鉴权及蓝牙／网络控制方式由原版处理。

## 下载与安装

从 [Releases](https://github.com/xiaoyao9933/hima-widget-bridge/releases) 下载 `hima-widget-bridge-0.6.1-universal.apk`。

- Android 8.0 及以上；NFC、蓝牙硬件均为可选。
- universal APK 没有架构专属的原生库，使用同一安装包。实际兼容性仍取决于手机系统、原版 App 版本和车辆小组件。
- 已安装此前同签名「智行快捷」的用户可直接覆盖安装，保留设置和绑定。
- 当前验证设备为荣耀 Magic9／MagicOS 11，适配的原版鸿蒙智行为 `3.0.2.320`；其他手机和车型仍需实测。

## 首次设置

1. 打开「智行快捷」，进入右上角「设置」。桌面应用图标只打开首页。
2. 绑定车辆小组件，在 Android 授权框中确认「创建」。
3. 等待原版按钮加载，分别启用需要的车锁／后备箱快捷项。
4. 开启通知使用权，用于读取原版数字车钥匙连接通知；开启使用情况访问权限，用于预热时判断原版页面和自动回切。
5. 长按桌面图标选择独立快捷项，或在手机 AI 键／场景中指定「切换车锁」或「后备箱」。选择「打开应用」只进入首页。

「切换车锁」由原版按当前状态决定锁车或解锁。「后备箱」调用原版对应按钮，原版可能按当前状态开启或关闭。原版要求的安全码或确认仍由用户完成。

## NFC 碰一碰

车锁和后备箱各用一枚普通可写 NDEF 标签。贴在车身金属位置时使用抗金属标签。

1. 打开手机 NFC，从首页进入对应标签管理页，点击「录入可写 NFC 标签」。
2. 第一次碰触只读取信息；确认覆盖标签原有内容后，移开并再次碰触同一标签写入。
3. 若出现「再次碰触，验证写入」，完全移开并再次碰触，保持贴近。仅在身份和本次标识匹配后保存绑定。
4. 录入本身不操作车辆，成功后碰触触发默认关闭。确认对应快捷项已启用，再手动开启标签触发。
5. 退出录入页，在亮屏解锁时碰触标签，发起对应原版操作。

两枚标签的绑定、开关与移除互不影响；同一标签不能同时绑定两种操作。重新录入会关闭该用途的触发，移除本机绑定不擦除实体标签。

这是快捷触发贴纸，车控仍依赖原版 App。普通 NFC 手机的 HCE 模拟不能替代录入所需的稳定 UID 可写标签。

## 连接准备与操作保护

- 蓝牙关闭时先尝试开启；手机系统不允许直接开启时显示系统确认框。
- 新的钥匙会话、通知重建或历史未知时，打开原版短暂预热并自动返回；同一已准备会话跳过预热。
- 未连接时最多等待 10 秒，再由原版选择控制方式。通知「已连接」和页面预热无法保证最终点击时车控模块已就绪，仍可能要求安全码。
- 两个动作共用防重复保护；倒计时代表助手的触发保护，不是车辆执行回执。
- 「已触发原版…」表示已点击原版按钮，车辆结果由原版确认。

## 权限与数据

| 权限 | 用途 |
| --- | --- |
| 小组件绑定 | 经系统授权加载原版车辆小组件 |
| 附近设备／蓝牙 | 检查蓝牙并发起开启请求 |
| 通知使用权 | 检查原版数字车钥匙通知和会话变化 |
| 使用情况访问 | 预热时判断前台并自动回切 |
| NFC | 录入和读取两种快捷标签 |

助手没有 Internet、定位、无障碍、Shizuku 或悬浮窗权限。标签只包含专用触发标识与应用信息；本机保存标签身份及标识，不保存车辆密钥。录入诊断仅记录阶段、容量、回读结果和异常类型，不记录 UID、标识或完整标签内容。

## 从源码构建

需要 Python 3、JDK 17、Android SDK Platform 34、Build Tools 36.0.0、Eclipse ECJ 3.37.0。无需 Gradle 或 NDK。

安装 SDK 包可使用 Android Studio 的 SDK Manager，或 [Android 官方命令行工具](https://developer.android.com/tools/sdkmanager)：

```bash
sdkmanager "platforms;android-34" "build-tools;36.0.0"
```

在仓库目录下载编译器：

```bash
mkdir -p .build-tools
curl -fL https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.37.0/ecj-3.37.0.jar \
  -o .build-tools/ecj-3.37.0.jar
```

ECJ 文件的 SHA-256 应为 `cde026ff966b48b5e5f148b6f041ceff3cf4f85cf75155f4ec0f40e4ee14b545`。

将 `JAVA_HOME` 指向 JDK 17，`ANDROID_HOME` 指向 Android SDK 根目录，然后运行：

```bash
python3 -m unittest discover -s tests -p 'test_build_config.py' -v
python3 build.py
```

构建自动执行 200 项纯策略检查，生成 DEX，进行 16KB ZIP 对齐和 v2/v3 签名，输出 `dist/hima-widget-bridge-0.6.1.apk`。没有 JNI 原生库，输出为通用 APK。

首次自行构建会在被 Git 忽略的 `.local-signing/` 创建开发签名。自建签名与本仓库 Release 签名不同，不能覆盖安装 Release；后续自建升级需保留同一签名文件。

可选环境变量：

| 变量 | 用途 |
| --- | --- |
| `HIMA_ECJ` | ECJ JAR 路径 |
| `HIMA_BUILD_TOOLS` | 含 `aapt2`、`zipalign`、`lib/d8.jar` 的 Build Tools 目录 |
| `HIMA_ANDROID_JAR` | 编译所用 `android.jar` |
| `HIMA_KEYSTORE` | 自己的 PKCS12 签名文件 |
| `HIMA_KEY_ALIAS` | 签名别名，默认 `widget-helper` |
| `HIMA_KEYSTORE_PASSWORD` | 签名密码，通过环境传递给签名工具 |
| `HIMA_RUNTIME` | 可选的本地固定工具目录；普通 SDK 构建不需要 |

设备测试 APK 可在主应用构建后运行 `python3 device-tests/build.py` 生成。设备测试必须按具体 runner 的范围执行；普通构建不会连接手机或操作车辆。NDEF 格式与隔离配置检查通过，车锁贴纸录入已完成实际验证；后备箱贴纸和跨机型行为尚未实测。

## 版本 0.6.1

- 简洁首页、独立设置与诊断页。
- 车锁和后备箱独立快捷项、独立 NFC 标签。
- 钥匙会话准备、原版预热与自动回切。
- 修复写入后立即回读为空导致录入失败：必要时再次碰触独立验证。
- 修复录入期间切到后台再返回的未验证流程处理。

项目与图标归属说明见 [NOTICE.md](NOTICE.md)。
