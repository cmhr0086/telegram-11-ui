# 路线图进度与交接

最后更新：2026-11-03。工作分支：`codex/roadmap`，基线 `origin/dev/ui-lib` @ `7eb17f0`，PR 目标 `dev/ui-lib`。禁止修改或推送 main，完成后推送工作分支并创建 PR，不自行合并。

## 当前方向

继续扩展已有 `tgui`，直接搬运并裁剪 Telegram Android `release-11.4.2-5469`。保留上游包名、GPL 版权声明和 `org.telegram.tgui.recyclerview` fork；新增示例放进现有 demo。旧 `codex/ui-library` 及其 core/ui/独立 demo 方案已放弃，未合并或带入本分支。

先完整阅读 AGENTS.md、README.md、CHANGELOG.md。本机 upstream 已核实为指定 tag，提交 `fb2e545101f41303f1e2712de2e7611a9335f1c3`，只读参考。使用 tools/pull.py 搬类和资源，不复制账号、网络等大块业务代码；业务操作改为模型或回调。

## 按顺序执行

| 项目 | 状态 | 证据与剩余工作 |
| --- | --- | --- |
| UndoView 撤销条 | 已完成 | 已通过 tools/pull.py 导入并裁剪归档/删除路径；保留归档 Lottie、删除 5 秒倒计时和原动画参数，已接入对话列表单项/批量操作。修复后 release/demo 构建通过；7 项设备边界测试已通过。单项/批量/过滤页原位恢复、超时、主题切换后数据保持均通过；外部宿主 debug/R8 release 通过。夜间缺字已在独立基线复现，确认是原有问题。 |
| AnimatedEmojiDrawable / AnimatedEmojiSpan | 已完成 | 宿主本地 JSON/gzip TGS 经现有 rlottie 实际渲染；原输入栏/气泡/发送路径通过模拟器截图及原生帧、像素检查，release/demo、AAR 消费端 debug/R8 release 构建通过。边界测试见下。 |
| 相册分组 | 已完成 | 已抽取 GroupedMessages 的特殊/通用布局；复用照片气泡组成相册，逐格查看器、整组多选、2–10 张和日夜切换已操作。release/demo、设备断言、AAR debug/R8 release 均通过，单 View 与上游多 cell 的 inset 差异已明确记录。 |
| 圆形视频消息 | 未开始 | 复用现有气泡，播放由宿主或 MediaPlayer 承担。 |
| 低风险限制补齐 | 未开始 | 前四项完成后再评估。 |

## 验证门槛

每项先通过 `./gradlew :tgui:assembleRelease :demo:assembleDebug`。公开 API/资源改动后先 `:tgui:publishToMavenLocal`，再编译 samples/host-app 的 debug 和 release。demo 必须在模拟器打开并操作；更新 README 组件表/用法/限制、CHANGELOG Unreleased、此文件，再单独提交。完成一项后才能进入下一项。

某项经合理尝试仍无法解决时，记录原因，回退该项未完成改动，再继续下一项；不得为了编译删掉已有功能。最终 PR 列明完成、跳过及原因、验证和人工检查项。

## UndoView 完成记录

- 来源：upstream `ui/Components/UndoView.java`。仅保留 ACTION_DELETE=1、ACTION_ARCHIVE=2、ACTION_ARCHIVE_FEW=4、ACTION_DELETE_FEW=27；宿主提供文案和 apply/undo Runnable，删去账号、MessagesController、TL 对象、支付/通话等无关分支。
- DemoDialogsFragment 保存移除对象的原 master 位置、归档计数和预览，支持单项/批量撤销；两页按各自过滤条件同步，退出页面时结算待处理操作。
- 审查发现并已修复：重入抢占不能丢掉新操作的 apply 回调；长文本预留 Undo 按钮宽度；退场中 detach 后保持隐藏；同步过滤页时处理已有项的顺序变更。修复后七项设备测试通过。
- 已验证：5 秒超时、替换、撤销及重复 hide 的一次性回调、重入、detach/reattach、长文本/窄屏；真实对话列表的单项/批量归档删除、原位撤销和 Unread 页恢复；删除到期后主题切换不复活。
- RoadmapInstrumentation 七项检查已在 Android 15 MuMu 通过。运行方式：构建 `:demo:assembleDebugAndroidTest`，安装 demo-debug.apk 和 demo-debug-androidTest.apk，执行 `adb -s 127.0.0.1:7555 shell am instrument -w org.telegram.messenger.demo.test/org.telegram.uidemo.RoadmapInstrumentation`，确认输出 `7 checks passed`（不能仅凭 adb shell 返回码判断测试通过）。
- 公开 API/资源已 publishToMavenLocal，消费端 ChatListFragment 新增长按归档/撤销以覆盖 UndoView 的 AAR/R8 路径，debug 与 R8 release 编译通过。

## 环境与已验证内容

- 用户级 JAVA_HOME：`C:\Users\CMHR\.jdks\temurin-21.0.13+9`；ANDROID_HOME：`C:\Users\CMHR\AppData\Local\Android\Sdk`。既有终端如未继承变量，先从用户级环境读入。
- Android 36、NDK 28.2.13676358、CMake 3.22.1 已就绪。新分支基线 `:tgui:assembleRelease :demo:assembleDebug` 通过（roadmap-baseline.log）；UndoView 初版同命令通过（roadmap-undo-build.log），日志在系统 TEMP。
- MuMu Android 15：ADB `127.0.0.1:7555`；SDK platform-tools/adb.exe。上一次安装的独立 demo/host 属于已放弃方案，不能作为本路线图的验收证据；当前路线图 demo-debug.apk 及设备测试 APK 已安装并完成上述检查。

## 提交与发布

UndoView 已提交为 `886b99a`，本地动态表情为 `ecb00e3`。相册已满足门槛，将独立提交；下一步为圆形视频消息。整体目标未完成，尚未推送或创建最终 PR。

## 正在定位的观察

- MuMu 上切换夜间后对话列表标题、消息和筛选标签不可见，日间恢复。已创建 detached 基线工作树 `D:\Codex\telegram-ui-baseline-check` @ 7eb17f0，使用独立 applicationId 诊断，构建并安装独立包后复现同样症状，确认是本次改动前存在的问题；主工作树源代码未因该诊断改变，保留工作树供后续排查。
- 筛选标签的无障碍描述出现 LOC_ERR:AccDescrUnreadCount_other，记录为后续低风险候选，当前未修改。

### UndoView 最终验证

- `:tgui:assembleRelease :demo:assembleDebug :demo:assembleDebugAndroidTest :tgui:publishToMavenLocal` 通过，日志 TEMP/roadmap-undo-final.log。
- `samples/host-app` debug 与开启 R8 的 release 通过，日志 TEMP/roadmap-undo-host.log。
- 七项仪器测试输出 `7 checks passed`。真实 MuMu 单项/批量归档、单项/批量删除的撤销恢复原位；Unread 过滤页恢复原位；倒计时 5→2、到期隐藏且数据保持；日夜切换不会复活已提交删除。
- 上述截图存于系统 TEMP 下 tgui-undo-*、tgui-bulk-*、tgui-after-*；原有夜间渲染问题的基线截图为 tgui-baseline-dark.png。平板、其它 Android 版本、长时间后台/进程死亡持久化不在本项运行验收范围内。

## 阶段 C：本地动态表情

- 继续修改既有 AnimatedEmojiDrawable / AnimatedEmojiSpan，复用本地 RLottieDrawable；保留 documentId、原比例/字体及垂直位置，以 SourceProvider 文件回调代替账号/ImageReceiver/网络。JSON 与 gzip TGS 先复制/解压至私有缓存（4 MiB 上限），坏文件不删除宿主原件；来源版本改变后重新加载。
- span 的 layout 分组扫描管理多 View、多组引用，attach 时绑定、最后 detach/release 回收，可重新 attach；cloneSpans 隔离视图之间的字体修改。现有 EditTextEffects 及文字气泡接入，纯 custom emoji 跟随 emoji-only 大号字体，同长度原地修改 span 后重新绑定也会重建布局。
- Emoji.replaceEmoji 的 createNew 原来会 toString 丢失自定义 span，现保留样式并去除旧普通 EmojiSpan 后重建。demo 发送/编辑从 entity 恢复自定义表情。
- 发现并修复两项实际边界问题：①关闭输入的 NoClipCanvas 时，Android Editor RenderNode 缓存令动画停帧；改用保留裁剪的 Canvas 代理绕过文本缓存，该模式与字体调整后已通过设备帧/像素检查。②RLottie 排队首帧在 recycle 后提前 return 未通知完成，留下 nativePtr；改为完整 no-frame completion，延迟回收同时清理第二指针和 BitmapsCache。
- `:tgui:assembleRelease :demo:assembleDebug :demo:assembleDebugAndroidTest :tgui:publishToMavenLocal` 通过，日志 TEMP/roadmap-emoji-final.log；新增纯表情/同长度编辑断言的测试 APK 构建日志为 roadmap-emoji-extra-tests.log。消费端 debug 与 R8 release 日志 roadmap-emoji-host-final.log。
- Android 15 MuMu 仪器最终输出 `7 UndoView checks + AnimatedEmojiDeviceChecks passed`：真实输入/气泡的 JSON 和 TGS 原生帧先自行推进，再验证 View.draw 像素变化；无裁剪代理模式、字体变大、纯表情、原地 span 编辑、实体往返、缺失/损坏/provider 恢复、原件保留、两 View 共享、最后解绑/重绑，以及 80 次快速绑定立即回收后两 native 指针归零。仪器测试不能用无限 waitForIdleSync 等待持续动画，已改有界布局等待。
- 真实 demo：Chat 底部样例和更多菜单 “Insert local animated emoji”，输入并点击发送后均连续截图观察到两动画姿态变化。证据 TEMP/c-bubble1.png、c-bubble2.png、c-input1.png、c-input2.png、c-sent1.png、c-sent2.png。未人工逐项检查编辑菜单和系统跨应用粘贴，实体链路有设备断言；普通 🙂 是静态表情。
- 限制如 README：仅本地矢量 rlottie；同步首次加载，不含网络/WebM/外置图片；未实现自定义表情页、动态反应、媒体 caption/回复预览动画生命周期和旧 overlay 独立 filter/spoiler 参数。非 MuMu 设备、平板、超大字体/复杂 RTL 长段落尚未人工验证。现有功能未删减。

## 相册分组

- 用脚本从固定 upstream MessageObject.java 提取 GroupedMessages 的 2/3/4 张特殊布局和多图行枚举评分，保留 800/1000 span、814 高度、宽高比裁剪、边缘/跨行数据和 GPL 头；去掉消息/TL/FileLoader/分享/头像/文件/音乐业务。模型为 GroupedMediaLayout，只读本地照片尺寸。
- AlbumBubbleCell 继承既有 PhotoBubbleCell：单照片中心裁剪渲染抽成缓存 shader 的 PhotoRenderer，其余说明、回复、反应、时间、背景、长按沿用。每格独立下载/取消/播放、时长、点击身份和查看器来源/可见性；TextBubbleMessage.album 支持 2..10 个 photo/video 成员，按整组作为一行选择/删除。
- 这是单 RecyclerView 行组合，上游比例和特殊布局保留，按行归一化宽度、2dp 缝、内角上限4dp；未复刻上游多 ChatMessageCell + GridLayoutManager 的不对称 inset / 父层延后时间绘制，不能声称逐像素完全一致。文件组、captionAbove、头像让位、逐格多选和组变形动画不在本项范围，详见 README。
- 已修正首次 measure 前隐藏格状态丢失、原地更改 albumMedia 不重新布局、内角大于小气泡外角，以及窄格状态覆盖到邻格的问题。单照片渲染/点击有回归检查。
- 审查补充修复：viewer 原本丢掉父组说明，现成员无自有说明时在每页显示共享说明；点击没有可用 source 的素材不再误开第一张；工厂拒绝同组重复引用同一个成员对象，设备模型断言覆盖此情况。
- 修复后快速设备回归：四图组成员打开为10/17、翻到11/17均显示 “Album · 4 photos”，返回列表正常；TEMP/album-caption.png、album-caption-return.png。
- 构建通过：`:tgui:assembleRelease :demo:assembleDebug :demo:assembleDebugAndroidTest :tgui:publishToMavenLocal`，TEMP/roadmap-album-final.log；消费端 debug/R8 release，roadmap-album-host.log；最终断言 APK 构建 roadmap-album-test-final.log。MuMu 输出 `UndoView + AnimatedEmojiDeviceChecks + AlbumDeviceChecks passed`。
- AlbumDeviceChecks：特殊/通用模型和跨行分支，2..10 张 × 240/400dp × 收发方向，真实 View 测量/绘制，边界、内角、无重叠、每格点击身份、隐藏格、一组恰好一次时间、单图绘制/点击回归。caption/reply/reactions 参与组合测试；未精确逐像素标定所有文字边界。
- 真实 demo：2/3/4/5 张和菜单发送10张都显示不同拼接布局与单个时间；5图右上紫图打开对应图（14/17），翻到15/17后返回全部恢复；10图另一格打开20/27；长按5图选中整组计数1；旧单图能开、旧视频实播到0:04/0:06；夜间10图说明/时间可见，切回日间正常。截图 TEMP/album-three.png、album-earlier.png、album-end.png、album-ten.png、album-viewer2.png、album-return.png、album-select.png、album-night.png、album-video.png、album-single-viewer.png。

## 主题 CRLF 修复（集成回归中发现的基线缺陷）

- 圆视频回归期间，动态表情设备检查连续失败：原生帧推进但气泡像素不变。保留诊断断言并输出实际 paint/native 状态，确认 messageTextIn 与 RLottie bitmap paint 的 alpha 都为0；不是动画未解码。
- 根因：Windows 工作树 .attheme 文件使用 CRLF，Theme.getThemeFileValues 去掉 LF 却保留 CR；严格的 Utilities.parseInt 将 `-328966\r` 读为0。改为解析前只剥行尾 CR，保留原字节长度用于 WPS offset；新增 .gitattributes，attheme 后续检出用LF。不改变 Utilities 的全局解析语义。
- ThemeFileDeviceChecks 验证 LF/CRLF 的负数颜色、十六进制颜色、WLS链接、WPS二进制字节位置和打包 night/darkblue 文字色。原 AnimatedEmojiDeviceChecks 的帧/像素断言未放宽，失败诊断会保存截图和详细 paint/native 状态。
- 修复后 `:tgui:assembleRelease :demo:assembleDebug :demo:assembleDebugAndroidTest :tgui:publishToMavenLocal` 通过，TEMP/roadmap-theme-round-final.log；完整设备测试输出 `UndoView + AnimatedEmojiDeviceChecks + AlbumDeviceChecks + RoundVideoDeviceChecks passed`（含主题格式检查）。
