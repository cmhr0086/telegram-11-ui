# Listen 宿主接入记录

2026-11-05，目标分支 `codex/listen-host-fixes`，基于 `dev/ui-lib@e40ffdf`（ui5）。Listen 的应用移植在其独立 `codex/tgui-port` 分支，保留原 UI 直到验收。

## 本机验证

- Windows，完整 Temurin 21.0.13+9，SDK 36，NDK 28.2.13676358，CMake 3.22.1。
- `gradlew.bat :tgui:publishToMavenLocal :tgui:assembleRelease :demo:assembleDebug`：**通过**（19 秒，102 tasks）；发布 `org.telegram.ui:tgui:11.4.2-ui5`。
- 原 JBR 21 安装缺 `jlink.exe`，首次 JdkImageTransform 失败；改用完整 JDK 解决，无源码绕过。
- 两库 `core.autocrlf=false`。切换分支留下的四个 CRLF-only 文件规范为 LF，无业务改动；主题资源保持 LF。
- 本次只用 Android SDK adb 与 MuMu 127.0.0.1:16384（Android 15、x86_64、1080×1920、480 dpi）；没有安装 demo 或操作真机。

## 宿主职责

Listen 自行实现 LifecycleOwner/ViewModelStoreOwner、Activity 权限/文件/相机结果、导航 Intent 和系统栏 insets。使用 ui5 原有 BaseFragment、ChatFragmentBase 和 fork RecyclerView，不复制库代码。页面在 onFragmentCreate 观察数据，createView 只绑定现有状态。

目前未确认必须修改 tgui 的缺陷，因此本分支暂时只有接入记录。若后续修库，将独立后缀发布本地 Maven，记录具体提交和消费者构建，不覆盖 ui5。

## 未验证与许可

Listen 全页面设备验收、真实麦克风、VAD、arm64、真机、长时间录音尚未由本记录证明。Listen 的 Apache-2.0 与本库 GPL v2 or later 的组合分发需要所有者决策；本次不修改任何许可证、不发版、不合并。
