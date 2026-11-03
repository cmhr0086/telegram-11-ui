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

坐标 `org.telegram.ui:tgui:11.4.2-ui4`。直接引用 AAR 文件时，宿主需要自己加 `androidx.core:core`、`androidx.annotation:annotation`、`androidx.dynamicanimation:dynamicanimation`、`androidx.customview:customview`、`androidx.collection:collection`、`androidx.interpolator:interpolator`、`androidx.viewpager:viewpager`。RecyclerView 用的是上游自带的 fork，包名改成了 `org.telegram.tgui.recyclerview`（源码在库里），可以和官方 `androidx.recyclerview` 共存，宿主照常引入 Material、preference、viewpager2 等即可。库的列表组件（`RecyclerListView` 等）用的是 fork，宿主代码里的 `RecyclerView` / `LinearLayoutManager` / `DiffUtil` 要从 `org.telegram.tgui.recyclerview` 导入，和库组件混用官方类会类型不匹配。

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

开箱即用的聊天页可继承 `org.telegram.ui.ChatFragmentBase`，只需提供原fork `RecyclerView.Adapter` 和输入回调；它复用现有组件，不另造气泡或数据层：

```java
@Override protected RecyclerView.Adapter createMessageAdapter(Context context) {
    return new MyMessageAdapter(context, messages); // 宿主拥有的数据与绑定逻辑
}
@Override protected ChatActivityEnterView.ChatActivityEnterViewDelegate createInputDelegate() {
    return new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
        @Override public void onSendText(CharSequence text, ArrayList<TLRPC.MessageEntity> entities,
                                         boolean notify, int scheduleDate) {
            sendFromHost(text, entities, notify, scheduleDate);
        }
    };
}
@Override protected void configureChat(Context context) {
    actionBar.setTitle("My chat");
    // 可选：输入权限/贴纸数据/联想、消息点击、宿主选择模式等。
}
```

基类装配 `ChatInputLayout`、fork `RecyclerListView`、从底部排列的布局管理器、气泡动画/群头像及 `ChatActivityEnterView`，统一ActionBar/系统栏padding、壁纸/emoji监听、日夜主题描述、输入面板Back与resume/pause/destroy；重建时释放旧输入和adapter并保留文字草稿/spans/选区。protected `contentView/listView/layoutManager/enterView` 可配置，`createMessageList` / `createInputView` 可定制。`configureChat` 在列表/输入创建之后、ActionBar最后挂载之前执行；每次重建都会调用，数据初始化仍放在 `onFragmentCreate`。覆盖生命周期、通知或主题描述时调用super；选择模式可先消费Back再调用super。业务发送、分页、媒体播放器、权限和数据存储仍归宿主；不会自动存录音草稿、回复/编辑目标或业务选择状态。demo与sample现已改为继承，原宿主直接组装方式仍支持。

保留低层写法（`ChatInputLayout` + `ChatActivityEnterView` + `TextBubbleCell`），自行组装时仍需以下步骤：
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

个人资料页 `ProfileFragment`：`new ProfileFragment(profile)`，`profile` 是 `ProfileFragment.Profile`（`id`、`name`、`status`、`online`、`photo`（`Drawable`，null 时画 `AvatarDrawable` 首字母，只有有照片才能下拉展开）、`verified`、`muted`）；
`setRows(list)` 给出下方的信息行（`Row.detail` / `bio` / `notifications` / `text` / `destructive` / `shadow`，每行 `setOnClickListener((fragment, row, view) -> …)`），
`addMenuItem(id, icon, text)` 加 ⋮ 菜单项，`setDelegate(...)` 接收 `onMenuItemClick` 和 `onWriteButtonClick`，`setWriteButtonAllowed(false)` 隐藏写消息按钮（自己的资料）；
之后 `presentFragment(fragment)`。改了模型字段调 `updateProfile()` / `updateRow(row)`；`openAvatar()` / `collapseAvatar()` 用代码展开 / 收起头像。用法见 demo 的 `DemoProfiles`。

图片 / 视频查看器 `MediaViewer`：`MediaViewer.open(activity, items, index, provider)`，`items` 是 `MediaViewer.Item`
（`Item.photo(drawable, sender, date, caption)` / `Item.video(uri, thumb, sender, date, caption)`，可设 `width` / `height` 和自用的 `tag`）。
`Provider` 全是 default 方法：`getSourceRect(index, outRect, outRadii)` 给出该项在屏幕上的位置（`PhotoBubbleCell.getPhotoRectOnScreen` / `getPhotoRadii`），
有位置就从气泡放大打开、关闭时缩回去，没有就淡入淡出；`setSourceVisible(index, visible)` 在查看期间隐藏气泡里的图（`PhotoBubbleCell.setPhotoVisible`）；
`onPageSelected` / `onClosed`。气泡里点图片会回调 `MediaDelegate.onMediaPressed`，宿主在这里打开查看器（下载中 / 未下载时仍是下载 / 取消）。
视频消息是带 `videoUri` 的图片气泡：`TextBubbleMessage.video(id, out, time, state, thumb, uri, durationSec, caption)`。用法见 demo 的 `DemoChatFragment.openMediaViewer`。

查看器右上菜单复用上游 `ActionBarMenuItem`：宿主覆写 `Provider.canPerformAction(action, index, item)` 按当前页启用 `MediaViewer.Action.SHARE / SAVE / DELETE`，在 `onAction(action, index, item)` 中确认、导出或更新自己的数据。默认全部禁用，旧调用没有额外菜单；也可调用 `openActionsMenu()` 打开。回调带菜单打开时的原始 Item 和整个列表索引，切页/关闭会作废旧菜单。库不请求存储权限、不执行分享Intent、不删除文件或列表项；宿主更新数据后可关闭并重新打开查看器。demo/sample 通过明确的“Host received”提示演示回调，不宣称已经保存/删除。

视频页的倍速入口使用上游 `SpeedIconDrawable`、`ActionBarMenuSlider.SpeedSlider` 和搬入的 `SpeedButtonsLayout`：滑条0.2–3倍、五个预设0.2/0.5/1/1.5/2倍，保留44dp滑条、196dp最小宽和原色。`setPlaybackSpeed(float)` / `getPlaybackSpeed()`、`setMuted(boolean)` / `isMuted()` 可编程设置，`openPlaybackMenu()` 打开菜单；`Provider.onPlaybackSpeedChanged` / `onMutedChanged` / `onPlaybackSpeedError` 接收状态与解码器拒绝的速度。偏好只保留于本次查看器，翻页后继续使用；暂停/播放完成时改速仅缓存，恢复时才应用，避免系统MediaPlayer自动开始播放。Android 21–22 不支持倍速，`isPlaybackSpeedSupported()` 返回false并隐藏速度选择，静音仍可用。静音行复用上游视频发送图标；这是观看模式的扩展，并非照搬上游发送/编辑页的静音位置。demo 的“Video viewer controls”和sample混合相册可操作；没有新运行依赖。

相册成员设置相同的非零 `Item.groupId`，并在列表中连续排列，即显示从固定上游搬入的 `GroupedPhotosListView`；`Provider.forceAllInGroup()` 可把整个输入列表作为一组。点击/拖动缩略图与左右翻页双向同步，回调索引始终是整个列表的索引，翻出分组后缩略图淡出。保留42×56dp缩略图、1dp间隔、2dp圆角、100/200ms选中动画；查看器用68dp区域/顶部10dp，手机横屏隐藏。普通组按上游最多取附近20项，选中仍在当前窗口时保持列表稳定；照片与视频都用宿主提供的静态Drawable作缩略图。`getGroupedPhotosListView()` 可获取组件，`setCurrentIndex(index)` 在打开动画结束后切页。demo 的相册及“Send a 10-photo album”、独立host混合相册均可打开体验；旧的无分组调用保持可用。

数据模型都是公开字段：
- `DialogItem`：`id`（稳定 id，决定头像配色）、`title`、`message`、`senderName`、`mediaPreview`、`draft`、`typing` / `typingType`、`time`、`unreadCount`、`markUnread`、`mention`、`muted`、`pinned`、`verified`、`secret`、`online`、`sentState`（`SENT_*`）、`avatar` / `avatarBitmap` / `avatarType`、`isFolder`（归档行）；搜索用：`status`（`ProfileSearchCell` 第二行）、`statusOnline`、`highlightedWords`（标题和消息里高亮的词）。
- `TextBubbleMessage`：`id`、`type`（`TYPE_TEXT` / `TYPE_VOICE` / `TYPE_PHOTO` / `TYPE_DOCUMENT`，宿主按它选单元格）、`text`（媒体消息是说明文字）、`isOut`、`time`、`state`（`STATE_*` 发送状态）、`reactions`（`TextBubbleMessage.Reaction` 列表：`emoji`、`count`、`chosen`，按列表顺序显示；`toggleReaction(emoji)` 按上游非 Premium 规则切换“我的”反应：再点取消，换一个则替换原来的）；媒体：`mediaState`（`MEDIA_READY` / `MEDIA_DOWNLOAD` / `MEDIA_LOADING`）+ `mediaProgress`，语音 `duration`（秒）/ `waveform`（上游 5-bit 打包，录音器给的就是这个格式）/ `playing` / `audioProgress` / `audioProgressSec` / `contentUnread`，图片 `photo`（Drawable）/ `photoWidth` / `photoHeight`，文件 `fileName` / `fileSize` / `documentThumbnail`。用 `TextBubbleMessage.voice(...)` / `photo(...)` / `document(...)` 创建。

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

### 归档与删除撤销条

`UndoView` 从上游归档/删除分支裁剪而来，保留 5 秒期限、归档 Lottie、删除数字圆环、10dp 圆角和原进出场动画。先把它加到页面底部（左右/底部各 8dp），宿主立即更新列表的视觉状态，再显示：

```java
UndoView undo = new UndoView(context, getResourceProvider());
container.addView(undo, LayoutHelper.createFrame(-1, -2, Gravity.BOTTOM, 8, 0, 8, 8));
undo.showWithAction(UndoView.ACTION_DELETE, null,
    () -> commitDeletion(),     // 5 秒到期或被下一条替换时执行一次
    () -> restoreOriginalRows() // 点击 Undo 时执行一次
);
```

支持 `ACTION_ARCHIVE` / `ACTION_ARCHIVE_FEW` / `ACTION_DELETE` / `ACTION_DELETE_FEW`。文案传 null 使用上游字符串，也可以传宿主文本。归档显示动画图标，删除显示数字倒计时。操作、原位置快照和持久化均由宿主负责，库不调用账号或消息控制器；重入时被更晚提示抢占的请求会立即执行 apply。离开页面可调用 `hide(true, 0)` 结算；从窗口移除也会结算待处理操作。主题变化时调用 `updateColors()`，如 demo 在 `getThemeDescriptions()` 中登记 undo 三个颜色键。进度仅在内存中，不承担进程重启后的业务恢复。

示例：原 demo 的 Chat list 页，长按进入多选后归档/删除，或在 Swipe action 菜单选择 Archive/Delete；外部宿主 `samples/host-app` 长按条目可归档并撤销。原位恢复及文件夹过滤由示例代码维护。

### 本地动态表情

```java
// 在 TelegramUI.init 后设置；宿主管理文件及 documentId 的映射。
AnimatedEmojiDrawable.setSourceProvider(id -> localFiles.get(id));
SpannableStringBuilder text = new SpannableStringBuilder("Hello 🐥");
text.setSpan(new AnimatedEmojiSpan(90001L, edit.getPaint().getFontMetricsInt()),
    6, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
enterView.setFieldText(text);
```

`SourceProvider` 在 UI 线程查询，返回已准备好的本地文件；支持普通 Lottie JSON 与 gzip TGS，解压上限 4 MiB，不访问网络。读取、解压和首次创建 rlottie 是同步的，宿主应提前准备小型表情素材。更换映射或文件内容后再次调用 `setSourceProvider`，已有视图下一帧重载。无来源或损坏文件回退为 span 内原文字，库只处理缓存副本，不删除宿主文件。

现有 `ChatActivityEnterView` / `EditTextEffects` 与 `TextBubbleCell` 自动管理布局和 attach/detach；自绘 StaticLayout 的宿主需保存 `AnimatedEmojiSpan.update(cacheType, view, group, layouts)` 的返回值，释放时调用 `release(view, group)`，布局正常 `draw` 即可。独立 drawable 需成对 `addView` / `removeView`，最后解绑回收，重绑可重新加载。发送/存储使用 `MediaDataController.getEntities`，恢复样式后另调 `addAnimatedEmojiSpans`；不要用 `toString()` 丢弃实体。各视图间可用 `cloneSpans` 隔离字体尺寸。

demo 的 Chat 页末尾有本地 JSON / TGS 混排；更多菜单的 “Insert local animated emoji” 可插入原输入栏并发送。素材复用上游已打包的 Utya 动画，压缩成 TGS 仅用于演示本地文件入口。`samples/host-app` 也提供 AAR 消费示例。

范围：本地 rlottie 静态图层/矢量动画，不含服务器下载、视频/WebM、外置图片资源、自定义表情包选择页及动态反应。当前兼容的 `drawAnimatedEmojis` 叠加入口不二次绘制，独立 overlay 的 alpha/filter/spoiler 参数尚未支持；行内绘制跟随 text paint 和 Canvas 的透明度、裁剪及坐标。媒体说明文字和回复预览尚未接入动画生命周期。

### 相册分组

```java
TextBubbleMessage album = TextBubbleMessage.album(id, isOut, "12:34", state,
    photos, caption); // 2..10 个 photo(...) / video(...)，顺序由宿主提供
AlbumBubbleCell cell = new AlbumBubbleCell(context, resourcesProvider);
cell.setMessage(album, pinnedTop, pinnedBottom);
cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
    public void onMediaPressed(TextBubbleCell cell, TextBubbleMessage item) { }
    public void onAlbumMediaPressed(AlbumBubbleCell cell, TextBubbleMessage group,
                                   TextBubbleMessage item, int index) {
        openHostViewer(item, index);
    }
});
```

`GroupedMediaLayout` 直接抽取上游 `MessageObject.GroupedMessages` 的 2/3/4 张特殊布局及多图行枚举/评分，保留 800/1000 span、814 高度基准、比例裁剪和边缘标志。照片尺寸来自 `photoWidth/photoHeight` 或 Drawable intrinsic，未知时按正方形处理。`AlbumBubbleCell` 复用 `PhotoBubbleCell` 的中心裁剪、背景、说明、回复、反应、时间和长按；每格支持原下载/取消/播放图标及视频时长，点击回调给出原 item，整组只显示一次时间/状态。修改成员、尺寸或组说明后重新 `setMessage`。

此处以一个 RecyclerView 行呈现整组，按行将上游逻辑宽归一化、相邻格留 2dp，保留外边圆角和 4dp 内角；没有复制上游多 ChatMessageCell + GridLayoutManager 的不对称 inset 和父层延后绘制，因此不宣称所有像素都与客户端相同。当前是照片/视频缩略图相册，不含文件组、各格独立消息多选/删除、captionAbove 或组布局变化动画。群聊头像列由整行统一让位。宿主负责下载、解码、数据持久化和播放。

查看器可通过 `getTileRectOnScreen(index, rect)` / `getTileRadii(index)` 获取来源，`setTileVisible(index, visible)` 隐藏单格。demo 将相册成员与现有媒体展平成查看器序列，翻页/返回对应格；成员无单独说明时每页显示共享说明。同组成员必须是各自独立的消息对象，复用照片可另建模型。Chat 页有 2/3/4/5 张样例，更多菜单可发送 10 张，长按选择整组。AAR 消费示例也有三图加一段本地视频的混合相册。

### 圆形视频消息

```java
TextBubbleMessage video = TextBubbleMessage.roundVideo(id, isOut, "12:34", state,
    thumbnail, localUri, durationSeconds);
RoundVideoBubbleCell cell = new RoundVideoBubbleCell(context, resourcesProvider);
cell.setMessage(video, pinnedTop, pinnedBottom);
cell.setMediaDelegate((source, item) -> hostPlayer.toggle(item));
// 宿主播放/暂停/进度事件：暂停表示 playing=false、videoPaused=true；停止时两者 false。
video.playing = isPlaying;
video.videoPaused = isPaused;
video.audioProgress = fraction;
video.audioProgressSec = elapsedSeconds;
cell.updatePlayingMessageProgress();
```

`RoundVideoBubbleCell` 按上游普通圆视频分支裁剪：最小侧 0.6 倍直径、圆形中心裁剪、原 `RoundVideoShadow`、17dp 高/6dp 圆角/50% 服务底的时长与未读点、3dp 圆头白色进度环、单个时间和发送状态。圆内点击回调媒体操作，圆外不触发播放；长按可由既有选择回调处理。`mediaState/mediaProgress` 同样走下载/取消/进度 UI。

视频解码和声音由宿主负责。`getVideoRectOnScreen(rect)` 给出上次绘制的圆框；可在透明列表后面放圆形 Surface/TextureView，首帧准备好后 `setVideoVisible(false)` 隐藏缩略图，气泡仍在视频上方画时间、状态和环，并继续接收点击。离屏、暂停页面或解绑时宿主必须停止/释放自己的播放器，恢复缩略图。demo 的 `LocalRoundVideoPlayer` 展示 MediaPlayer + TextureView：居中裁剪、本地六秒素材、暂停/恢复、切换播放、音频焦点丢失暂停、离屏/退出释放。消费端提供纯 UI/回调示例。

当前普通圆视频不支持播放扩大/过渡动画、拖环定位、转写、阅后即焚、画中画及反应布局；支持旁侧转发头和回复块，也可引用圆视频到普通文字消息。播放器不是库内媒体服务，不处理录制、下载、后台播放和进程恢复；MediaPlayer 格式兼容性由设备决定。窄圆的两个信息条重叠时，时长条向上避让。

### 本地贴纸与 GIF（阶段 A）

宿主提供不可变 `StickerSet` / `Sticker` / `Gif` 元数据及 `MediaSource`。本地 `File` 可直接传入；异步来源用 `new MediaSource(result -> { ...; return cancellation; })`，准备好文件后调用 `result.onLoaded(file)`，失败调用 `onFailed()`。回调允许后台线程；取消、过期结果和 View 复用由库隔离，文件的保存与删除仍由宿主负责。库不连接 Telegram，不增加第三方解码依赖。

```java
Sticker sticker = new Sticker(1, Sticker.Format.TGS, localFile, thumbnail, 512, 512, "🦆");
StickerSet pack = new StickerSet(10, "My pack", Collections.singletonList(sticker));
enterView.setAllowStickersAndGifs(true, true, true);
enterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
    @Override public void onEmojiPanelCreated(EmojiView panel) {
        panel.setMediaData(packs, recentStickers, favoriteStickers, gifs);
    }
    @Override public void onSendSticker(Sticker item) { /* host sends and updates recents */ }
    @Override public void onSendGif(Gif item) { /* host sends */ }
    @Override public void onStickerSetRequested(StickerSet set) { /* show StickersAlert */ }
    @Override public void onStickerFavoriteChanged(Sticker item, boolean favorite) {
        // Persist in the host, then call panel.setMediaData with the updated snapshot.
    }
});
```

直接使用 EmojiView 时，其 delegate 对应 `onStickerSelected` / `onGifSelected`。`getCurrentPage()` 返回类型 emoji=0、GIF=1、贴纸=2；兼容输入栏的 `onTabOpened` 则为 0/2/3。`setAllow` 隐藏禁用页。收藏、最近、包标签和比例 GIF 网格共用原 pager；长按 200ms 预览，移动超过 10dp 取消，松手关闭，继续按住 1300ms 打开宿主操作菜单。`StickersAlert(context, set, delegate, provider)` 提供五列贴纸包、点选预览/发送与长按操作。

消息通过 `TextBubbleMessage.sticker(...)` / `gif(..., caption)` 创建，适配器选择 `StickerBubbleCell` / `GifBubbleCell`。静态 WEBP、JSON/gzip TGS 和 GIF 在附着时加载，隐藏时停播、离开窗口释放；`setAnimating(false)` 可显式暂停。透明贴纸沿用上游比例与 service 时间色，GIF 复用照片气泡的布局、圆角、说明、回复和触摸。

demo 的 Chat 页包含上述消息，输入面板可选择、发送、收藏、预览包；`samples/host-app` 覆盖发布 AAR 的相同 API。示例的最近/收藏只保存在页面内存；示例 GIF/WEBM 由既有 sample_video.mp4 转码，不增加应用运行依赖。

限制：WEBM 当前固定降级为设备 MediaMetadataRetriever 解出的首帧，解码失败保留宿主缩略图；没有视频贴纸实播。GIF 在 API 28+ 使用系统 AnimatedImageDrawable，21–27 使用 Movie（后者尚未设备验证）。不含服务器搜索、精选包/安装管理、Premium、完整上游拖拽展开/预览模糊与滑出菜单动画；包弹窗布局裁剪为固定五列。贴纸支持旁侧回复/转发头，下载业务交宿主；GIF 回复/说明沿用现有照片气泡。尚未逐像素标定所有屏幕尺寸/平板/RTL。

### 附件面板

输入栏的附件按钮通过 `didPressAttachButton` 回调宿主；宿主可在其中打开 `ChatAttachAlert`，并处理选中的附件和说明文字：

```java
new ChatAttachAlert(activity, new ChatAttachAlert.Delegate() {
    @Override
    public void onSend(List<Attachment> items, CharSequence caption) {
        // 由宿主读取 URI、保存或发送附件及说明。
    }

    @Override
    public List<Attachment.Contact> getContacts() {
        return Collections.emptyList(); // 可替换为宿主提供的联系人
    }
}, resourcesProvider).show();
```

面板包含相册、文件、联系人、位置和投票页，当前页的选择通过 `Attachment` 模型返回；联系人由 `getContacts()` 提供，位置页通过 `createLocationView(Context, LocationCallback)` 注入宿主地图并回传位置，投票由本地表单创建。`onPermissionResult` 可接收相册权限及部分授权状态。库不读取系统联系人，也不提供地图或上传服务。

相册使用 MediaStore，并按系统版本申请媒体读取权限；Android 14 的部分照片访问可重新选择。文件页使用系统文档选择器，返回的 `Uri` 及其授权取决于提供方；宿主需要处理访问失败，并在需要长期使用时保存可持久授权或复制内容。选择器进行中如果 Activity 重建，待处理选择会取消，需要重新打开。demo 当前将选择的图片和视频以 URI 文字摘要展示；文件使用文件气泡，联系人使用名片气泡，位置显示地图气泡，投票显示可操作气泡，说明文字单独显示。

附件预览是精简列表，不包含 `PhotoViewer` 图片编辑功能；顶部手柄支持拖动，但不包含上游完整的整页联动。贴纸 WEBM 的首帧降级限制见上节。

### 未读消息分隔条

`ChatUnreadCell` 直接搬运上游：40dp 行高、27dp 分隔背景、居中文字和右侧箭头。宿主在第一条未读消息前插入独立 adapter 行，分隔行打断连续气泡分组。

```java
ChatUnreadCell row = new ChatUnreadCell(context, resourcesProvider);
row.setText(context.getString(org.telegram.tgui.R.string.UnreadMessages));
// 主题切换后重读 provider；也可沿用 getter / ThemeDescription 更新三个颜色键。
row.updateColors();
```

demo 聊天菜单的 Unread Messages 可定位到分隔行；宿主示例菜单提供独立预览。是否已读和未读数量由宿主管理。

服务消息继续使用 `ChatActionCell` 的原版文字胶囊；`ServiceMessage` 提供普通群返回、大型群加入、邀请、改名、置顶及宿主自定义事件。名字和置顶目标只保存宿主 ID，不查询账号或服务器。

```java
ServiceMessage.Person alice = new ServiceMessage.Person(userId, "Alice");
ChatActionCell cell = new ChatActionCell(context, resourcesProvider);
cell.setMessage(ServiceMessage.pin(eventId, alice, false, messageId, "Pinned preview"));
cell.setDelegate(new ChatActionCell.Delegate() {
    @Override public void onUserPressed(Object id) { /* host opens profile */ }
    @Override public void onPinnedMessagePressed(Object id) { /* host locates message */ }
});
// join(id, person, isSelf, true) selects the upstream megagroup joined wording.
```

demo 的 “Service messages” 菜单定位示例；点击置顶正文跳到原消息。宿主 sample 的信息按钮打开 AAR 服务消息预览。文案通过 Android 资源翻译；媒体置顶类型由 `PinnedKind` 指定，文本预览沿用上游 20 字符截断。仅实现文字服务事件，未包含头像变更卡片、礼物、按钮、剧透、链接按压高亮或逐链接无障碍导航；其它事件可由宿主通过 `custom` 提供。

群聊通过既有消息模型启用；姓名使用上游按用户 ID 选择的颜色，宿主也可提供 `Sender.colorId` 色板下标。`avatar` 为静态 Drawable，缺省使用上游首字母头像。普通消息在组首显示姓名；贴纸、圆视频、纯 emoji 使用媒体旁的服务色姓名胶囊。

```java
message.group = true;
message.sender = new TextBubbleMessage.Sender(userId, displayName, avatar);
message.date = unixSeconds;
cell.setMessage(message, MessageGrouping.canMerge(previous, message),
        MessageGrouping.canMerge(message, next));
// Add once to the fork RecyclerView, then enable on each bound cell:
list.addItemDecoration(new ChatAvatarDecoration());
cell.setAvatarDrawnByList(true);
```

`MessageGrouping` 要求同发送者、同收发方向、同群聊上下文、时间相隔不超过 300 秒；未知发送者/时间不合并。日期、服务和未读行应以 null 邻居打断分组。头像为 42dp、左侧列留 48dp；列表装饰在可见区域底部绘制连续组头像，遵守列表 padding 和行透明度。不启用装饰时仍使用行内头像。demo 的 “Group names and avatars” 可切换；host sample 使用群聊模型。尚未包含头像点击/动态头像、管理员头衔/频道签名、Premium 多色姓名，以及圆视频播放放大的姓名过渡；RTL/平板仍需人工检查。

### 转发来源

```java
message.forwardedFrom = new TextBubbleMessage.ForwardedFrom(sourceId, sourceName,
        sourceAvatar, -1); // null avatar uses initials; -1 color uses the upstream ID rule.
cell.setMessage(message, pinnedTop, pinnedBottom);
cell.setDelegate(new TextBubbleCell.Delegate() {
    @Override public void onForwardPressed(TextBubbleCell cell, TextBubbleMessage message,
                                           TextBubbleMessage.ForwardedFrom source) {
        // Host opens the source; the library performs no account lookup.
    }
});
```

`sourceId=0` 表示匿名来源，保留名字但不画头像；空名字或 `forwardedFrom=null` 隐藏头部。既有普通气泡在正文/媒体上方显示两行来源，贴纸与圆视频放在旁侧，纯 emoji 保留大号绘制并预留上方空间。`ForwardBackground` 直接搬运上游路径、按压水波和回弹；字体、头像比例、间距与颜色 key 来自 `ChatMessageCell` / `AvatarSpan`。同一消息修改来源后重新绑定即可。

贴纸/圆视频的回复块与来源共用服务底色，分别触发 `onReplyPressed` / `onForwardPressed`；媒体区域仍走媒体回调。demo 的 “Forwarded messages” 菜单定位示例，点击来源显示宿主回调；外部 sample 也有来源模型。极窄的群聊媒体行可能没有足够宽度显示旁侧来源（如 240dp），保留上游媒体尺寸并省略文字，不为此缩小贴纸或圆视频。未移植 PSA、导入消息、Saved Messages 特殊头、来源加载状态和动态头像；纯 emoji 只有回复、没有转发时仍沿用既有普通文字气泡布局。复杂 RTL、平板及大字体需人工检查。

### 联系人消息

```java
TextBubbleMessage.Contact contact = new TextBubbleMessage.Contact(contactId, name,
        formattedPhone, avatar, canMessage, canAdd);
TextBubbleMessage message = TextBubbleMessage.contact(messageId, outgoing, time, state, contact);
ContactBubbleCell cell = new ContactBubbleCell(context, resourcesProvider);
cell.setMessage(message, pinnedTop, pinnedBottom);
cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
    @Override public void onMediaPressed(TextBubbleCell cell, TextBubbleMessage message) { }
    @Override public void onContactAction(TextBubbleCell cell, TextBubbleMessage message,
                                         TextBubbleMessage.Contact contact, int action) {
        // ContactBubbleCell.ACTION_VIEW / ACTION_MESSAGE / ACTION_ADD: host handles the action.
    }
});
```

`ContactBubbleCell` 从上游 `ChatMessageCell.TYPE_CONTACT` 分支裁剪，复用已有气泡、群聊、转发、回复、时间和反应。名片保留 46dp 头像、15/13dp 姓名/号码、270dp 最大气泡和底部 36dp 按钮。四参 `Contact(id,name,phone,avatar)` 默认只有 View Contact；宿主可用 capability 标志提供 Message / Add Contact。单按钮时点击整卡执行该动作，多按钮时整卡打开查看、按钮分别回调；长按仍用于选择消息。

号码由宿主格式化，空号码使用 Android 字符串资源。头像为静态 Drawable，缺省使用首字母或联系人图标。库不查询账号或系统通讯录，不保存联系人、不启动电话或发送消息；示例以 Toast 展示动作，实际操作由宿主接管。demo 的 “Contact messages” 菜单可定位样例，附件菜单选择联系人后显示名片，外部 sample 同样接入。vCard 解析、Premium 多色侧线/背景表情、动态头像尚未移植。

### 音乐消息

```java
TextBubbleMessage track = TextBubbleMessage.music(id, outgoing, time, state,
        title, performer, durationSeconds, caption);
MusicBubbleCell cell = new MusicBubbleCell(context, resourcesProvider);
cell.setMessage(track, pinnedTop, pinnedBottom);
cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
    @Override public void onMediaPressed(TextBubbleCell cell, TextBubbleMessage message) {
        // Host starts/pauses playback, or downloads/cancels according to mediaState.
    }
    @Override public void onSeek(TextBubbleCell cell, TextBubbleMessage message, float progress) {
        // Seek the host player; progress is in [0, 1].
    }
    @Override public void onMenuPressed(TextBubbleCell cell, TextBubbleMessage message, float x, float y) {
        // Open the host message menu.
    }
});
// From host player updates, on the UI thread:
track.playing = isPlaying;
track.audioPaused = hasPausedSession;
track.audioProgress = positionFraction;
track.audioProgressSec = elapsedSeconds;
cell.updatePlayingMessageProgress();
```

`MusicBubbleCell` 从固定上游 `ChatMessageCell.DOCUMENT_ATTACH_TYPE_MUSIC` 裁剪，保留 270dp 宽上限、82dp 主体、44dp 播放按钮、标题/演唱者/时长颜色与 100ms 演唱者到进度条过渡；支持说明、反应、群聊、转发及回复。暂停时设置 `playing=false, audioPaused=true`，仍显示进度条和已播放秒数；停止会话时两者均为 false。用 `MEDIA_DOWNLOAD` / `MEDIA_LOADING` / `MEDIA_READY` 和 `mediaProgress` 控制下载状态，`updatePlayingMessageProgress()` 同步按钮与进度，无需重绑整个列表。库不持有播放器、文件 URI 或网络加载器。

demo 的 ⋮ “Music messages” 定位三条样例，用系统 `MediaPlayer` 播放项目生成的 12 秒 PCM 音阶，可暂停、拖动、切曲，播完释放；与语音/圆视频互斥，离开页面、重建视图或进入选择时停止。第三条仅模拟下载状态，完成后播放同一本地文件，不访问网络。外部 `samples/host-app` 展示 AAR 气泡和回调，实际播放器接入见主 demo 的 `LocalMusicPlayer`。没有新增运行依赖；WAV 只打包进 demo。

未移植流式 mini 下载按钮、封面覆盖图、播放列表/后台服务/通知控件和歌词时间戳；说明文字显示样式但没有独立链接点击。音频焦点丢失会暂停，需要用户主动恢复。库只渲染宿主提供的元数据，音轨读取、格式支持和 URI 授权由宿主管理。

### 位置消息

```java
TextBubbleMessage.Location place = new TextBubbleMessage.Location(latitude, longitude,
        title, address, mapImage); // title empty: plain coordinates; nonempty: venue card
TextBubbleMessage message = TextBubbleMessage.location(id, outgoing, time, state, place);
LocationBubbleCell cell = new LocationBubbleCell(context, resourcesProvider);
cell.setMessage(message, pinnedTop, pinnedBottom);
cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
    @Override public void onMediaPressed(TextBubbleCell cell, TextBubbleMessage message) { }
    @Override public void onLocationPressed(TextBubbleCell cell, TextBubbleMessage message,
                                          TextBubbleMessage.Location location) {
        // Host opens its map or handles the selected coordinates.
    }
});
```

`LocationBubbleCell` 从固定上游 `ChatMessageCell.TYPE_GEO` 的普通坐标与地点分支移植。地图固定 195dp 高、气泡最大 285dp；纯坐标使用媒体背景和图内时间，地点卡片在图下显示 15dp 标题及 13dp 地址。保留原图钉、圆角、群聊/转发/回复、反应和长按选择；无新回调实现时 `onLocationPressed` 默认交给既有 `onMediaPressed`。

地图 `Drawable` 由宿主准备，并按中心裁切；请提供以目标坐标居中、不含图钉的底图，库在中心画原图钉。`mapImage=null` 显示原 `map_placeholder` 的静态回退图，仍可点击；宿主取得图片后替换 `message.location` 并重新绑定。`getMapRect(RectF)` / `getMapRadii()` 提供上次布局的图区域。库不加载地图、不定位、不引入地图 SDK，也不直接启动外部地图应用。

demo 的 ⋮ “Location messages” 有地点、纯坐标及缺图示例；附件位置页选择并确认后直接发送位置气泡。demo 和 sample 的道路图是生成的示意图，坐标 `(0,0)` 仅用于回调演示，不是真实地图服务。暂不含实时位置共享、地图加载渐变/缩放和闪动占位；静态占位采用上游关闭背景动画时的 alpha 70 分支。说明文字通过附件流程作为独立消息发送。

### 投票消息

```java
List<TextBubbleMessage.PollOption> options = Arrays.asList(
        new TextBubbleMessage.PollOption("walk", "Walk", 0, false),
        new TextBubbleMessage.PollOption("read", "Read", 0, false));
TextBubbleMessage.Poll poll = new TextBubbleMessage.Poll(question, options,
        0, true, false, false, false); // totalVoters, anonymous, multiple, closed, pending
TextBubbleMessage message = TextBubbleMessage.poll(id, outgoing, time, state, poll);
PollBubbleCell cell = new PollBubbleCell(context, resourcesProvider);
cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
    @Override public void onMediaPressed(TextBubbleCell cell, TextBubbleMessage message) { }
    @Override public void onPollVote(TextBubbleCell cell, TextBubbleMessage message, List<String> ids) {
        // Publish a pending snapshot, then ask your own data source to record the vote.
        // Replace message.poll with the authoritative result (or pending=false on failure)
        // and rebind on the UI thread. The library does not add votes itself.
    }
    @Override public void onPollResults(TextBubbleCell cell, TextBubbleMessage message) {
        // Open the host's results page for a public poll.
    }
    @Override public void onPollOptionPressed(TextBubbleCell cell, TextBubbleMessage message,
                                             TextBubbleMessage.PollOption option) {
        // Show this option's count or host-provided voters.
    }
});
cell.setMessage(message, pinnedTop, pinnedBottom);
```

`PollBubbleCell` 从固定上游 `ChatMessageCell.TYPE_POLL` 布局、绘制、触摸与结果过渡分支裁剪。单选点选后提交；多选先勾选，再点 VOTE；已投票或关闭时显示结果。问题/选项文字、稳定选项 ID、票数、当前用户 chosen、匿名/多选/关闭/pending 均由宿主快照提供；选项 ID 在同一投票的后续快照中保持稳定。未发送完成的消息不接受投票。结果行回调包含具体选项，公开投票的底部按钮回调为 `onPollResults`。

百分比沿用上游整数和余数分配规则，等票可能显示 `33% / 33% / 33%`；多选的分母是投票人数，百分比总和可以超过100。条形长度相对最高选项，保留原最短5dp及300ms结果过渡。宿主应在回调里立即发布 pending 状态，完成或失败后替换快照并重绑；不要把 UI 的勾选直接当作已确认选票。构造器可追加第8参 `List<String> pendingOptionIds`，传入正在提交的稳定 ID，使列表复用/重建后仍能恢复对应等待圆环和多选勾选。

demo ⋮ “Poll messages” 提供单选、多选公开投票和关闭结果；本地回调延迟650ms展示 pending，然后更新内存票数。sample 同样展示宿主更新模型；附件创建投票→确认发送后直接成为可投票气泡。这些数据只用于本地演示，不连接服务器。暂不含 quiz/答题解释/定时关闭倒计时、最近投票者头像和库内投票者列表；实际参与者、撤票或关闭操作由宿主处理。极窄宽度下的长票数/自定义长时间、大字体和更多语言仍需人工检查；当前内置文案为英文，宿主可覆盖 Android 资源。

### @提及列表

`MentionsContainerView` / `MentionCell` 从固定上游的普通用户提及分支裁剪。宿主负责候选来源、过滤与排序，库不访问通讯录、群成员或 Telegram 网络：

```java
MentionsController mentions = new MentionsController(enterView, chatInputLayout,
        (query, results) -> {
            // Query.query excludes @; Query.start/end are UTF-16 replacement offsets.
            results.onResult(hostFindUsers(query.query)); // List<Mention>; async allowed
        }, resourcesProvider);
// A candidate: stable ID, display name, optional username, optional static Drawable.
Mention user = new Mention(42, getString(R.string.member_name), "alice", avatar);
```

输入框聚焦后，文本与光标变化会自动查询；非折叠选区、失焦或录音时隐藏。异步结果回到主线程，并核对全文/光标/请求代次；有效新查询等待期间保留旧面板但旧候选不能插入，新结果原位替换；宿主数据变化时调用 `mentions.refresh()`。`ChatInputLayout` 的 `setInputSuggestionsView` 将候选面板锚定输入上方并跟随 IME/表情面板位移，输入销毁会自动释放 controller，也可提前 `destroy()`。每个输入只绑定一个 controller；替换时自动释放旧绑定。

点击保留光标右侧文字，只替换 `[@, 光标)` 并添加空格；这与上游一致，所以在单词中间选择候选不会吞掉右半词。无 username 的候选插入原 `URLSpanUserMention`，ID 通过既有 `onSendText` 实体交给宿主。username 可带前导 @，模型会规范化。候选ID在列表中须唯一，不能用RecyclerView的NO_ID（−1）。边界沿用上游文本起点/空格/换行，允许全名查询中的空格，不把邮箱里的 @ 当作提及。

原36dp行高、28dp头像、15dp文字、6dp面板圆角和stiffness=550无弹性spring保留，最多显示3.5行/屏幕22%，列表可滚动。头像由宿主提供静态Drawable，绘制后恢复bounds；动态头像需宿主刷新。demo菜单“Mention a member”填入@并展示候选；sample输入@同样可用。没有inline bot网格/网络搜索、反向布局、模糊背景或群成员权限逻辑；仅普通用户提及。

### 表情联想

`SuggestEmojiView` 从上游同名组件搬运，保留箭头浮层、横向列表、渐隐边缘与展开/按压动画。`EmojiSuggestionsController` 接入现有输入栏，关键词与普通 emoji 的候选来源由宿主决定：

```java
EmojiSuggestionsController suggestions = new EmojiSuggestionsController(
        enterView, chatInputLayout, (query, results) -> {
            // query.type is KEYWORD or EMOJI; start/end are UTF-16 offsets.
            results.onResult(hostFindEmoji(query.type, query.query)); // async allowed
        }, resourcesProvider);
EmojiSuggestion unicode = new EmojiSuggestion("smile", "😀", 0);
// Nonzero documentId resolves through the existing AnimatedEmojiDrawable.SourceProvider.
EmojiSuggestion animated = new EmojiSuggestion("duck", "🐥", localDocumentId);
```

候选使用唯一稳定字符串 ID、Unicode 文字和可选本地 documentId；动态内容复用现有本地 SourceProvider，不访问 Telegram。首次无候选时沿用600ms查询延迟，文本/光标通知合并到16ms后；有候选时新查询直接交给宿主。关键词取光标前完整前缀（长度小于52），不是最后一个单词；emoji 查询取光标前完整 EmojiSpan。异步结果核对全文、光标及请求代次，后台回调自动切主线程；旧候选在新结果到达前不可插入。绑定提及 controller 时，@查询优先。

点击关键词候选替换光标前前缀；点击 emoji 候选沿用上游向左替换连续相同 emoji 的规则，保留右侧文字，并为每个动态表情建立独立 span，遇到既有动态 span 停止替换。`setDelegate` 的 `onSelected` / `onLongPressed` 可供宿主统计或打开预览，长按本身不替换文字。输入栏销毁会自动释放 controller，也可调用 `destroy()`；同一输入重新绑定会释放旧 controller。候选面板跟随输入框和 emoji 所在行，失焦、录音或展开贴纸时隐藏；`refresh()` 可主动刷新宿主数据。

保留原44×52dp候选、66.66dp浮层、9dp圆角及hint panel颜色 key，普通 emoji 图集冷加载会自动刷新。demo ⋮ “Emoji suggestions” 可填入 `smile` 或两个普通 emoji，sample 输入同样内容可体验本地候选。宿主负责多语言搜索、排序和长按预览；未迁移 Telegram 关键词下载、Premium 策略或状态选择器。其它Android版本、平板、复杂RTL/大字体及真实软键盘过渡仍需人工补验。

### 机器人命令菜单

`org.telegram.ui.bots.BotCommandsMenuView` / `BotCommandsMenuContainer` 从同名上游组件搬运，接回现有 `ChatActivityEnterView` 左侧 Menu 入口。宿主发布纯命令快照：

```java
enterView.setBotCommands(Arrays.asList(
        new BotCommand("start", "start", getString(R.string.command_start)),
        new BotCommand("help", "help@my_bot", getString(R.string.command_help))));
// Optional: open/dismiss explicitly; the input's Menu button also toggles it.
enterView.showBotCommandsMenu();
```

`BotCommand` 的稳定字符串 ID 须唯一；命令规范为单个前导 `/`，可包含 `@bot`，不能含空白。说明文字和列表均为快照。短按沿用上游行为：清空草稿、关闭菜单后调用 delegate 的 `onBotCommandSelected(BotCommand)`；默认实现转发已有 `onSendText(command.command, emptyEntities, true, 0)`，宿主也可覆盖它处理自己的发送策略。长按覆盖输入为 `command + " "` 并关闭，不发送、不强制打开软键盘。更新快照后旧行不会发送旧命令，回调中发布并打开新菜单不受旧清理影响。

Menu 在空输入且没有键盘/表情面板时展开文字；其它状态收成图标。编辑、录音、音视频待发送、暂停时关闭，空列表移除入口。文本变化关闭菜单；与底部表情/回复键盘可共存，菜单贴输入上沿，不占用底部面板高度，同时压制提及/表情联想。使用 `ChatInputLayout` 自动完成位置与位移，销毁由 `enterView.onDestroy()` 释放。宿主处理返回键时先判断 `botCommandsMenuIsShowing()`，调用 `dismissBotCommandsMenu()` 并消费事件；demo/sample已接入，也可与现有 `hidePopup(true)` 合用。

保留原40dp图标宽/32dp入口高、15dp入口文字、16/14dp说明与命令文字、16/8dp行内边距、最多两行说明，以及320ms Overshoot(0.8)入场、150ms收起/回弹、下拉超过16dp关闭。行高由文字测量决定，36dp仅用于首次布局估算；初始显示前四行及下一行边缘，可滚动展开更多。demo/sample提供六个本地命令，默认发送为普通消息。菜单不查询机器人资料，不执行 Telegram 排程/慢速模式/Web App/支付；宿主负责权限和执行结果。尚不提供输入 `/` 的命令自动补全。

### 回复键盘（reply keyboard）

`org.telegram.ui.bots.BotKeyboardView` 从同名上游类搬运，`ReplyKeyboard` 替代 TL 标记。集成在原 `ChatActivityEnterView` 内，不需要宿主另建底部容器：

```java
ReplyKeyboard keyboard = new ReplyKeyboard("greeting-v1",
        Collections.singletonList(Arrays.asList(
                new ReplyKeyboard.Button("hello", getString(R.string.greeting), ReplyKeyboard.Button.Type.TEXT),
                new ReplyKeyboard.Button("location", getString(R.string.location), ReplyKeyboard.Button.Type.REQUEST_LOCATION))),
        true, false, getString(R.string.choose_reply)); // resize, singleUse, placeholder
enterView.setReplyKeyboard(keyboard, false); // Bind without interrupting the current panel.
enterView.showReplyKeyboard();
// Add this to the existing ChatActivityEnterViewDelegate:
@Override public void onReplyKeyboardButtonPressed(ReplyKeyboard keyboard, ReplyKeyboard.Button button) {
    // Host sends text, requests consent, opens its picker or handles its own URL/data.
}
```

单参 `setReplyKeyboard(keyboard)` 在输入为空且没有其它面板或系统键盘时自动打开；双参可禁止自动打开。输入栏原48dp键盘按钮负责展开/收起，表情面板与回复键盘互斥，点输入框切回系统输入法。`singleUse` 点击后只收起，模型仍可重新打开；回调中替换模型不会关闭新键盘。`setReplyKeyboard(null)` 移除键盘和入口，`getReplyKeyboardView()` / `getReplyKeyboardButton()` / `isReplyKeyboardShowing()` 供宿主协调界面；独立 `BotKeyboardView` 也可用 `setPanelHeight` / `setButtons` 接入自己的布局。

原布局为42dp最小行高、16dp文字、4dp圆角、左右/首尾15dp及行列10dp间距；`resize=true` 按内容高度显示，false 填充记住的键盘高度，多行超过面板高度可滚动。行列表、按钮文字和占位文字都是快照，按钮ID在键盘内唯一；空总行列表表示无键盘，空单行/空按钮/重复ID会被拒绝。支持 TEXT、REQUEST_CONTACT、REQUEST_LOCATION、POLL、URL、COPY、WEB_VIEW，Web View 保留原右上角图标。实际发送、权限/同意、打开网址或应用都由宿主回调负责，库不访问账号、通讯录、定位或网络。

demo 的 “Reply keyboard” 菜单可切紧凑/全高/单次/移除，普通文字按钮会发送消息，联系人按钮先确认虚构名片，位置和投票交给现有附件示例；sample 同样接入输入栏按钮。没有 Telegram 的 answered/closed 持久化、persistent 强制常驻、Web App容器或请求用户/群选择器；宿主负责这些业务策略。

### 内联按钮（inline keyboard）

消息的 `inlineKeyboard` 由宿主提供多行按钮，文字、照片和其它既有气泡共用上游按钮布局：

```java
message.inlineKeyboard = Collections.singletonList(Arrays.asList(
        new TextBubbleMessage.InlineButton("confirm", "Confirm",
                TextBubbleMessage.InlineButton.Type.CALLBACK, false),
        new TextBubbleMessage.InlineButton("website", "Website",
                TextBubbleMessage.InlineButton.Type.URL, false)));
cell.setDelegate(new TextBubbleCell.Delegate() {
    @Override public void onInlineButtonPressed(TextBubbleCell cell,
            TextBubbleMessage message, TextBubbleMessage.InlineButton button) {
        // Host maps button.id to its action or URL, then publishes an updated snapshot.
    }
    @Override public void onInlineButtonLongPressed(TextBubbleCell cell,
            TextBubbleMessage message, TextBubbleMessage.InlineButton button) {
        // Optional host action, such as showing button details.
    }
});
```

按钮 ID 在同一键盘内保持唯一。替换行快照并重新 `setMessage` 可更新文案和 `loading`；支持 CALLBACK、URL、SWITCH_INLINE、COPY 的外观，实际 URL/查询/复制/网络均由宿主处理。按钮为44dp高、48dp行距、15dp文字，背景/选择色使用上游 service 颜色键，加载反馈复用 `LoadingDrawable`。键盘放在气泡下方，不改变正文、媒体、时间和头像的内容位置；`getInlineButtonRect(row, column, out)` 可获取按钮区域，`getBubbleContentHeight()` 不包含键盘高度。

demo 的 “Inline buttons” 菜单定位到文字和照片样例：Confirm 显示本地加载反馈，Website 显示宿主回调，Search here 填入输入框，Copy code 由宿主复制示例代码；长按报告对应按钮 ID。消费端 sample 也展示这些回调。未移植支付/收据/星币、Web App 登录、Telegram 邀请识别或服务端 callback 查询；不提供自动 URL 跳转。按钮的逐项无障碍虚拟节点尚未实现。

### 带缩略图的文件消息

```java
TextBubbleMessage file = TextBubbleMessage.document(id, outgoing, time, state,
        fileName, fileSize, thumbnail, caption);
// Equivalent when updating an existing message:
file.documentThumbnail = thumbnail; // host-provided Drawable, null retains the original file icon
file.mediaState = TextBubbleMessage.MEDIA_DOWNLOAD;
DocumentBubbleCell cell = new DocumentBubbleCell(context, resourcesProvider);
cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
    @Override public void onMediaPressed(TextBubbleCell cell, TextBubbleMessage message) {
        // Host opens the file, starts a download, or cancels it according to mediaState.
    }
    @Override public void onMenuPressed(TextBubbleCell cell, TextBubbleMessage message, float x, float y) {
        // Host opens its file/message actions.
    }
});
cell.setMessage(file, pinnedTop, pinnedBottom);
```

在已有 `DocumentBubbleCell` 内扩展上游 `DOCUMENT_ATTACH_TYPE_DOCUMENT` 的有图分支，没有新建替代组件。缩略图为86dp正方形，居中的状态圆按钮为48dp；无图时保留原44dp按钮。宿主提供 `documentThumbnail` 和下载状态/进度；文件名、大小、后缀、菜单、说明文字、群聊/转发/回复与反应沿用消息布局。旧六参 `document(...)` 可继续使用，新增重载末尾为 `Drawable thumbnail, CharSequence caption`。

宿主负责解码和异步加载，收到缩略图后替换字段并重绑；下载进度可调用 `updateButtonState(animated)`。库不读取文件 URI、不生成 PDF 封面、不下载或打开文件；`getThumbnailRect(RectF)` / `getButtonRect(RectF)` 返回最近布局的缩略图/状态控件区域，宿主 Drawable 的原 bounds 在绘制后恢复。demo 的 ⋮ “File thumbnails” 包含带说明的预览、长文件名下载/取消，以及无图说明；sample 演示同一 AAR API。demo 下载为本地进度模拟，打开动作用回调提示展示；sample 下载按钮切换示例状态。说明文字显示样式和表情，但暂不支持独立的说明链接点击；不包含文件相册分组、剧透预览或缩略图模糊/加载渐变。

## 长按发送预览与本地特效

`org.telegram.ui.MessageSendPreview` 从固定上游 Dialog 搬运，复用现有气泡；文字或录音草稿长按发送默认打开预览。选项保留静音、定时及宿主允许的上线发送。返回/点击空白取消并保留草稿，草稿修改、暂停、销毁会使旧预览失效。`setSendPreviewEnabled(false)` 恢复旧弹出菜单；编辑、慢速、转发或圆视频录制使用既有路径。

```java
// ID belongs to the host. animationDocumentId resolves through the existing local SourceProvider.
enterView.setMessageEffects(Arrays.asList(new MessageEffect(1, "🐥", 90001)));
enterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
    @Override public void onSendTextWithEffect(CharSequence text,
            ArrayList<TLRPC.MessageEntity> entities, boolean notify, int scheduleDate, long effectId) {
        // Store/send using your own backend; 0 means no effect.
    }
    // onSendVoiceWithEffect(RecordedAudio, notify, scheduleDate, effectId) is analogous.
});
enterView.showMessageSendPreview(); // optional programmatic entry
```

新增发送方法默认转调旧 `onSendText` / `onSendVoice`，原宿主实现保持可用。只给分段文本的第一条传效果ID；库不向网络发送数据。`MessageEffect(id, emoticon, animationDocumentId)` 是不可变宿主模型，id 非零且列表内唯一，动画ID也必须非零；源无法加载时回退静态emoji候选。宿主通过 `AnimatedEmojiDrawable.setSourceProvider` 提供本地 JSON/TGS。选择同一效果再次点击取消；全屏动画播放一轮后释放，发送结果交宿主。demo 菜单“Send preview and effects”与真实长按入口均可操作，鸭子动画是现有本地样例，不代表 Telegram 服务端特效素材；sample 提供同样的 AAR 接线。

独立使用时，创建 `MessageSendPreview(context, resourcesProvider)`，设置 `setMessages(List<TextBubbleMessage>)`、`setSendButton(anchor, fillWhenClose, listener)`、可选 `setOptionsView(view)` / `setEffects(list)` 后 `show()`。`getSelectedEffect()` 返回宿主ID，发送后由宿主 `dismiss(true)`，取消 `dismiss(false)`，生命周期退出用 `dismissImmediately()`。窗口关闭期间 `isInteractive()` 为false，宿主异步回调应检查它；实例销毁后不可重用。列表复制容器而不深拷贝消息，宿主应把预览消息作为稳定快照提供。文本、语音及现有图片/文件/相册/贴纸/GIF/圆视频/音乐/联系人/位置/投票复用既有 cell；独立预览不启动音视频播放器。

保留上游350ms EASE_OUT_QUINT开合、250ms Overshoot(0.5)效果选择器，以及原尺寸、颜色key、日夜模糊滤镜。预览隐藏时间/发送状态，并从气泡实际时间坐标定位效果；`TextBubbleCell.setDrawTime` 默认true，正常聊天不受影响。明确限制：没有输入文字逐字变成气泡、相机纹理、超宽列表缩放与发出后追踪气泡的转场；输入自动预览不生成宿主reply/forward头，独立调用可提供完整模型。模糊只捕获宿主Activity，不合成其它窗口；候选复用本地动画而非上游独立首帧缩略文档，不含Premium/Stars/网络特效目录/展开全量选择器。实际软件IME过渡、其它Android版本、平板和复杂RTL/大字体仍需人工检查。

## 包含的组件

| 类别 | 类 |
| --- | --- |
| 未读消息 | `ChatUnreadCell`：原版 40dp 分隔行、主题颜色和宿主未读文案 |
| 服务消息 | `ChatActionCell` / `ServiceMessage`：加入、邀请、改名、置顶，用户与消息回调 |
| 群聊消息 | `TextBubbleMessage.Sender` / `MessageGrouping` / `ChatAvatarDecoration`：原配色姓名、头像列、连续消息及滚动吸底头像 |
| 转发来源 | `TextBubbleMessage.ForwardedFrom` / `ForwardBackground`：普通与无背景媒体来源头、头像、原按压动画、宿主来源回调；贴纸/圆视频旁侧回复 |
| @提及 | 原 MentionsContainerView / MentionCell 普通用户分支，宿主候选查询、异步隔离、光标插入及ID span |
| 表情联想 | 原 SuggestEmojiView 浮层与横向候选，宿主关键词/emoji查询、Unicode及本地动态表情替换 |
| 发送预览与特效 | MessageSendPreview、MessageEffect、ReactionsContainerLayout；文字/语音长按预览、本地JSON/TGS效果及宿主发送回调 |
| 机器人命令菜单 | 原 BotCommandsMenuView / Container，输入栏入口、滚动/下拉关闭、短按宿主发送/长按编辑 |
| 回复键盘 | 原 `ui.bots.BotKeyboardView`，宿主不可变行快照、紧凑/全高/单次模式；原输入栏切换与按钮回调 |
| 内联按钮 | 上游44dp按钮行、类型图标/按压/加载反馈；宿主行模型与短按/长按回调，所有既有气泡共用 |
| 文件缩略图 | 既有 `DocumentBubbleCell`：宿主86dp缩略图、下载状态、说明文字及文件/菜单回调；旧无图布局保留 |
| 投票消息 | `PollBubbleCell` / `TextBubbleMessage.Poll`：单选/多选、结果条形与百分比、宿主提交和结果回调 |
| 位置消息 | `LocationBubbleCell` / `TextBubbleMessage.Location`：宿主地图、纯坐标/地点布局、原图钉与位置回调 |
| 音乐消息 | `MusicBubbleCell` / `TextBubbleMessage.music(...)`：播放/暂停、拖动、下载状态、说明与宿主回调 |
| 联系人消息 | `ContactBubbleCell` / `TextBubbleMessage.Contact`：原名片布局、查看/发消息/添加按钮及宿主回调 |
| 附件面板 | `ChatAttachAlert`、`Attachment`、`AttachmentAccess`：相册、文件、联系人、位置与投票选择及宿主回调 |
| 圆形视频 | `RoundVideoBubbleCell`、`TextBubbleMessage.roundVideo(...)`、上游 `RoundVideoShadow`：圆形预览、时长、未读/下载状态、播放环及宿主回调 |
| 贴纸与 GIF | `StickerBubbleCell` / `GifBubbleCell`、`StickerSet` / `Sticker` / `Gif` / `MediaSource`、`LocalMediaView`；本地 WEBP/TGS/GIF、WEBM 首帧，自动播放及宿主加载回调 |
| 贴纸预览 | `StickersAlert` 五列包预览、`ContentPreviewViewer` 长按/滑动切换及发送/收藏/移除回调 |
| 相册 | `GroupedMediaLayout`、`AlbumBubbleCell`、`TextBubbleMessage.album(...)`：上游比例分组算法，2–10 张本地照片/视频缩略图、共享说明和单个时间、每格状态及回调 |
| 导航 | `BaseFragment`、`ActionBarLayout` / `INavigationLayout`、`DrawerLayoutContainer`、`AdjustPanLayoutHelper`、`EmptyBaseFragment` |
| ActionBar | `ActionBar`、`ActionBarMenu`、`ActionBarMenuItem`（含搜索框）、`ActionBarMenuSubItem`、`ActionBarPopupWindow`、`ActionBarMenuSlider`、`BackDrawable`、`MenuDrawable`、`SimpleTextView`、`FloatingToolbar` / `FloatingActionMode` |
| 弹窗 | `AlertDialog`（`AlertDialogDecor`、`DarkAlertDialog`）、`BottomSheet` |
| 设置项 | `HeaderCell`、`TextCell`、`TextSettingsCell`、`TextCheckCell`、`TextDetailSettingsCell`、`TextInfoPrivacyCell`、`ShadowSectionCell`、`NotificationsCheckCell`、`RadioCell`、`TextRadioCell`、`CheckBoxCell`、`DividerCell`、`EmptyCell` |
| 列表与控件 | `RecyclerListView` + 上游 RecyclerView fork、`Switch`、`CheckBox2`、`CheckBoxSquare`、`RadioButton`、`EditTextBoldCursor`、`AnimatedTextView`、`LinkSpanDrawable`、`RadialProgressView`、`LineProgressView`、`EmptyTextProgressView`、`SizeNotifierFrameLayout`、`LayoutHelper`、`CubicBezierInterpolator` 等 |
| 表情 | `Emoji` + `EmojiData` + `CompoundEmoji`：上游原版，`assets/emoji/` 里的 3586 张 Telegram 表情精灵图（实际约 5.2 MB，`du` 按磁盘块算会显示 14 MB；APK 里不再压缩）按页异步加载，`Emoji.replaceEmoji(...)` 把文字里的 emoji 换成 `Emoji.EmojiSpan`；输入栏、回复 / 编辑顶栏、气泡、`CheckBoxCell` / `TextDetailSettingsCell` 等上游已调用的地方都会显示。精灵图加载完会发 `NotificationCenter.emojiLoaded`，宿主列表要像上游 ChatActivity 那样收到后重绘（`Emoji.invalidateAll(listView)`）；`SharedConfig.useSystemEmoji = true` 可改回系统 emoji |
| 聊天 | `TextBubbleCell` + `TextBubbleMessage`（纯文本气泡：时间、发送中/已发送/已读勾、同侧连续消息合并圆角与尾巴、Telegram 表情；只有 emoji 的消息按上游 TYPE_EMOJIS 放大显示、不画气泡，时间放在小圆角底条上）；媒体气泡（上游 `ChatMessageCell` 对应分支按原数值移植，同样继承 `TextBubbleCell` 的背景、时间、勾和多选）：`VoiceBubbleCell`（语音：`RadialProgress2` 播放 / 暂停按钮、`SeekBarWaveform` 波形可拖动、时长 / 已播放时间、未听圆点；播放由宿主做，宿主更新 `playing` / `audioProgress` / `audioProgressSec` 后调 `updatePlayingMessageProgress()`）、`PhotoBubbleCell`（图片：上游 `getMessageSize` 尺寸规则、同侧连续消息的分组圆角、图片上的时间小胶囊、可选说明文字、可选 `chat_mediaLoaderPhoto` 下载 / 取消 / 进度圆圈；图片是宿主给的 `Drawable`，不联网）、`DocumentBubbleCell`（文件：44dp 圆形 `ICON_FILE` / 下载 / 取消按钮、两行文件名中间省略、“大小 扩展名”、下载中“已下载 / 总大小”、⋮ 图标）；按钮点击和波形拖动通过 `TextBubbleCell.setMediaDelegate(MediaDelegate)` 回调宿主，气泡其它位置照常单击出菜单、长按多选；`TextBubbleCell.addMediaThemeDescriptions(...)` 登记媒体气泡、回复引用、链接和日期分隔用到的颜色 key；**回复引用**：`TextBubbleMessage.replyTo`（`ReplyTo`：名字、一行预览、可选缩略图、原消息 id，`ReplyTo.of(message, name)` 一步生成）在文字 / 语音 / 图片 / 文件气泡顶部画上游 `ChatMessageCell` 的回复块（`ReplyMessageLine` 色条与浅色圆角底、按下水波和回弹、名字用回复色、预览走 Telegram 表情、图片回复带 4dp 圆角缩略图），点按回调 `TextBubbleCell.Delegate.onReplyPressed`，`setHighlightedAnimated()` 播放上游跳转后的高亮闪烁（`highlightProgress` + `chat_selectedBackground`）；**链接**：文字气泡里的网址、@提及、#话题按上游 `MessageObject.addLinks` 识别（可用 `TextBubbleCell.detectLinks` 关掉），用 `chat_messageLinkIn/Out` 着色，按下画上游 `LinkSpanDrawable` / `LinkPath` 高亮，单击 / 长按回调 `Delegate.onLinkPressed` / `onLinkLongPressed`（demo 单击打开网址，长按弹上游的“打开 / 复制”底部菜单）；**气泡里的反应**：`ReactionsLayoutInBubble`（上游同名类移植到纯模型）把 `TextBubbleMessage.reactions` 画成上游的 26dp 反应胶囊——20dp 静态 Telegram 表情 + `CounterView.CounterDrawable` 计数，`chat_in/outReactionButton*` 配色（未选 15.6% 透明底、已选实心），按上游 `measure` 换行排布、4dp 间距，时间放在最后一行末尾放不下就换行；文字 / 语音 / 文件气泡画在气泡内底部，图片画在图片下方的气泡条里（有反应时时间从图片上移到条里，和上游 `shouldDrawTimeOnMedia` 一致），纯 emoji 消息画在表情下方、用壁纸取色的服务底（出站右对齐）；气泡高度随之增加。单击胶囊回调 `Delegate.onReactionPressed`，长按回调 `onReactionLongPressed`（按下有 `ButtonBounce` 回弹，手势不再触发整行单击 / 菜单）；改了 `reactions` 后对同一条消息再 `setMessage`（列表里就是 `notifyItemChanged`）会播放上游的变化动画：新增胶囊从 0.5 倍放大淡入、移除的缩小淡出、其余平移 / 变宽 / 颜色渐变、计数数字上下滚动（250ms，`CubicBezierInterpolator.DEFAULT`）；**日期分隔**：`ChatActionCell`（上游 `ChatActionCell` 的居中服务消息胶囊，`setCustomDate(unix 秒)` 显示 Today / Yesterday / 日期，背景是 `Theme.chat_actionBackgroundPaint`：动态渐变壁纸时取壁纸位图做 shader，其它壁纸取平均色，日夜都跟着壁纸变；`setFloating(true)` 用作滚动时的悬浮日期），`TextBubbleMessage.date` 记消息时间，分隔行由宿主 adapter 插入（见 demo）、`Theme.MessageDrawable`（含渐变/动态渐变出站气泡）、`ChatInputBar`（已弃用的简易输入框，保留作轻量选项）、`ChatBubbleItemAnimator`（新消息从输入栏下方滑入、上方气泡同步上移）、`ChatSounds`（上游的发送 / 接收提示音 `sound_out` / `sound_in`，静音模式下不播放）、聊天背景 `MotionBackgroundDrawable` / `BackgroundGradientDrawable`（由 `SizeNotifierFrameLayout.setBackgroundImage(Theme.getCachedWallpaper(), …)` 绘制，发送时 `switchToNextPosition()` 旋转） |
| 聊天输入栏 | `ChatActivityEnterView`：上游 11.4.2 原版移植，保留原布局、图标和动画——`EditTextCaption`（多行增长、选中文字的格式菜单：引用 / 剧透 / 粗体 / 斜体 / 等宽 / 删除线 / 下划线 / 链接 / 清除格式）、表情↔键盘图标动画（`ChatActivityEnterViewAnimatedIconView`，Lottie）与表情面板（见下行）、附件按钮、`SendButton` 及出现动画、长按发送菜单（定时发送 / 上线时发送 / 静音发送，日期选择器 `AlertsCreator.createScheduleDatePickerDialog`）、编辑模式的完成按钮、频道静音铃铛、慢速模式倒计时；语音 / 圆形视频录制整套界面：`RecordCircle`（Blob/Wave 波纹）、`ControlsView`（上滑锁定、暂停、阅后即焚开关）、`SlideTextView`（左滑取消）、`RecordDot`、`TimerView`、录音预览（`SeekBarWaveform` 波形、播放 / 删除 / 继续录）。宿主可以用 `setRecordButtonVisible(false)` 隐藏语音 / 圆形视频录制按钮（没有文字时那一格留空，有文字时发送按钮照常带动画出现）。另有 `ChatInputLayout`（ChatActivity 根布局里与输入栏相关的测量 / 摆放：表情面板、录音圆圈、键盘高度）、`ChatInputTopPanel`（回复 / 编辑 / 转发 / 链接预览顶栏，即上游 ChatActivity 的 replyLayout） |
| 表情面板 | `EmojiView`：原 pager 的表情/贴纸/GIF 页（宿主快照和发送回调），表情页保留 Telegram 表情网格（最近使用 + 8 个分类，分类标题 `StickerSetNameCell`）、`EmojiTabsStrip` 分类标签（最近使用 + 8 个分类铺满整行，保留 Lottie 图标动画和选中指示；上游可收起的分类胶囊只在后面跟着表情包标签时才有意义，普通表情分类仍使用整行，媒体包另在贴纸页显示；点标签滚动、滚动时选中标签跟随）、滚动时标签栏和底栏收起、长按带肤色的表情弹出 `EmojiColorPickerWindow`（肤色和最近使用由 `Emoji` 保存）、底栏退格（长按连删）；面板高度沿用输入栏记下的键盘高度（`kbd_height`；第一次弹出键盘前，上游固定用 200dp，tgui 改为按屏幕长边的 38% 估算）。面板弹出 / 收起时，`ChatInputLayout` 像上游 ChatActivity 的 `bottomPanelTranslationYChanged` 那样让输入栏和聊天列表跟着面板一起滑动；键盘弹出 / 收起用 tgui 自己的实现（上游 `AdjustPanLayoutHelper` 每帧改整条视图链高度并重绘壁纸，在 Mate 9 上掉帧严重）：点输入框时先按记下的键盘高度把输入栏和聊天列表抬起，窗口真正 resize 时保持屏幕位置、在抬起动画剩下的时间里到位，只改这两个 view 的 `translationY`；动画期间输入栏下方用表情面板底色填充，看起来是键盘区域升起，而不是输入栏悬在半空（以上是 Android 10 及以下）。Android 11+ 用 `WindowInsetsAnimationCompat` 让输入栏和聊天列表逐帧贴着输入法顶边走，表情面板和键盘互换时输入栏保持不动；`DrawerLayoutContainer` 在输入法动画期间先按较大的可用区域布局，结束时再应用新的 inset，避免跟随中的输入栏被裁掉。宿主需要 `androidx.viewpager:viewpager`（发布的 POM 已带上） |
| 对话列表 | `DialogCell` + `DialogItem`（上游 `DialogCell` 的 `buildLayout` / `onDraw` 按原数值移植到纯模型：名字 + 时间、两行 / 三行预览（`SharedConfig.useThreeLinesLayout`）、发送者前缀、草稿、媒体预览配色、正在输入行（6 种状态图标，与消息行交叉淡入）、未读计数（数字变化动画）、@ 提及、置顶图标与置顶底色、静音、认证、私密聊天锁、发送中 / 已发送 / 已读勾（切换动画）、发送失败、在线圆点、分隔线；收到 `emojiLoaded` 自己重绘）、`AvatarDrawable`（上游原版：首字母 + 上游配色，收藏夹 / 归档（Lottie）/ 回复等特殊头像）、`DialogsListView`（列表 + 上游 `DialogsItemAnimator`：删除折叠、移动、新增；`setArchivePullDelegate` 开启上游隐藏归档：`SharedConfig.archiveHidden` 时归档行藏在列表上方，下拉出现 `PullForegroundDrawable` 提示条，超过 0.85 行高松手后收成归档头像，滚走后自动再藏起，惯性滑动停在第一个对话）、`DialogsViewPager`（上游 `DialogsActivity` 的两个 ViewPage + 左右拖动翻文件夹，`FilterTabsView.selectTabWithId(id, progress)` 跟随指示条，点标签也滑动切换；宿主在 `onBindPage` 里为文件夹填列表）、`DialogsSwipeController`（上游左滑：归档滑出；归档行左滑“隐藏 / 置顶”归档；置顶 / 已读 / 静音 / 删除回弹并回调，阈值 0.45、Lottie 图标、圆形揭露底色；多选时拖动置顶行排序）、`DialogsActionMode`（长按多选：计数 `NumberTextView`、54dp 图标纵向弹出、ActionBar 渐变成白底、返回键转成 ×、⋮ 溢出菜单；头像上的 `CheckBox2` 勾选）、`FilterTabsView`（上游文件夹标签原版：指示条动画、计数、编辑拖动）、`DialogsFloatingButton`（写消息圆形按钮，下滑隐藏 / 上拉出现）、`DialogsSearchController` + `DialogsSearchView`（上游对话搜索：放大镜展开搜索框，文件夹标签淡出收起、写消息按钮隐藏，列表缩到 0.9 淡出、搜索页从 1.05 淡入，有最近记录时白色搜索栏从放大镜处圆形展开；空查询显示 `HintDialogCell` 头像横排和带 “Clear” 的 Recent 分组，有查询显示 `GraySectionCell` 分组下的 `ProfileSearchCell`（名字高亮）和消息结果 `DialogCell`（匹配文字高亮），没有结果显示上游样式的空状态；结果由宿主异步回调）、`DrawerMenuView` + `DrawerProfileCell` + `DrawerActionCell`（侧边抽屉：头像、名字、电话、日 / 夜切换太阳月亮 Lottie → `TelegramUI.toggleDayNight`，菜单项由宿主提供）。用法见 demo 的 `DemoDialogsFragment` |
| 个人资料 | `ProfileFragment`（上游 `ProfileActivity` 的用户资料页：可折叠头部——头像 / 名字 / 状态随滚动缩放并收进 ActionBar，下拉把头像展开成全宽正方形、名字和状态落在渐变上，松手吸附；写消息浮动按钮的显示 / 隐藏动画；信息行用 `TextDetailCell`、`NotificationsCheckCell`、`TextCell`、红色操作行、`ShadowSectionCell`；⋮ 菜单项和点击回调由宿主提供；滑动返回和日夜切换与其它页面一致） |
| 聊天页基类 | `ChatFragmentBase`：复用既有列表/输入/壁纸/主题与生命周期，宿主提供adapter和输入回调；demo/sample接入，原直接组装仍可用 |
| 媒体查看器 | `MediaViewer`（简易全屏图片 / 视频查看器，不是 `PhotoViewer` 移植，只借用上游的外观和手势数值：从气泡图片位置放大打开、缩回关闭（200ms DecelerateInterpolator），下拉 30dp 后跟手、超过 1/6 屏高关闭、背景按 `max(127, 255 × (1 − |dy| / (宽/4)))` 变淡；双指缩放和双击放大到 3 倍（以手指为中心）、放大后拖动带边界回弹；左右翻页（超过 1/3 屏宽或 650dp/s，页间 15dp，250ms DecelerateInterpolator(1.5)）；0x7f000000 顶栏（返回、发送者、日期和“n of m”）和底部说明文字，单击显示 / 隐藏；搬入上游 `GroupedPhotosListView`，相册缩略图点击/拖动与翻页同步，分享/保存/删除由宿主按页授权并回调；视频用系统 `MediaPlayer` + `TextureView`，64dp 播放按钮、播放 / 暂停、可拖动进度条和“当前 / 总时长”、上游倍速滑条/预设与观看静音；Android 9 到 16 边到边，按系统栏和刘海留出顶栏 / 底栏） |
| 消息菜单与多选 | `MessageContextMenu`：上游 `ChatActivity.createMenu()` 的消息菜单按原数值移植到纯模型——全屏 0.2 黑色遮罩（`dimBehindView`，EASE_OUT_QUINT 320ms），被长按的气泡画在遮罩之上（裁到列表顶部以下）；`ActionBarPopupWindow` + `ActionBarMenuSubItem` 行（`popup_fixed_alert` 背景、200dp 最小宽度、220ms 收起），位置按上游算法以触点为锚、左右 / 上下夹在屏幕内；上方是 `ReactionsContainerLayout` 样式的反应条（药丸 + 阴影 + 两个小气泡圆点，250ms `OvershootInterpolator(0.5)` 从右向左展开，菜单随之放大淡入，表情按上游延迟依次弹出），表情是 `Emoji` 绘制的静态 Telegram 表情（默认 👍 👎 ❤ 🔥 🥰 👏）。用法：`new MessageContextMenu(context, resourcesProvider).addItem(icon, text, [destructive,] onClick)…setReactions(…).setDelegate(…).show(cell, touchX, touchY)`；颜色每次打开时按当前主题取，日夜都跟随。`setSelectedReactions(message)`（或传 emoji 集合）把已选的反应在反应条里画成上游的样子（`key_listSelector` 圆底、表情缩到 0.76）。和上游 ChatActivity 一样：**单击**消息打开这个菜单，**长按**进入多选；消息列表要 `setInstantClick(true)` + `setDisableHighlightState(true)`（不显示列表点按水波）。多选的单元格部分在 `TextBubbleCell.setCheckBoxVisible / setChecked`（上游 `ChatMessageCell`：21dp `CheckBoxBase` 从左侧滑入、来信气泡右移 35dp、200ms EASE_OUT / EASE_IN，选中时整行 `chat_selectedBackground` 高亮、气泡换成 Selected 颜色、时间用 `*TimeSelectedText`）；ActionMode 本身由宿主搭（demo 的 `DemoChatFragment` 照搬 `createActionMode` / `createBottomMessagesActionButtons`：数量 `NumberTextView`、编辑（单选自己的消息时）/ 复制 / 转发 / 删除图标及 250ms 纵向弹出、返回键变 ×、输入栏换成底部“回复 / 转发”栏，单击切换选中、长按后拖动连选 `RecyclerListView.startMultiselect`）。demo 菜单：回复、复制、编辑（仅自己的消息）、删除（带列表动画）、点反应条里的表情给消息加 / 换 / 取消反应（`toggleReaction` + `notifyItemChanged`，气泡里的胶囊带动画变化）；气泡里的胶囊单击同样切换，长按弹出计数提示 |
| 撤销提示 | `UndoView`：归档/删除（单项和批量）、5 秒期限、删除倒计时圆环、归档动画，apply/undo 由宿主回调 |
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
| `PhotoViewer` | 不移植（2 万行，含加载器、编辑、PiP、ExoPlayer）；改写成简易的 `MediaViewer`，见上表 |
| `RadialProgress2` | 上游原版，去掉 `ImageReceiver` 覆盖图（音乐封面用的 `setImageOverlay`） |
| `ChatActivityEnterView` 的业务边界 | 构造参数从 `ChatActivity` 换成 `BaseFragment`；`ChatActivityEnterViewDelegate` 全部改成 default 方法，并新增宿主回调：`onSendText(text, entities, notify, scheduleDate)`、`onSendVoice(RecordedAudio, …)`、`onEditDone`、`onSendContent`（键盘发来的 GIF / 贴纸）、`onImagePasted`、`onSilentChanged`、`onRecordTypingStatus`、`onTopViewTranslationChanged`、`canScheduleMessage` / `isInScheduleMode` / `isSelfChat` / `canSendWhenOnline`、`roundVideoNeedsCameraPermission`、`onEmojiPanelCreated`、`onSendSticker` / `onSendGif` / `onStickerSetRequested` / `onStickerFavoriteChanged` / `onGifRemoved`；聊天权限 / 频道 / 圆形视频 / 慢速模式用 `setChatPermissions()`、`setChannel()`、`setRoundVideoEnabled()`、`setSlowModeTimer()` 设置；回复目标是宿主的任意对象（`setReplyingMessageObject(Object)`），编辑用 `EditingMessage(id, text, entities, caption)`。删掉的业务分支：机器人键盘的发送/持久化业务和命令菜单的机器人资料/执行业务（UI已恢复） / WebView 容器、以…身份发送、礼物按钮、生日提示、Premium / 动态表情检查、快捷回复与商务链接、网页预览、发送特效的网络目录与账号策略（MessageSendPreview UI已恢复）、草稿同步 |
| `MediaController` | 只保留语音消息录制 / 预览接口，交给可替换的 `MediaController.Recorder`；默认实现 `DefaultRecorder` 用 `MediaRecorder`（AAC .m4a，按 `getMaxAmplitude()` 出波纹和 5-bit 波形，打包格式与上游 native `set_bits` 相同）+ `MediaPlayer` 试听。宿主可换成自己的 Opus 编码器；录制状态仍通过上游的 `NotificationCenter` 事件（`recordStarted` / `recordProgressChanged` / `recordStopped` / `audioDidSent`…）回到输入栏，新增 `voiceRecordedForSend` 表示松手直接发送。圆形视频的相机（上游 `InstantCameraView`）由宿主在 `needStartRecordVideo` 里实现 |
| `TLRPC` / `MediaDataController` | `TLRPC` 只剩消息实体（粗体、链接、自定义表情 id…）的纯数据类；`MediaDataController` 只保留文字实体相关的静态方法（`getEntities`、`addStyleToText`、`addTextStyleRuns`…，原样复制） |
| `ProfileActivity` | 只移植了用户资料页的头部（`needLayout` / `setAvatarExpandProgress` / `needLayoutText` / `TopView` / `OverlaysView` 的数值和动画）、写消息按钮和信息行的单元格，改写成 `ProfileFragment` + 纯模型；去掉头像相册翻页（`ProfileGalleryView`，展开的就是那张 `Profile.photo`）、`SharedMediaLayout` 共享媒体分页（换成 `TextCell` 计数行）、群组 / 频道资料、成员列表、Stories、表情状态、Premium、个人颜色背景与图案、二维码 / 搜索 / 通话按钮、从聊天头像飞入的打开动画、修改头像、`AboutLinkCell`（简介用多行 `TextDetailCell`）、`UndoView` |
| `EmojiView` | 恢复宿主数据驱动的贴纸/GIF 页与 ContentPreviewViewer 长按；账号、网络、精选包、自定义 emoji 包、Premium 和服务器搜索仍裁剪。普通表情分类沿用 EmojiTabsStrip，媒体包独立标签。完整拖拽展开仍未恢复 |
| `AnimatedEmojiSpan` | 保留上游字体尺寸、缩放和行内位置，直接在 ReplacementSpan 中画本地 rlottie；layout 分组管理父 View 的绑定和释放，实体 ID 可往返 |
| `AlertsCreator` / `CaptionContainerView` / `VideoEditedInfo` | 分别只保留定时发送日期选择器、阅后即焚图标 `PeriodDrawable`、圆形视频裁剪字段 |
| `RLottieDrawable` / `RLottieImageView` | 上游原版，native 库换成 `libtguilottie.so`；`addParentView` 改收 `View`（无 `ImageReceiver`），`RLottieImageView` 去掉了按 `TLRPC.Document` 加载贴纸的分支。`BitmapsCache` 缓存目录改为 `cacheDir/acache` |
| `Emoji` | 上游原版；只把 `replaceWithRestrictedEmoji`（服务器的 RestrictedEmoji 动态贴纸集）换成普通 `replaceEmoji`，`useSystemEmoji` / `allowBigEmoji` 由 `SharedConfig` 的静态字段提供 |
| `AnimatedEmojiDrawable` | 宿主以 documentId 提供本地 JSON / gzip TGS；替换 ImageReceiver、账号和网络加载；解压至私有缓存，最后解绑释放原生动画，保留 Swap API |
| `BackupImageView` | 改成本地 Drawable/Bitmap 视图，支持圆角，不再依赖 `ImageReceiver` 和网络加载 |
| `EditTextEffects` | 保留裁剪修正，接入本地自定义 emoji；剧透粒子和引用块仍未移植 |
| `URLSpanReplacement` | 点击后用 `ACTION_VIEW` 打开链接 |
| `LaunchActivity` | 换成 `TelegramUI.Host` / `TelegramActivity` |

删掉的上游功能：完整聊天页（`createChatResources` 只保留气泡、文字、时间、勾和输入框阴影部分）、壁纸文件 / 云端壁纸 / 自定义壁纸、云端主题（`TL_theme`、主题上传）、Stories、Bot WebView 标签栏、群组通话画中画、`Bulletin` 提示条、剧透效果、ActionBar 搜索里的筛选标签、`BackButtonMenu`（长按返回的历史列表）、`TextCell` 的 sticker / 等级锁 / NEW 标记，以及 `CheckBoxCell.setUserOrChat`（换成了 `setUser(CharSequence, Drawable)`）。

## 已知限制

主题 `.attheme` 解析同时接受 LF / CRLF，壁纸二进制偏移按原始字节计算；`.gitattributes` 固定新检出的主题资源为 LF。Windows 上此前出现的夜间文字透明/缺失已修复，设备覆盖打包的 night/darkblue 与两种换行格式。

- 聊天气泡：有文字、语音、图片（单张，可带 `videoUri` 当视频显示：缩略图 + 播放按钮 + 时长）、文件、相册、圆形视频、贴纸、GIF、联系人、音乐、位置和投票；已有群聊头像、名字和转发来源；文件已支持宿主缩略图和说明；尚缺文字选择；图片没有模糊预览缩略图和剧透，语音没有转文字按钮和上游播放时的音量波纹（`AudioVisualizerDrawable`）；多选的整行高亮是简单淡入（上游 `MessageBackgroundDrawable` 从触点圆形展开，浅色主题用壁纸取色的 service 画笔）；表情反应只有静态表情：气泡里的反应没有动态 / 自定义表情、付费星星反应、最近反应者头像、未读反应标记，按模型顺序显示（上游私聊会把已选的排前面），气泡本身的高度变化不做动画（胶囊有动画）；长按菜单的反应条没有动态 / 自定义反应、展开全部反应的按钮、长按放大和飞入气泡的特效；气泡里的剧透（spoiler）实体没有粒子效果（剧透效果在第一阶段已剥离）
- 查看器 `MediaViewer`：只是简易版——已有相册缩略图/分组翻页和分享/保存/删除宿主菜单，已有视频倍速和静音，仍没有转发菜单、图片编辑、画中画、视频时间轴缩略图预览、横屏全屏按钮；视频用系统 `MediaPlayer`（格式取决于设备解码器，没有缓冲进度、不循环），播放中不能连续翻页预加载；图片直接画宿主给的 `Drawable`（大图要宿主自己先缩放）；打开 / 关闭动画不处理被 ActionBar 或输入栏遮住的那部分（上游会裁剪），起点用整张图片的位置；平板没有专门布局。缩略图仅用静态宿主Drawable，无网络加载或视频抽帧；沿用本查看器“说明/缩略图/视频控制”底部组合，照片未额外留上游空的48dp工具栏位置
- 输入栏：附件按钮由宿主回调打开 `ChatAttachAlert`；粘贴 / 键盘提交的图片只回调宿主（没有 `PhotoViewer`）；已有宿主驱动的 @提及、表情联想、回复键盘和命令菜单；没有话题、命令自动补全和 WebView 容器；圆形视频需要宿主自己的相机实现；默认录音器输出 AAC 而不是 Telegram 的 Opus/OGG

- 只支持主题自带的颜色/渐变壁纸和默认图案壁纸，不支持壁纸文件和视差（motion）效果；各强调色原本的不同图案都显示成同一个 `default_pattern`
- Lottie 只带了库内引用到的 `R.raw` JSON（对话列表的归档 / 滑动动作图标、骰子 / 飞镖等），外加上游 APK 自带的 46 个大幅插画：11 只 Utya 小鸭子 `R.raw.utyan_*`、两步验证猴子 `tsv_setup_monkey_*` / `tsv_monkey_close`、设置和邮箱引导（`tsv_setup_*`、`email_*`）、钱包 `wallet_*`、`qr_login`、`filter_new` 等，以及一批白色线条图标（`passcode_lock`、`stats`、`fragment*`、`boosts` 等，上游画在彩色圆形上，可配合 `AlertDialog.Builder.setTopAnimationIsNew(true)` 使用），demo 的 "Lottie illustrations" 页分组展示全部动画。客户端里另一些鸭子（搜索无结果等空状态）和动态表情、贴纸是运行时从服务器贴纸包下载的，不在 APK 里；其它动画 JSON 由宿主放进自己的 `res/raw`，再 `setTopAnimation(R.raw.xxx, …)` / `new RLottieDrawable(R.raw.xxx, …)` 使用
- rlottie 的 pixman NEON 汇编（`pixman-arm*-neon-asm.S`）需要 GNU as，新 NDK 只有 clang 汇编器，所以所有 ABI 都走 rlottie 的 C 填充路径，纯色填充比上游略慢
- 不能播放 Telegram 贴纸 / TGS 文件的网络加载（没有 `ImageReceiver` / `FileLoader`）；本地文件可以用 `new RLottieDrawable(File, w, h, cacheOptions, limitFps)`
- 字符串只带了库内实际用到的 49 条英文，加上游自带的部分翻译（ar / de / es / it / ko / nl / pt-BR）；其它语言需要宿主覆盖资源
- demo 在 Android 9 真机（Huawei MHA-AL00）上完整验证过；Android 16（OnePlus，targetSdk 36 强制边到边）上验证过聊天背景、气泡、Telegram 表情和 `ChatActivityEnterView` 的显示，表情面板（打开、日夜切换、键盘↔面板切换、返回键收起）两台都验证过；这台 OnePlus 的手势导航条高度为 0，所以没能在非零导航栏高度下验证面板位置；输入栏的录音交互还没在 Android 15+ 真机上逐项验证；平板布局没验证
- 对话列表：隐藏归档没有 Stories 联动（上游下拉时会先展开 Stories 栏）、没有“已隐藏归档”撤销条和归档长按菜单；`archiveHidden` 只存在内存里，由宿主持久化；翻页手势与上游一样只在“滑动切换文件夹”设置下生效（demo 的 Swipe action 菜单第一项），此时普通行不能左滑，归档行仍可左滑；锁定文件夹（Premium 上限）的抖动提示未移植；搜索只有对话页（没有媒体 / 链接 / 文件等标签页、搜索筛选、长按删除单条最近记录），宿主负责最近记录的持久化；没有 Stories、论坛话题、消息缩略图、平板右侧对话预览；多账号只保留了抽屉里的箭头（`setAccountsArrowVisible`），没有账号列表
- 个人资料页：只有一张头像（没有相册翻页和指示条）、没有共享媒体分页、没有从聊天页头像飞入的打开动画；只做了用户资料（没有群组 / 频道）；横屏和平板不能下拉展开头像（与上游一致）
- `Theme` 里仍留着个人资料相关的 paint/drawable 字段，没用到但可以安全忽略；后续可以继续瘦身

## 路线图（分阶段恢复，均不放弃）

1. **阶段 A（已完成）**：`ChatActivityEnterView` 原版输入栏——输入、格式菜单、表情↔键盘切换（占位面板）、发送动画与长按菜单、语音录制（按住 / 左滑取消 / 上滑锁定 / 暂停预览 / 播放）、圆形视频录制界面（相机由宿主提供）、回复 / 编辑顶栏
2. **阶段 B1（已完成）：Telegram 表情渲染**——上游 `Emoji` / `EmojiData` / `CompoundEmoji` 和全部 emoji 精灵图，输入栏、气泡（含纯 emoji 大号显示）和设置单元格里显示 Telegram 风格表情；输入栏可隐藏录音按钮
3. **阶段 B2（已完成）：表情面板**——上游 `EmojiView` 的表情页（分类标签、最近使用、肤色选择、退格），替换占位面板，跟随日夜主题切换
4. **阶段 B3**：表情搜索、动态 / 自定义表情页、贴纸 / GIF 页（与下面 C、D 合并推进）
5. **阶段 C（本地渲染已完成）：动态表情 / 自定义表情**——`AnimatedEmojiDrawable` / `AnimatedEmojiSpan` 渲染宿主本地 JSON / TGS，输入栏与文字气泡支持混排及实体往返；自定义表情包面板和动态反应仍待后续
6. **贴纸和 GIF（新任务阶段 A）**——本地面板、StickersAlert、ContentPreviewViewer 与气泡已接入；TrendingStickersLayout、服务器搜索/安装管理未纳入。新任务 B–F 的逐阶段状态见 docs/PROGRESS.md
7. **对话列表（已完成）**：`DialogCell`、滑动动作、多选、列表动画、文件夹标签、抽屉、写消息按钮；下拉显示隐藏归档（`PullForegroundDrawable`）、文件夹左右翻页（`DialogsViewPager`）也已完成；对话搜索（`DialogsSearchController`）也已完成；`UndoView` 归档/删除撤销已提供，隐藏归档等其它提示尚未移植
8. **消息菜单与多选（已完成静态版）**：`MessageContextMenu`（单击：遮罩、浮起气泡、弹出菜单、静态表情反应条），长按多选（复选框、ActionMode、底部回复 / 转发栏、拖动连选）；气泡里的反应胶囊（`ReactionsLayoutInBubble`，静态表情 + 计数动画）已完成；后续：动态反应（上游 lottie 选中 / 飞入动画、反应者头像、展开全部反应、自定义表情反应），依赖阶段 C
9. **媒体气泡（已完成第一批）**：语音、图片、文件；回复引用、链接、日期分隔条、气泡里的反应已完成；视频缩略图 / 简易查看器、照片视频相册及普通圆形视频已提供；更高阶媒体能力见各组件限制
10. 输入增强：`SuggestEmojiView`、提及列表、机器人回复键盘和命令菜单已接入；`MessageSendPreview`（长按发送预览与本地特效）已接入，体验差异见上文

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
