# 更新日志

版本号格式：`11.4.2-uiN`，前面是所基于的上游版本，`uiN` 是本库自己的迭代号。

## 11.4.2-ui2

### 新增
- **聊天背景与气泡**：聊天页背景（含暗色图案壁纸）、文字气泡、输入栏，以及聊天音效和气泡动画。
- **上游 ChatActivityEnterView 作聊天输入**：用移植的上游输入栏取代原先的简化版。
- **Telegram 表情**：表情渲染改用 Telegram 自己的 emoji，录音按钮可开关。
- **表情面板**：移植上游 EmojiView 的表情页（标签条、肤色选择、滚动辅助），跟随主题切换，面板显示/隐藏带动画，默认高度加高。
- **46 个 Lottie 插图**：打包上游的大尺寸 Lottie 插图。
- **聊天列表**：
  - 模型驱动的 `DialogCell`，移植 `AvatarDrawable`；
  - 左右滑动操作、多选模式、`DialogsItemAnimator` 列表动画；
  - 文件夹标签、侧边抽屉、撰写按钮；
  - 下拉显示隐藏的归档；
  - 左右滑动切换文件夹页。
- **键盘跟随**：Android 11+ 上输入区逐帧跟随输入法动画；键盘稳定后不再二次跳动。

### 修复
- 强制 edge-to-edge 下 `AlertDialog` 的内边距问题。
- 演示应用包名改为 `org.telegram.messenger.demo`（命名空间 `org.telegram.uidemo`）。
- **发布的 AAR 无法被宿主 dex**：`RecyclerView$State$LayoutState`、`FastScroller$State` / `$DragState` / `$AnimationState`、`EmojiView$Type` 这 5 个非 public 的嵌套 `@IntDef` 会被 AGP 打包时从 AAR 删掉，但 Java 17 的 `NestMembers` 属性仍引用它们，宿主 `mergeExtDex` 时 D8 报 `requires its nest mates … (unavailable)`。已去掉这 5 处的 `@IntDef`（注解类型和常量保留），AAR 里不再缺类，宿主无需任何绕过。

### 宿主需要注意
- **新增依赖**：`androidx.viewpager:viewpager:1.0.0`（表情面板用到）。通过 Maven 引用时会自动传递；直接引用 AAR 的宿主需要自行添加。

### 工程
- 加入 GitHub Actions 构建（`.github/workflows/build.yml`），产出 AAR 和演示 APK。
- CI 增加一步：发布到 mavenLocal 后构建 `samples/host-app`（debug + R8 release），覆盖只在 AAR 路径上出现的问题。
- 演示应用 versionCode 2 / versionName 1.1。

## 11.4.2-ui1

- **第一阶段**：从 Telegram Android 11.4.2 抽取经典 UI 组件，做成 `tgui` 库和 `demo` 演示应用，纯 Java 实现。
- **Lottie**：接入上游 rlottie 原生库（`libtguilottie.so`，四个 ABI），并带上 11 个小鸭子（Utya）动画。
