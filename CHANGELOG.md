# 更新日志

版本号格式：`11.4.2-uiN`，前面是所基于的上游版本，`uiN` 是本库自己的迭代号。

## 11.4.2-ui4

### 新增
- 媒体消息气泡，按上游 `ChatMessageCell` 对应分支移植（尺寸、绘制顺序、颜色 key、动画参数照搬），和文字气泡一样支持单击菜单、长按多选、日夜切换：
  - `VoiceBubbleCell`：语音消息，`RadialProgress2` 播放 / 暂停（出站渐变气泡上图标镂空）、`SeekBarWaveform` 波形（可拖动定位）、时长 / 已播放时间、未听圆点；宿主负责播放。
  - `PhotoBubbleCell`：图片，上游尺寸规则和分组圆角、图片上的时间小胶囊、可选说明文字、可选下载 / 取消 / 进度圆圈；图片是宿主给的 `Drawable`。
  - `DocumentBubbleCell`：文件，圆形文件图标 / 下载 / 取消按钮、两行文件名、大小与扩展名、下载进度文字、⋮ 图标。
- `TextBubbleMessage` 增加 `type` 和媒体字段，以及 `voice(...)` / `photo(...)` / `document(...)` 工厂方法；`TextBubbleCell.MediaDelegate`（`onMediaPressed`、`onSeek`）和 `setMediaDelegate`；`TextBubbleCell.MEDIA_THEME_KEYS` / `addMediaThemeDescriptions` 登记媒体气泡的颜色。
- `RadialProgress2`（上游原版，去掉封面覆盖图）。
- 气泡里的回复引用：`TextBubbleMessage.replyTo`（`TextBubbleMessage.ReplyTo`：名字、预览、可选缩略图、原消息 id、`colorId`；`ReplyTo.of(message, name)`），文字 / 语音 / 图片 / 文件气泡都会在顶部画上游的回复块（新类 `ReplyMessageLine`）；点按走新的 `TextBubbleCell.Delegate.onReplyPressed`，长按走 `onBubbleLongPressed`；`TextBubbleCell.setHighlightedAnimated()` 是上游跳到原消息后的高亮闪烁。
- 文字气泡里的链接：网址、@提及、#话题按上游 `MessageObject.addLinks` 识别并着色（`TextBubbleCell.addLinks(text)` 也可单独用，`TextBubbleCell.detectLinks = false` 关闭），按下有上游 `LinkSpanDrawable` 高亮，点按 / 长按回调 `Delegate.onLinkPressed` / `onLinkLongPressed`；手指拖过触摸阈值会把手势还给列表，照常滚动。
- 日期分隔：新类 `ChatActionCell`（上游服务消息胶囊，`setCustomDate` / `setCustomText` / `setVisiblePart` / `setFloating`，`ChatActionCell.formatDate` 给出 Today / Yesterday / 上游 `chatDate`、`chatFullDate` 格式）；`TextBubbleMessage.date`（unix 秒）。
- `Theme.applyChatServiceMessageColor(...)`（上游移植）：壁纸加载完、切换主题时重算服务消息底色和文字色，动态渐变壁纸用壁纸位图做 shader。之前 `chat_actionBackgroundPaint` 没有颜色、`chat_actionTextPaint` 没有字号。
- `TextBubbleCell.MEDIA_THEME_KEYS` 加上回复、链接按下底色和服务消息的颜色 key。
- demo：示例消息分成三天并插入日期分隔，滚动时顶部浮出当前日期；示例里有回复文字和回复图片的消息，点引用跳到原消息并闪烁；从输入栏回复面板发出的消息带引用；有一条带链接、@提及和 #话题的消息，单击打开网址，长按弹出“打开 / 复制”。
- demo：录完语音会生成语音气泡并用 `MediaPlayer` 播放（波形进度跟着走）；示例里加了语音、图片（含说明文字和待下载的一张）、文件消息，点下载按钮模拟下载进度。

- 气泡里的反应：新类 `ReactionsLayoutInBubble`（上游同名类按原数值移植到纯模型：26dp 胶囊、换行排布、`chat_in/outReactionButton*` 颜色、新增 / 移除 / 移动 / 变色动画，静态 Telegram 表情）和 `CounterView`（上游原版，反应计数的数字滚动动画）。文字、语音、图片、文件和纯 emoji 消息都会画反应并相应加高气泡；图片有反应时下面接一条气泡放反应和时间，和上游一样。
- `TextBubbleMessage.reactions`（`TextBubbleMessage.Reaction`：`emoji`、`count`、`chosen`）、`getReaction(emoji)`、`toggleReaction(emoji)`（上游非 Premium 规则：一人一个反应，再点取消，换一个则替换）。对同一条消息改完 `reactions` 再 `setMessage` 会播放变化动画。
- `TextBubbleCell.Delegate.onReactionPressed` / `onReactionLongPressed`：单击 / 长按反应胶囊（按下回弹，手势不会再触发整行单击或菜单）。
- `MessageContextMenu.setSelectedReactions(message)` / `setSelectedReactions(Collection<String>)`：反应条里已选的表情画上游的选中圆底并缩小。
- `TextBubbleCell.MEDIA_THEME_KEYS` 加上反应胶囊的 6 个颜色 key。
- demo：示例消息带上反应（文字、语音、图片、文件、纯 emoji 都有），单击胶囊切换自己的反应，长按弹计数提示；菜单反应条显示已选项，选表情会加 / 换 / 取消反应并带动画。

- 对话列表搜索：新类 `DialogsSearchController`（上游 `DialogsActivity` 的 `searchItem` / `showSearch` / `setSearchAnimationProgress` 按原数值移植：放大镜展开搜索框，文件夹标签淡出并收起，写消息按钮隐藏，列表缩到 0.9 淡出、搜索页从 1.05 淡入，200ms / 180ms；有最近记录时上游白色搜索栏从放大镜处圆形展开，图标和状态栏变深色；返回键或箭头反向收回）和 `DialogsSearchView`（上游 `SearchViewPager` 对话页 + `DialogsSearchAdapter`：空查询显示 People 头像横排和带 Clear 的 Recent，有查询显示 Chats / Global search / Messages 分组，没有结果显示 “No results” 空状态）。结果由宿主通过 `DialogsSearchView.Delegate.onSearch(query, callback)` 异步给出。
- 新单元格（上游移植到 `DialogItem`）：`GraySectionCell`（分组标题 + 右侧按钮）、`ProfileSearchCell`（聊天搜索行：头像、名字高亮、私密锁、认证、状态行、最近记录的未读数）、`HintDialogCell`（People 横排头像：未读数、在线圆点）。
- `DialogItem.status` / `statusOnline`（搜索行第二行）、`highlightedWords`（`DialogCell` 和 `ProfileSearchCell` 里高亮，消息会按上游 `ellipsizeCenterEnd` 截到匹配处）、`fillAvatarDrawable(AvatarDrawable)`。
- `ActionBar.setSearchRevealProgress(progress, fromColor, toColor, circle)`：上游白色搜索栏的圆形展开 / 整体渐变和底部分隔线。
- `DialogsFloatingButton.hide(hide, animated)`：不带动画地隐藏 / 显示。
- demo：对话列表的放大镜，本地过滤示例对话、一小段消息历史和三个“公开频道”，结果延迟 120ms 回调模拟异步；点结果弹 Toast 并把聊天移到 Recent 顶部，Clear 弹上游确认框。

- 个人资料页：新类 `ProfileFragment`（上游 `ProfileActivity` 的头部按原数值移植到纯模型 `ProfileFragment.Profile`：`needLayout` / `setAvatarExpandProgress` / `needLayoutText` 的几何与缩放、`TopView`、`OverlaysView` 上下渐变；42dp 圆形头像在 ActionBar 区域、名字和在线状态在右侧，上滑时头像 / 名字 / 状态缩进 ActionBar，下拉超过阈值（`expandProgress` ≥ 0.33）时 `expandAnimator` 把头像展开成全宽正方形、名字和状态落到渐变上，松手按上游吸附到展开或收起；点头像也会展开 / 收起；写消息浮动按钮贴在头部底边，按上游 150ms 缩放淡入淡出；认证标记展开时交叉淡入白色版本，静音图标；⋮ 菜单项、写消息按钮由宿主提供，`Delegate` 回调）。只有带照片（`Profile.photo`）的资料能下拉展开，和上游一样。
- `ProfileFragment.Row`：资料页下方的信息行，用上游的单元格和颜色：`detail`（电话 / 用户名，`TextDetailCell`）、`bio`（多行简介）、`notifications`（`NotificationsCheckCell` 开关，点击先翻转 `checked` 再回调）、`text`（`TextCell` 图标 + 计数，共享媒体入口）、`destructive`（红色“Block user” / “Delete contact”）、`shadow`；每行自带点击回调，改完字段调 `updateRow(row)`，改完资料调 `updateProfile()`。
- `TextDetailCell`（上游原版）、`CrossfadeDrawable`（上游原版）；`NotificationsCheckCell.updateColors()`（值文字是 `AnimatedTextView`，`ThemeDescription` 改不到）。
- 资源：`profile_newmsg`、`floating_shadow_profile`、`verified_profile`、`msg_block`、`msg_photos`、`msg_filehq`、`msg_link2`、`msg_gif`、`msg_addcontact`、`msg_qrcode`、`msg_openprofile`。
- demo：聊天页点标题 / 副标题、对话列表点头像、抽屉新增的 “My Profile” 都会打开资料页；⋮ 菜单里有日 / 夜切换，通知开关会给名字加静音图标，Block user 弹上游确认框。

### 变更（不兼容）
- `TextBubbleMessage.reaction`（单个字符串、固定计数 1 的小胶囊）删除，改用 `reactions` 列表；原来 `message.reaction = emoji.equals(message.reaction) ? null : emoji` 的写法换成 `message.toggleReaction(emoji)`。

### 修复
- `Theme.createChatResources` 补上上游的媒体文字字号（`chat_infoPaint`、`chat_docNamePaint`、`chat_audioTimePaint` 等原来是默认字号）和 `chat_filePath`（`MediaActionDrawable.ICON_FILE` 原来画不出来）。

## 11.4.2-ui3

这一版修的是外部宿主接入测试（`samples/host-app`，只通过 mavenLocal 引用 AAR）发现的问题，有两处**不兼容改动**，升级前请看“迁移”。

### 迁移（宿主需要改代码）
- **R 类换包**：库 namespace 从 `org.telegram.messenger` 改为独立的 `org.telegram.tgui`。库资源一律用
  `import org.telegram.tgui.R;`（原来是 `import org.telegram.messenger.R;`），可以全局替换：
  `grep -rl 'import org.telegram.messenger.R;' src | xargs sed -i '' 's/import org\.telegram\.messenger\.R;/import org.telegram.tgui.R;/'`（GNU sed 去掉 `''`）。
  好处：namespace 用 `org.telegram.messenger` 的宿主（Telegram 各分支）不再合并失败，也不用再开 `nonTransitiveRClass=false`。
  库的 `BuildConfig` 也随之变成 `org.telegram.tgui.BuildConfig`（一般宿主用不到）。
- **RecyclerView fork 换包**：从 `androidx.recyclerview.widget` 搬到 `org.telegram.tgui.recyclerview`。宿主代码里和库组件一起用的
  `RecyclerView`、`LinearLayoutManager`、`DiffUtil`、`ItemTouchHelper` 等要改导入：
  `sed -i '' 's/androidx\.recyclerview\.widget/org.telegram.tgui.recyclerview/g'`。
  好处：可以和官方 RecyclerView 共存，Material、preference、viewpager2 等直接引入即可，**之前加的 `exclude(group = "androidx.recyclerview")` 可以删掉**。
  注意别把官方 `androidx.recyclerview.widget.*` 的类传给库组件（类型不同）。
- `FilterTabsViewDelegate` 除 `onPageSelected` 外都改成了 default 方法；已有实现照常编译，可以删掉空实现。

### 修复
- `ChatActivityEnterViewDelegate.hasScheduledMessages()` 默认值改为 `false`：只实现 `onSendText` 的宿主发完第一条消息后，不再冒出一个空的“定时消息”按钮。其它 default 方法都检查过，默认都不显示额外内容。
- `TelegramActivity.onCreate` 不再把根页面的 `createView()` 跑两次（`addFragmentToStack` 已经建过视图，之后的 `rebuildFragments` 是多余的）。注意主题 / 语言 / 屏幕配置变化时 `createView()` 仍可能再跑，数据初始化放 `onFragmentCreate()`。

### 其它
- CI 在构建库和 demo 之后，先 `publishToMavenLocal` 再构建 `samples/host-app`（debug + R8 release，且依赖 Material），只在 AAR 上出现的问题也能拦住。
- README 补充：SDK 路径要求、APK 体积与裁剪、聊天页必须登记主题颜色才会跟着日夜切换、搭聊天页的必做步骤、文件夹标签的挂法、`DialogItem` / `TextBubbleMessage` 字段表。

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
