# telegram-ui

把 Telegram Android 官方客户端（经典 UI）的原生 UI 组件抽成独立 Android Library（AAR），去掉账号、消息、网络等业务依赖，供其他项目直接复用。

- 基线：[DrKLO/Telegram](https://github.com/DrKLO/Telegram) `release-11.4.2-5469`（不追上游）
- 包名保持上游原样（`org.telegram.ui.*`、`org.telegram.messenger.*`），库 namespace 是独立的 `org.telegram.tgui`（不和 Telegram 及其分支的 `org.telegram.messenger` 冲突），库资源的 R 类是 `org.telegram.tgui.R`，上游代码搬过来时把 `import org.telegram.messenger.R;` 换成 `import org.telegram.tgui.R;`
- 第一阶段：纯 Java 组件；第二阶段接入 Lottie（上游 rlottie 原生库，CMake 构建出 `libtguilottie.so`，含 armeabi-v7a / arm64-v8a / x86 / x86_64）
- 版本变更见 [CHANGELOG.md](CHANGELOG.md)

## 目录

```
tgui/        库模块 → tgui/build/outputs/aar/tgui-release.aar
  src/main/cpp/  rlottie + lottie.cpp（从上游 jni/ 搬来，只保留 Lottie 部分）
demo/        演示应用（org.telegram.uidemo）
samples/host-app/  独立宿主工程（不在根构建里，只通过 mavenLocal 引用 AAR），接入问题见其中 INTEGRATION-NOTES.md
tools/       抽取/裁剪用的脚本（见下文）
upstream/    上游源码浅克隆，只读参考，已在 .gitignore 中
```

## 构建

需要 JDK 17 或 21（AGP 9.0.1 / Gradle 9.1.0，compileSdk 36，minSdk 21），以及 NDK `28.2.13676358` 和 CMake `3.22.1`（编 rlottie）。Android SDK 路径要通过 `ANDROID_HOME` 环境变量或仓库根目录的 `local.properties`（`sdk.dir=...`）给出，新检出 / 新 worktree 里没有它会报 `SDK location not found`：

```bash
sdkmanager "ndk;28.2.13676358" "cmake;3.22.1"
```

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :tgui:assembleRelease
```

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :demo:installDebug
```

发布到本地 Maven（会带上 androidx 传递依赖，比直接拷 AAR 省事）：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :tgui:publishToMavenLocal
```

坐标 `org.telegram.ui:tgui:11.4.2-ui3`。直接引用 AAR 文件时，宿主需要自己加 `androidx.core:core`、`androidx.annotation:annotation`、`androidx.dynamicanimation:dynamicanimation`、`androidx.customview:customview`、`androidx.collection:collection`、`androidx.interpolator:interpolator`、`androidx.viewpager:viewpager`。RecyclerView 用的是上游自带的 fork，包名改成了 `org.telegram.tgui.recyclerview`（源码在库里），可以和官方 `androidx.recyclerview` 共存，宿主照常引入 Material、preference、viewpager2 等即可。库的列表组件（`RecyclerListView` 等）用的是 fork，宿主代码里的 `RecyclerView` / `LinearLayoutManager` / `DiffUtil` 要从 `org.telegram.tgui.recyclerview` 导入，和库组件混用官方类会类型不匹配。

体积参考（`samples/host-app` 实测，最小宿主 + R8）：release APK 约 10.9 MB，基本都来自 tgui；加 `isShrinkResources = true` 约 9.8 MB，再只留 `abiFilters += "arm64-v8a"` 约 8.6 MB。大头是 `assets/emoji` 精灵图 5.2 MB、`res` 1.3 MB（含 Lottie 插图）、字体 0.8 MB、四个 ABI 的 `libtguilottie.so` 合计 1.45 MB。

## 使用

```java
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        TelegramUI.init(this);
    }
    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        TelegramUI.onConfigurationChanged(c);
    }
}

public class MainActivity extends TelegramActivity {   // 替代上游 LaunchActivity 的宿主
    @Override protected BaseFragment createRootFragment() {
        return new MyFragment();
    }
}
```

Manifest 里给 Activity 加上
`android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|uiMode|density"`、
`android:windowSoftInputMode="adjustResize"`，主题用库里的 `@style/Theme.TMessages`。

页面写法和上游完全一样：继承 `BaseFragment`，在 `createView()` 里配置 `actionBar` 并返回 `fragmentView`；跳转用 `presentFragment()`，左滑返回、预览、`showAsSheet()` 都可用；`getThemeDescriptions()` 里登记的颜色会在切换主题时做动画。完整示例见 `demo/src/main/java/org/telegram/uidemo/`。

注意 `createView()` 不是只调一次：切换主题（部分情况）、语言或屏幕配置变化时，`ActionBarLayout` 会重建页面视图，`createView()` 会再次执行。和上游约定一样，数据和监听的初始化放在 `onFragmentCreate()`，`createView()` 里只建视图、绑定已有数据。

主题切换只刷新在 `getThemeDescriptions()` 里登记过的视图，**没登记的页面不会跟着日夜切换**（要退出再进才变）。聊天页至少要登记 ActionBar 的背景 / 标题 / 按钮颜色、`ChatActivityEnterView.getThemeDescriptions()`，以及一个在聊天颜色 key（`chat_inBubble`、`chat_outBubble`、`chat_messageTextIn` 等）变化时重绘列表的回调；壁纸要监听 `NotificationCenter.didSetNewWallpapper` 后重新 `setBackgroundImage(...)`。写法见 demo 的 `DemoChatFragment` 或 `samples/host-app` 的 `ChatFragment`。

搭聊天页（`ChatInputLayout` + `ChatActivityEnterView` + `TextBubbleCell`）目前要照 demo 自己拼，几个容易漏的步骤：
- `actionBar.setAddToContainer(false)`，再 `contentView.addView(actionBar)` + `contentView.setActionBar(actionBar)`；
- `new ChatInputLayout(context, parentLayout)` → `setContentView(listView)` → 加 `enterView` 后 `setEnterView(enterView)`，最后加 ActionBar；
- 列表顶部 padding 自己算：`ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight + dp(4)`；
- `onResume` / `onPause` 转发给 `contentView` 和 `enterView`，`onFragmentDestroy` 里调 `enterView.onDestroy()`；
- `ChatActivityEnterViewDelegate` 全是 default 方法，默认值都是“不显示额外东西”，只实现 `onSendText` 就能发消息；
- 多选的 ActionMode 由宿主搭（`samples/host-app/.../ChatFragment.java` 里有约 60 行的最小版）。

文件夹标签 `FilterTabsView`：`FilterTabsViewDelegate` 只有 `onPageSelected` 必须实现，其余都是 default（不显示计数、没有长按菜单）。顺序是先 `setDelegate`，再 `addTab(...)`（`addTab` 会通过 delegate 取计数），然后 `finishAddingTabs(false)`；挂到 ActionBar 下沿用 `actionBar.setExtraHeight(dp(44))` + `actionBar.addView(tabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.BOTTOM))`。

对话搜索 `DialogsSearchController`：在列表、文件夹标签和写消息按钮都建好之后
`controller = new DialogsSearchController(actionBar, contentView, listOrPager, filterTabsView, floatingButton)`（后两个可为 null），
再 `controller.createSearchItem(menu)` 加放大镜（想让它在 ⋮ 左边就先加它）。数据全由宿主给：
- `controller.getSearchView().setDelegate(...)`：`onSearch(query, callback)` 里查自己的数据，任意时刻在 UI 线程 `callback.onResults(results)`（过期查询的结果会被丢弃）；`DialogsSearchView.Results` 有 `chats`、`globalChats`、`messages` 三个 `DialogItem` 列表，分别显示在 “Chats” / “Global search” / “Messages” 分组下；聊天行用 `DialogItem.status` 做第二行（`statusOnline` 或 `online` 时为蓝色），可以直接复用对话列表的条目（高亮不会改动条目）；消息行是 `DialogCell`，`title` 为聊天名、`message` 为消息原文，没设 `highlightedWords` 的会自动高亮查询词并把匹配处截到可见范围；
- `onChatClick` / `onMessageClick` 打开聊天，`onClearRecent`（上游会先弹“Clear search history”确认框）、`onRemoveHint` 可选；
- `getSearchView().setRecent(hints, recent)`：空查询时显示的“People”头像横排和 “Recent” 列表；有最近记录时搜索栏按上游变成白底（从放大镜处圆形展开），宿主在 `isLightStatusBar()` 里先问 `controller.isLightStatusBar()`（非 null 时用它）；
- `Listener.onSearchingChanged` 里关掉抽屉滑动等手势，`canToggleSearch` 可禁止打开；`getThemeDescriptions()` 加进 fragment 的主题描述。返回键 / 左上箭头关闭搜索由 `ActionBarLayout` 处理。

数据模型都是公开字段：
- `DialogItem`：`id`（稳定 id，决定头像配色）、`title`、`message`、`senderName`、`mediaPreview`、`draft`、`typing` / `typingType`、`time`、`unreadCount`、`markUnread`、`mention`、`muted`、`pinned`、`verified`、`secret`、`online`、`sentState`（`SENT_*`）、`avatar` / `avatarBitmap` / `avatarType`、`isFolder`（归档行）；搜索用：`status`（`ProfileSearchCell` 第二行）、`statusOnline`、`highlightedWords`（标题和消息里高亮的词）。
- `TextBubbleMessage`：`id`、`type`（`TYPE_TEXT` / `TYPE_VOICE` / `TYPE_PHOTO` / `TYPE_DOCUMENT`，宿主按它选单元格）、`text`（媒体消息是说明文字）、`isOut`、`time`、`state`（`STATE_*` 发送状态）、`reactions`（`TextBubbleMessage.Reaction` 列表：`emoji`、`count`、`chosen`，按列表顺序显示；`toggleReaction(emoji)` 按上游非 Premium 规则切换“我的”反应：再点取消，换一个则替换原来的）；媒体：`mediaState`（`MEDIA_READY` / `MEDIA_DOWNLOAD` / `MEDIA_LOADING`）+ `mediaProgress`，语音 `duration`（秒）/ `waveform`（上游 5-bit 打包，录音器给的就是这个格式）/ `playing` / `audioProgress` / `audioProgressSec` / `contentUnread`，图片 `photo`（Drawable）/ `photoWidth` / `photoHeight`，文件 `fileName` / `fileSize`。用 `TextBubbleMessage.voice(...)` / `photo(...)` / `document(...)` 创建。

库里的图标、字符串等资源都通过 `org.telegram.tgui.R` 引用（AGP 8+ 默认 `nonTransitiveRClass=true`，宿主自己的 `R` 不包含库资源）：

```java
import org.telegram.tgui.R;

showDialog(new AlertDialog.Builder(getParentActivity())
        .setTitle("Title").setMessage("Message")
        .setPositiveButton("OK", null).setNegativeButton("Cancel", null)
        .create());

BottomSheet.Builder b = new BottomSheet.Builder(getParentActivity());
b.setItems(new CharSequence[]{"Copy", "Share"}, new int[]{R.drawable.msg_copy, R.drawable.msg_share}, (d, which) -> {});
showDialog(b.create());

TelegramUI.toggleDayNight(anchorView);                  // 上游抽屉里太阳/月亮按钮的圆形揭露切换
TelegramUI.applyTheme(Theme.getTheme("Arctic Blue"), anchorView);
```

## 包含的组件

| 类别 | 类 |
| --- | --- |
| 导航 | `BaseFragment`、`ActionBarLayout` / `INavigationLayout`、`DrawerLayoutContainer`、`AdjustPanLayoutHelper`、`EmptyBaseFragment` |
| ActionBar | `ActionBar`、`ActionBarMenu`、`ActionBarMenuItem`（含搜索框）、`ActionBarMenuSubItem`、`ActionBarPopupWindow`、`ActionBarMenuSlider`、`BackDrawable`、`MenuDrawable`、`SimpleTextView`、`FloatingToolbar` / `FloatingActionMode` |
| 弹窗 | `AlertDialog`（`AlertDialogDecor`、`DarkAlertDialog`）、`BottomSheet` |
| 设置项 | `HeaderCell`、`TextCell`、`TextSettingsCell`、`TextCheckCell`、`TextDetailSettingsCell`、`TextInfoPrivacyCell`、`ShadowSectionCell`、`NotificationsCheckCell`、`RadioCell`、`TextRadioCell`、`CheckBoxCell`、`DividerCell`、`EmptyCell` |
| 列表与控件 | `RecyclerListView` + 上游 RecyclerView fork、`Switch`、`CheckBox2`、`CheckBoxSquare`、`RadioButton`、`EditTextBoldCursor`、`AnimatedTextView`、`LinkSpanDrawable`、`RadialProgressView`、`LineProgressView`、`EmptyTextProgressView`、`SizeNotifierFrameLayout`、`LayoutHelper`、`CubicBezierInterpolator` 等 |
| 表情 | `Emoji` + `EmojiData` + `CompoundEmoji`：上游原版，`assets/emoji/` 里的 3586 张 Telegram 表情精灵图（实际约 5.2 MB，`du` 按磁盘块算会显示 14 MB；APK 里不再压缩）按页异步加载，`Emoji.replaceEmoji(...)` 把文字里的 emoji 换成 `Emoji.EmojiSpan`；输入栏、回复 / 编辑顶栏、气泡、`CheckBoxCell` / `TextDetailSettingsCell` 等上游已调用的地方都会显示。精灵图加载完会发 `NotificationCenter.emojiLoaded`，宿主列表要像上游 ChatActivity 那样收到后重绘（`Emoji.invalidateAll(listView)`）；`SharedConfig.useSystemEmoji = true` 可改回系统 emoji |
| 聊天 | `TextBubbleCell` + `TextBubbleMessage`（纯文本气泡：时间、发送中/已发送/已读勾、同侧连续消息合并圆角与尾巴、Telegram 表情；只有 emoji 的消息按上游 TYPE_EMOJIS 放大显示、不画气泡，时间放在小圆角底条上）；媒体气泡（上游 `ChatMessageCell` 对应分支按原数值移植，同样继承 `TextBubbleCell` 的背景、时间、勾和多选）：`VoiceBubbleCell`（语音：`RadialProgress2` 播放 / 暂停按钮、`SeekBarWaveform` 波形可拖动、时长 / 已播放时间、未听圆点；播放由宿主做，宿主更新 `playing` / `audioProgress` / `audioProgressSec` 后调 `updatePlayingMessageProgress()`）、`PhotoBubbleCell`（图片：上游 `getMessageSize` 尺寸规则、同侧连续消息的分组圆角、图片上的时间小胶囊、可选说明文字、可选 `chat_mediaLoaderPhoto` 下载 / 取消 / 进度圆圈；图片是宿主给的 `Drawable`，不联网）、`DocumentBubbleCell`（文件：44dp 圆形 `ICON_FILE` / 下载 / 取消按钮、两行文件名中间省略、“大小 扩展名”、下载中“已下载 / 总大小”、⋮ 图标）；按钮点击和波形拖动通过 `TextBubbleCell.setMediaDelegate(MediaDelegate)` 回调宿主，气泡其它位置照常单击出菜单、长按多选；`TextBubbleCell.addMediaThemeDescriptions(...)` 登记媒体气泡、回复引用、链接和日期分隔用到的颜色 key；**回复引用**：`TextBubbleMessage.replyTo`（`ReplyTo`：名字、一行预览、可选缩略图、原消息 id，`ReplyTo.of(message, name)` 一步生成）在文字 / 语音 / 图片 / 文件气泡顶部画上游 `ChatMessageCell` 的回复块（`ReplyMessageLine` 色条与浅色圆角底、按下水波和回弹、名字用回复色、预览走 Telegram 表情、图片回复带 4dp 圆角缩略图），点按回调 `TextBubbleCell.Delegate.onReplyPressed`，`setHighlightedAnimated()` 播放上游跳转后的高亮闪烁（`highlightProgress` + `chat_selectedBackground`）；**链接**：文字气泡里的网址、@提及、#话题按上游 `MessageObject.addLinks` 识别（可用 `TextBubbleCell.detectLinks` 关掉），用 `chat_messageLinkIn/Out` 着色，按下画上游 `LinkSpanDrawable` / `LinkPath` 高亮，单击 / 长按回调 `Delegate.onLinkPressed` / `onLinkLongPressed`（demo 单击打开网址，长按弹上游的“打开 / 复制”底部菜单）；**气泡里的反应**：`ReactionsLayoutInBubble`（上游同名类移植到纯模型）把 `TextBubbleMessage.reactions` 画成上游的 26dp 反应胶囊——20dp 静态 Telegram 表情 + `CounterView.CounterDrawable` 计数，`chat_in/outReactionButton*` 配色（未选 15.6% 透明底、已选实心），按上游 `measure` 换行排布、4dp 间距，时间放在最后一行末尾放不下就换行；文字 / 语音 / 文件气泡画在气泡内底部，图片画在图片下方的气泡条里（有反应时时间从图片上移到条里，和上游 `shouldDrawTimeOnMedia` 一致），纯 emoji 消息画在表情下方、用壁纸取色的服务底（出站右对齐）；气泡高度随之增加。单击胶囊回调 `Delegate.onReactionPressed`，长按回调 `onReactionLongPressed`（按下有 `ButtonBounce` 回弹，手势不再触发整行单击 / 菜单）；改了 `reactions` 后对同一条消息再 `setMessage`（列表里就是 `notifyItemChanged`）会播放上游的变化动画：新增胶囊从 0.5 倍放大淡入、移除的缩小淡出、其余平移 / 变宽 / 颜色渐变、计数数字上下滚动（250ms，`CubicBezierInterpolator.DEFAULT`）；**日期分隔**：`ChatActionCell`（上游 `ChatActionCell` 的居中服务消息胶囊，`setCustomDate(unix 秒)` 显示 Today / Yesterday / 日期，背景是 `Theme.chat_actionBackgroundPaint`：动态渐变壁纸时取壁纸位图做 shader，其它壁纸取平均色，日夜都跟着壁纸变；`setFloating(true)` 用作滚动时的悬浮日期），`TextBubbleMessage.date` 记消息时间，分隔行由宿主 adapter 插入（见 demo）、`Theme.MessageDrawable`（含渐变/动态渐变出站气泡）、`ChatInputBar`（已弃用的简易输入框，保留作轻量选项）、`ChatBubbleItemAnimator`（新消息从输入栏下方滑入、上方气泡同步上移）、`ChatSounds`（上游的发送 / 接收提示音 `sound_out` / `sound_in`，静音模式下不播放）、聊天背景 `MotionBackgroundDrawable` / `BackgroundGradientDrawable`（由 `SizeNotifierFrameLayout.setBackgroundImage(Theme.getCachedWallpaper(), …)` 绘制，发送时 `switchToNextPosition()` 旋转） |
| 聊天输入栏 | `ChatActivityEnterView`：上游 11.4.2 原版移植，保留原布局、图标和动画——`EditTextCaption`（多行增长、选中文字的格式菜单：引用 / 剧透 / 粗体 / 斜体 / 等宽 / 删除线 / 下划线 / 链接 / 清除格式）、表情↔键盘图标动画（`ChatActivityEnterViewAnimatedIconView`，Lottie）与表情面板（见下行）、附件按钮、`SendButton` 及出现动画、长按发送菜单（定时发送 / 上线时发送 / 静音发送，日期选择器 `AlertsCreator.createScheduleDatePickerDialog`）、编辑模式的完成按钮、频道静音铃铛、慢速模式倒计时；语音 / 圆形视频录制整套界面：`RecordCircle`（Blob/Wave 波纹）、`ControlsView`（上滑锁定、暂停、阅后即焚开关）、`SlideTextView`（左滑取消）、`RecordDot`、`TimerView`、录音预览（`SeekBarWaveform` 波形、播放 / 删除 / 继续录）。宿主可以用 `setRecordButtonVisible(false)` 隐藏语音 / 圆形视频录制按钮（没有文字时那一格留空，有文字时发送按钮照常带动画出现）。另有 `ChatInputLayout`（ChatActivity 根布局里与输入栏相关的测量 / 摆放：表情面板、录音圆圈、键盘高度）、`ChatInputTopPanel`（回复 / 编辑 / 转发 / 链接预览顶栏，即上游 ChatActivity 的 replyLayout） |
| 表情面板 | `EmojiView`：上游表情页——Telegram 表情网格（最近使用 + 8 个分类，分类标题 `StickerSetNameCell`）、`EmojiTabsStrip` 分类标签（最近使用 + 8 个分类铺满整行，保留 Lottie 图标动画和选中指示；上游可收起的分类胶囊只在后面跟着表情包标签时才有意义，tgui 没有表情包所以不用；点标签滚动、滚动时选中标签跟随）、滚动时标签栏和底栏收起、长按带肤色的表情弹出 `EmojiColorPickerWindow`（肤色和最近使用由 `Emoji` 保存）、底栏退格（长按连删）；面板高度沿用输入栏记下的键盘高度（`kbd_height`；第一次弹出键盘前，上游固定用 200dp，tgui 改为按屏幕长边的 38% 估算）。面板弹出 / 收起时，`ChatInputLayout` 像上游 ChatActivity 的 `bottomPanelTranslationYChanged` 那样让输入栏和聊天列表跟着面板一起滑动；键盘弹出 / 收起用 tgui 自己的实现（上游 `AdjustPanLayoutHelper` 每帧改整条视图链高度并重绘壁纸，在 Mate 9 上掉帧严重）：点输入框时先按记下的键盘高度把输入栏和聊天列表抬起，窗口真正 resize 时保持屏幕位置、在抬起动画剩下的时间里到位，只改这两个 view 的 `translationY`；动画期间输入栏下方用表情面板底色填充，看起来是键盘区域升起，而不是输入栏悬在半空（以上是 Android 10 及以下）。Android 11+ 用 `WindowInsetsAnimationCompat` 让输入栏和聊天列表逐帧贴着输入法顶边走，表情面板和键盘互换时输入栏保持不动；`DrawerLayoutContainer` 在输入法动画期间先按较大的可用区域布局，结束时再应用新的 inset，避免跟随中的输入栏被裁掉。宿主需要 `androidx.viewpager:viewpager`（发布的 POM 已带上） |
| 对话列表 | `DialogCell` + `DialogItem`（上游 `DialogCell` 的 `buildLayout` / `onDraw` 按原数值移植到纯模型：名字 + 时间、两行 / 三行预览（`SharedConfig.useThreeLinesLayout`）、发送者前缀、草稿、媒体预览配色、正在输入行（6 种状态图标，与消息行交叉淡入）、未读计数（数字变化动画）、@ 提及、置顶图标与置顶底色、静音、认证、私密聊天锁、发送中 / 已发送 / 已读勾（切换动画）、发送失败、在线圆点、分隔线；收到 `emojiLoaded` 自己重绘）、`AvatarDrawable`（上游原版：首字母 + 上游配色，收藏夹 / 归档（Lottie）/ 回复等特殊头像）、`DialogsListView`（列表 + 上游 `DialogsItemAnimator`：删除折叠、移动、新增；`setArchivePullDelegate` 开启上游隐藏归档：`SharedConfig.archiveHidden` 时归档行藏在列表上方，下拉出现 `PullForegroundDrawable` 提示条，超过 0.85 行高松手后收成归档头像，滚走后自动再藏起，惯性滑动停在第一个对话）、`DialogsViewPager`（上游 `DialogsActivity` 的两个 ViewPage + 左右拖动翻文件夹，`FilterTabsView.selectTabWithId(id, progress)` 跟随指示条，点标签也滑动切换；宿主在 `onBindPage` 里为文件夹填列表）、`DialogsSwipeController`（上游左滑：归档滑出；归档行左滑“隐藏 / 置顶”归档；置顶 / 已读 / 静音 / 删除回弹并回调，阈值 0.45、Lottie 图标、圆形揭露底色；多选时拖动置顶行排序）、`DialogsActionMode`（长按多选：计数 `NumberTextView`、54dp 图标纵向弹出、ActionBar 渐变成白底、返回键转成 ×、⋮ 溢出菜单；头像上的 `CheckBox2` 勾选）、`FilterTabsView`（上游文件夹标签原版：指示条动画、计数、编辑拖动）、`DialogsFloatingButton`（写消息圆形按钮，下滑隐藏 / 上拉出现）、`DialogsSearchController` + `DialogsSearchView`（上游对话搜索：放大镜展开搜索框，文件夹标签淡出收起、写消息按钮隐藏，列表缩到 0.9 淡出、搜索页从 1.05 淡入，有最近记录时白色搜索栏从放大镜处圆形展开；空查询显示 `HintDialogCell` 头像横排和带 “Clear” 的 Recent 分组，有查询显示 `GraySectionCell` 分组下的 `ProfileSearchCell`（名字高亮）和消息结果 `DialogCell`（匹配文字高亮），没有结果显示上游样式的空状态；结果由宿主异步回调）、`DrawerMenuView` + `DrawerProfileCell` + `DrawerActionCell`（侧边抽屉：头像、名字、电话、日 / 夜切换太阳月亮 Lottie → `TelegramUI.toggleDayNight`，菜单项由宿主提供）。用法见 demo 的 `DemoDialogsFragment` |
| 消息菜单与多选 | `MessageContextMenu`：上游 `ChatActivity.createMenu()` 的消息菜单按原数值移植到纯模型——全屏 0.2 黑色遮罩（`dimBehindView`，EASE_OUT_QUINT 320ms），被长按的气泡画在遮罩之上（裁到列表顶部以下）；`ActionBarPopupWindow` + `ActionBarMenuSubItem` 行（`popup_fixed_alert` 背景、200dp 最小宽度、220ms 收起），位置按上游算法以触点为锚、左右 / 上下夹在屏幕内；上方是 `ReactionsContainerLayout` 样式的反应条（药丸 + 阴影 + 两个小气泡圆点，250ms `OvershootInterpolator(0.5)` 从右向左展开，菜单随之放大淡入，表情按上游延迟依次弹出），表情是 `Emoji` 绘制的静态 Telegram 表情（默认 👍 👎 ❤ 🔥 🥰 👏）。用法：`new MessageContextMenu(context, resourcesProvider).addItem(icon, text, [destructive,] onClick)…setReactions(…).setDelegate(…).show(cell, touchX, touchY)`；颜色每次打开时按当前主题取，日夜都跟随。`setSelectedReactions(message)`（或传 emoji 集合）把已选的反应在反应条里画成上游的样子（`key_listSelector` 圆底、表情缩到 0.76）。和上游 ChatActivity 一样：**单击**消息打开这个菜单，**长按**进入多选；消息列表要 `setInstantClick(true)` + `setDisableHighlightState(true)`（不显示列表点按水波）。多选的单元格部分在 `TextBubbleCell.setCheckBoxVisible / setChecked`（上游 `ChatMessageCell`：21dp `CheckBoxBase` 从左侧滑入、来信气泡右移 35dp、200ms EASE_OUT / EASE_IN，选中时整行 `chat_selectedBackground` 高亮、气泡换成 Selected 颜色、时间用 `*TimeSelectedText`）；ActionMode 本身由宿主搭（demo 的 `DemoChatFragment` 照搬 `createActionMode` / `createBottomMessagesActionButtons`：数量 `NumberTextView`、编辑（单选自己的消息时）/ 复制 / 转发 / 删除图标及 250ms 纵向弹出、返回键变 ×、输入栏换成底部“回复 / 转发”栏，单击切换选中、长按后拖动连选 `RecyclerListView.startMultiselect`）。demo 菜单：回复、复制、编辑（仅自己的消息）、删除（带列表动画）、点反应条里的表情给消息加 / 换 / 取消反应（`toggleReaction` + `notifyItemChanged`，气泡里的胶囊带动画变化）；气泡里的胶囊单击同样切换，长按弹出计数提示 |
| 主题 | `Theme`（内置 Classic / Dark / Arctic / Day / Night，从上游 `.attheme` 资源加载；强调色、自动夜间模式（定时 / 光线 / 跟随系统））、`ThemeDescription`、`ThemeColors`、`OKLCH` |
| 宿主 | `TelegramUI`（初始化 + 主题切换入口）、`TelegramActivity`（生命周期转发、系统栏颜色、日夜切换动画） |

## 剥离与 stub

业务类用最小 stub 代替，或直接删掉调用点：

| 上游 | 处理方式 |
| --- | --- |
| `ApplicationLoader` | 只保留 context / handler，由 `TelegramUI.init()` 初始化 |
| `LocaleController` | 直接读 Android 字符串资源（不再下载语言包），复数用 ICU 规则；`isRTL` 跟随系统语言 |
| `SharedConfig`、`MessagesController`、`UserConfig`、`LiteMode`、`BuildVars`、`FileLog` | 只留 UI 用到的开关和偏好设置；单账号；日志写到 logcat（tag `tgui`） |
| `Utilities` | 去掉 native 方法和加密相关代码，stack blur 改用纯 Java 实现；`generateGradient`（四色动态渐变）和 `drawDitheredGradient`（双色抖动渐变）从 `jni/image.cpp` 移植成 Java（只在 60×80 小位图 / 后台线程上跑，不进 native 库） |
| 聊天壁纸 | `Theme.reloadWallpaper` 只按主题的 `chat_wallpaper` / `chat_wallpaper_gradient_to1..3` 颜色生成纯色、双色渐变或四色动态渐变；默认 Blue 主题用上游默认绿色动态渐变 + `default_pattern` 图案；强调色带图案的（上游按 slug 从服务器下载图案）统一用 `default_pattern` 代替，深色主题按上游用负强度（黑底透出渐变色图案）；因为代用图案线条比服务器图案细，深色下图案按满强度绘制，并用 `MotionBackgroundDrawable.setDarkBaseGradientAlpha(0.6f)` 在黑底下透出 60% 的渐变（上游是纯黑底）（`SvgHelper` 只保留渲染成 Bitmap 的部分，约 1.4k 行，`res/raw/default_pattern.svg` 约 500 KB） |
| `DialogCell`（5689 行） | 按上游绘制代码重写成模型驱动（约 2100 行），数据层 `TLRPC.Dialog` / `MessageObject` / `MessagesController` / 草稿 / 正在输入换成 `DialogItem`；去掉 Stories 头像圈、论坛话题、Premium / 表情状态、机器人、广告、文件夹标签、缩略图、剧透、按钮行 |
| `DialogsActivity` 的列表部分 | 只移植 `SwipeController`（→ `DialogsSwipeController`）、`DialogsRecyclerView` 的松手判断和隐藏归档的下拉 / 回弹 / 布局管理器（→ `DialogsListView`）、`ContentView` 的左右翻页手势（→ `DialogsViewPager`）、ActionMode 创建 / 显示 / 隐藏（→ `DialogsActionMode`）、写消息按钮（→ `DialogsFloatingButton`）；滑动动作、多选操作和数据都交给宿主回调 |
| `DialogsActivity` 的搜索 / `SearchViewPager` / `DialogsSearchAdapter` | `searchItem` 监听、`showSearch`、`setSearchAnimationProgress` 按原数值移植到 `DialogsSearchController`（白色搜索栏的圆形展开由 `ActionBar.setSearchRevealProgress` 画，原来画在 `ContentView` 里；文件夹标签收起用 `setExtraHeight` 逐帧缩小）；只保留对话页：Recent（含 People 横排）/ Chats / Global search / Messages 四种分组，去掉媒体 / 链接 / 文件 / 音乐 / 语音 / 下载标签页和筛选标签、话题、频道 / 机器人页、话题标签搜索、公开帖子、按手机号搜索、“显示更多”折叠、加载骨架；`ProfileSearchCell`、`HintDialogCell` 换成 `DialogItem` 驱动（去掉表情状态、Premium 锁、机器人“打开”按钮），`GraySectionCell` 用普通 `TextView`；空状态的上游放大镜鸭子是服务器贴纸，换成自带的 `utyan_empty` |
| `DrawerProfileCell` / `DrawerLayoutAdapter` | 按上游布局重写（→ `DrawerProfileCell`、`DrawerMenuView`）；去掉表情状态与反应特效、Premium 渐变 / 星星、雪花、多账号列表、附件菜单机器人 |
| `FilterTabsView` | 上游原版，去掉文件夹顺序上传服务器和 Premium 排序检查（默认标签固定在第一位） |
| `AvatarDrawable` | 上游原版，去掉 `TLRPC.User` / `Chat` 入口和服务器 peer color，用 `setInfo(id, firstName, lastName)` |
| `ChatMessageCell` / `MessageObject` | 不整体移植，换成上面的轻量 `TextBubbleCell` / `TextBubbleMessage`；回复块（`replyNameLayout` / `replyTextLayout` / `drawNamesLayout` 的 hasReply 段 / `checkReplyTouchEvent`）、链接（`MessageObject.addLinks` + `checkTextBlockMotionEvent`）照上游数值移植进 `TextBubbleCell`；`ReplyMessageLine` 只保留单色条和底色（颜色来自 `ReplyTo.colorId`，不读服务器 peer color，没有双色 / 三色条纹、背景表情和加载动画）；`ChatActionCell` 只保留文字胶囊（礼物、头像、按钮、剧透都去掉）；`Theme.applyChatServiceMessageColor` 移植了服务消息颜色部分，`BitmapDrawable` 壁纸不做模糊 shader，用平均色；语音 / 图片 / 文件气泡按上游对应分支（`createDocumentLayout`、`setMessageContent`、`onLayout`、`drawContent`、`getIconForCurrentState`…）移植到 `VoiceBubbleCell` / `PhotoBubbleCell` / `DocumentBubbleCell`，每个类的头注释写明来源方法 |
| `RadialProgress2` | 上游原版，去掉 `ImageReceiver` 覆盖图（音乐封面用的 `setImageOverlay`） |
| `ChatActivityEnterView` 的业务边界 | 构造参数从 `ChatActivity` 换成 `BaseFragment`；`ChatActivityEnterViewDelegate` 全部改成 default 方法，并新增宿主回调：`onSendText(text, entities, notify, scheduleDate)`、`onSendVoice(RecordedAudio, …)`、`onEditDone`、`onSendContent`（键盘发来的 GIF / 贴纸）、`onImagePasted`、`onSilentChanged`、`onRecordTypingStatus`、`onTopViewTranslationChanged`、`canScheduleMessage` / `isInScheduleMode` / `isSelfChat` / `canSendWhenOnline`、`roundVideoNeedsCameraPermission`、`onEmojiPanelCreated`；聊天权限 / 频道 / 圆形视频 / 慢速模式用 `setChatPermissions()`、`setChannel()`、`setRoundVideoEnabled()`、`setSlowModeTimer()` 设置；回复目标是宿主的任意对象（`setReplyingMessageObject(Object)`），编辑用 `EditingMessage(id, text, entities, caption)`。删掉的业务分支：机器人键盘 / 命令菜单 / WebView 按钮、以…身份发送、礼物按钮、生日提示、Premium / 动态表情检查、快捷回复与商务链接、网页预览、`MessageSendPreview`（长按发送的消息预览，改用上游自带的弹出菜单）、草稿同步 |
| `MediaController` | 只保留语音消息录制 / 预览接口，交给可替换的 `MediaController.Recorder`；默认实现 `DefaultRecorder` 用 `MediaRecorder`（AAC .m4a，按 `getMaxAmplitude()` 出波纹和 5-bit 波形，打包格式与上游 native `set_bits` 相同）+ `MediaPlayer` 试听。宿主可换成自己的 Opus 编码器；录制状态仍通过上游的 `NotificationCenter` 事件（`recordStarted` / `recordProgressChanged` / `recordStopped` / `audioDidSent`…）回到输入栏，新增 `voiceRecordedForSend` 表示松手直接发送。圆形视频的相机（上游 `InstantCameraView`）由宿主在 `needStartRecordVideo` 里实现 |
| `TLRPC` / `MediaDataController` | `TLRPC` 只剩消息实体（粗体、链接、自定义表情 id…）的纯数据类；`MediaDataController` 只保留文字实体相关的静态方法（`getEntities`、`addStyleToText`、`addTextStyleRuns`…，原样复制） |
| `EmojiView` | 只保留表情页；贴纸 / GIF 页、自定义和精选表情包、Premium 检查、表情搜索（需要服务器关键词）和长按预览（`ContentPreviewViewer`）都已剥离，输入栏会调用的对应方法保留为空实现。`EmojiTabsStrip` 去掉了表情包标签和 Premium 锁 |
| `AnimatedEmojiSpan` | 保留自定义表情 id、占一个表情的宽度，动画渲染见路线图 |
| `AlertsCreator` / `CaptionContainerView` / `VideoEditedInfo` | 分别只保留定时发送日期选择器、阅后即焚图标 `PeriodDrawable`、圆形视频裁剪字段 |
| `RLottieDrawable` / `RLottieImageView` | 上游原版，native 库换成 `libtguilottie.so`；`addParentView` 改收 `View`（无 `ImageReceiver`），`RLottieImageView` 去掉了按 `TLRPC.Document` 加载贴纸的分支。`BitmapsCache` 缓存目录改为 `cacheDir/acache` |
| `Emoji` | 上游原版；只把 `replaceWithRestrictedEmoji`（服务器的 RestrictedEmoji 动态贴纸集）换成普通 `replaceEmoji`，`useSystemEmoji` / `allowBigEmoji` 由 `SharedConfig` 的静态字段提供 |
| `AnimatedEmojiDrawable`、`AnimatedEmojiSpan` | 自定义 emoji 只保留常量和 `SwapAnimatedEmojiDrawable`，当普通 Drawable 容器用 |
| `BackupImageView` | 改成本地 Drawable/Bitmap 视图，支持圆角，不再依赖 `ImageReceiver` 和网络加载 |
| `EditTextEffects` | 去掉剧透（spoiler）、引用块和自定义 emoji 渲染 |
| `URLSpanReplacement` | 点击后用 `ACTION_VIEW` 打开链接 |
| `LaunchActivity` | 换成 `TelegramUI.Host` / `TelegramActivity` |

删掉的上游功能：完整聊天页（`createChatResources` 只保留气泡、文字、时间、勾和输入框阴影部分）、壁纸文件 / 云端壁纸 / 自定义壁纸、云端主题（`TL_theme`、主题上传）、Stories、Bot WebView 标签栏、群组通话画中画、`Bulletin` 提示条、剧透效果、ActionBar 搜索里的筛选标签、`BackButtonMenu`（长按返回的历史列表）、`TextCell` 的 sticker / 等级锁 / NEW 标记，以及 `CheckBoxCell.setUserOrChat`（换成了 `setUser(CharSequence, Drawable)`）。

## 已知限制

- 聊天气泡：有文字、语音、图片（单张）、文件四种；没有头像、名字、回复引用、转发、视频 / GIF / 圆形视频 / 音乐 / 贴纸 / 位置等其它媒体、相册（多图分组）、带缩略图的文件、文字选择、链接点击；媒体气泡下面还不画已选反应；图片没有模糊预览缩略图和剧透，语音没有转文字按钮和上游播放时的音量波纹（`AudioVisualizerDrawable`）；多选的整行高亮是简单淡入（上游 `MessageBackgroundDrawable` 从触点圆形展开，浅色主题用壁纸取色的 service 画笔）；表情反应只有静态表情：气泡里的反应没有动态 / 自定义表情、付费星星反应、最近反应者头像、未读反应标记，按模型顺序显示（上游私聊会把已选的排前面），气泡本身的高度变化不做动画（胶囊有动画）；长按菜单的反应条没有动态 / 自定义反应、展开全部反应的按钮、长按放大和飞入气泡的特效；气泡里的剧透（spoiler）实体没有粒子效果（剧透效果在第一阶段已剥离）
- 输入栏：附件按钮只回调宿主（没有 `ChatAttachAlert`）；粘贴 / 键盘提交的图片只回调宿主（没有 `PhotoViewer`）；没有 @提及 / 话题 / 表情联想列表（`MentionsContainerView`、`SuggestEmojiView`）；没有机器人键盘、命令菜单和 WebView 按钮；圆形视频需要宿主自己的相机实现；默认录音器输出 AAC 而不是 Telegram 的 Opus/OGG

- 只支持主题自带的颜色/渐变壁纸和默认图案壁纸，不支持壁纸文件和视差（motion）效果；各强调色原本的不同图案都显示成同一个 `default_pattern`
- Lottie 只带了库内引用到的 `R.raw` JSON（对话列表的归档 / 滑动动作图标、骰子 / 飞镖等），外加上游 APK 自带的 46 个大幅插画：11 只 Utya 小鸭子 `R.raw.utyan_*`、两步验证猴子 `tsv_setup_monkey_*` / `tsv_monkey_close`、设置和邮箱引导（`tsv_setup_*`、`email_*`）、钱包 `wallet_*`、`qr_login`、`filter_new` 等，以及一批白色线条图标（`passcode_lock`、`stats`、`fragment*`、`boosts` 等，上游画在彩色圆形上，可配合 `AlertDialog.Builder.setTopAnimationIsNew(true)` 使用），demo 的 "Lottie illustrations" 页分组展示全部动画。客户端里另一些鸭子（搜索无结果等空状态）和动态表情、贴纸是运行时从服务器贴纸包下载的，不在 APK 里；其它动画 JSON 由宿主放进自己的 `res/raw`，再 `setTopAnimation(R.raw.xxx, …)` / `new RLottieDrawable(R.raw.xxx, …)` 使用
- rlottie 的 pixman NEON 汇编（`pixman-arm*-neon-asm.S`）需要 GNU as，新 NDK 只有 clang 汇编器，所以所有 ABI 都走 rlottie 的 C 填充路径，纯色填充比上游略慢
- 不能播放 Telegram 贴纸 / TGS 文件的网络加载（没有 `ImageReceiver` / `FileLoader`）；本地文件可以用 `new RLottieDrawable(File, w, h, cacheOptions, limitFps)`
- 字符串只带了库内实际用到的 49 条英文，加上游自带的部分翻译（ar / de / es / it / ko / nl / pt-BR）；其它语言需要宿主覆盖资源
- demo 在 Android 9 真机（Huawei MHA-AL00）上完整验证过；Android 16（OnePlus，targetSdk 36 强制边到边）上验证过聊天背景、气泡、Telegram 表情和 `ChatActivityEnterView` 的显示，表情面板（打开、日夜切换、键盘↔面板切换、返回键收起）两台都验证过；这台 OnePlus 的手势导航条高度为 0，所以没能在非零导航栏高度下验证面板位置；输入栏的录音交互还没在 Android 15+ 真机上逐项验证；平板布局没验证
- 对话列表：隐藏归档没有 Stories 联动（上游下拉时会先展开 Stories 栏）、没有“已隐藏归档”撤销条和归档长按菜单；`archiveHidden` 只存在内存里，由宿主持久化；翻页手势与上游一样只在“滑动切换文件夹”设置下生效（demo 的 Swipe action 菜单第一项），此时普通行不能左滑，归档行仍可左滑；锁定文件夹（Premium 上限）的抖动提示未移植；搜索只有对话页（没有媒体 / 链接 / 文件等标签页、搜索筛选、长按删除单条最近记录），宿主负责最近记录的持久化；没有 Stories、论坛话题、消息缩略图、撤销条（`UndoView`）、平板右侧对话预览；多账号只保留了抽屉里的箭头（`setAccountsArrowVisible`），没有账号列表
- `Theme` 里仍留着个人资料相关的 paint/drawable 字段，没用到但可以安全忽略；后续可以继续瘦身

## 路线图（分阶段恢复，均不放弃）

1. **阶段 A（已完成）**：`ChatActivityEnterView` 原版输入栏——输入、格式菜单、表情↔键盘切换（占位面板）、发送动画与长按菜单、语音录制（按住 / 左滑取消 / 上滑锁定 / 暂停预览 / 播放）、圆形视频录制界面（相机由宿主提供）、回复 / 编辑顶栏
2. **阶段 B1（已完成）：Telegram 表情渲染**——上游 `Emoji` / `EmojiData` / `CompoundEmoji` 和全部 emoji 精灵图，输入栏、气泡（含纯 emoji 大号显示）和设置单元格里显示 Telegram 风格表情；输入栏可隐藏录音按钮
3. **阶段 B2（已完成）：表情面板**——上游 `EmojiView` 的表情页（分类标签、最近使用、肤色选择、退格），替换占位面板，跟随日夜主题切换
4. **阶段 B3**：表情搜索、动态 / 自定义表情页、贴纸 / GIF 页（与下面 C、D 合并推进）
5. **阶段 C：动态表情 / 自定义表情**——`AnimatedEmojiDrawable` / `AnimatedEmojiSpan` 的真实渲染（rlottie 已就绪），文字里的自定义表情实体
6. **阶段 D：贴纸和 GIF**——`EmojiView` 的贴纸 / GIF 页、`StickersAlert`、`TrendingStickersLayout`，数据由宿主提供（不接 Telegram 服务器）
7. **对话列表（已完成）**：`DialogCell`、滑动动作、多选、列表动画、文件夹标签、抽屉、写消息按钮；下拉显示隐藏归档（`PullForegroundDrawable`）、文件夹左右翻页（`DialogsViewPager`）也已完成；对话搜索（`DialogsSearchController`）也已完成；后续：撤销条
8. **消息菜单与多选（已完成静态版）**：`MessageContextMenu`（单击：遮罩、浮起气泡、弹出菜单、静态表情反应条），长按多选（复选框、ActionMode、底部回复 / 转发栏、拖动连选）；气泡里的反应胶囊（`ReactionsLayoutInBubble`，静态表情 + 计数动画）已完成；后续：动态反应（上游 lottie 选中 / 飞入动画、反应者头像、展开全部反应、自定义表情反应），依赖阶段 C
9. **媒体气泡（已完成第一批）**：语音、图片、文件；回复引用、链接、日期分隔条、气泡里的反应已完成；后续：视频 / 圆形视频 / 相册
10. 之后可选：`SuggestEmojiView`、提及列表、`MessageSendPreview`（长按发送的消息预览与特效）、机器人键盘

## 抽取工具（`tools/`）

继续从 `upstream/` 搬组件时会用到：

| 脚本 | 作用 |
| --- | --- |
| `pull.py class <FQCN>...` | 把上游类拷进 `tgui`（已存在的跳过） |
| `pull.py res` / `pull.py missing` | 扫描 `R.x.y` 引用，补齐缺的 drawable / string / style 等资源 |
| `deps.py` | 列出被引用但还没搬进来的上游类 |
| `build.sh` + `errs.py [Class]` | 编译库，按类汇总 javac 错误 |
| `errmembers.py Class [--prune --keep a,b]` | 找出（或删掉）有编译错误的成员 |
| `prune.py File.java name...` / `--lines` / `--list` | 按名字或行号删除类成员 |
| `restore_member.py File.java name...` | 从上游把成员恢复回来 |
| `dropbranch.py File.java Class...` | 删掉 `instanceof Class` 的 if / else-if 分支 |
| `dropif.py File.java "if (cond) {"...` | 按条件原文删掉不带 else 的 if 块 |
| `dropmethod.py File.java "marker" name...` | 删掉 marker 之后（匿名类里也可以）的同名方法 |
| `extract_members.py File.java name...` | prune.py 解析不了的上游文件（如 MediaDataController）按名字抽取成员 |
| `fiximports.py` / `restoreimports.py` / `autoimport.py` / `unusedimports.py` | 整理 import |
| `audit.py [Class...]` | 对比上游，列出被删掉的顶层成员 |
| `show.sh Class from to` | 带行号打印源码片段 |

## 许可证

上游 Telegram for Android 使用 **GPL v2 或更高版本**授权（见 `LICENSE`）。本库是它的衍生作品，同样受 GPL 约束：分发包含本库的应用时，必须按 GPL 公开该应用的源码。闭源项目请先确认合规性。另外 Telegram 的名称和图标是商标，demo 用的是中性图标。
