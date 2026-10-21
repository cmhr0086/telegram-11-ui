# tgui 外部接入测试记录（11.4.2-ui2）

这个目录是一个**独立的 Gradle 工程**（自己的 `settings.gradle.kts`，不在根构建里），只通过
`mavenLocal()` + `implementation("org.telegram.ui:tgui:11.4.2-ui2")` 引用库，包名 `com.example.tguihost`。
按“新手只看 README / CHANGELOG（加上 README 指向的 demo 源码）”的方式搭了：Application 初始化、
10 条对话的聊天列表（`DialogCell` + `DialogItem`、左滑静音、`FilterTabsView` 三个文件夹标签）、
点进聊天页（壁纸、`TextBubbleCell` 气泡、`ChatActivityEnterView` 发送 / 表情面板、单击 `MessageContextMenu`、长按多选）、日夜切换。

下面每一条都是这次实际跑出来或量出来的，不是推测。没测到的东西单列在最后。

## 怎么跑

```bash
# 仓库根目录：先发布库
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :tgui:publishToMavenLocal
# 然后在 samples/host-app 里
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:installDebug
```

验证结果（Huawei MHA-AL00，Android 9，USB）：debug 和 R8 混淆后的 release 都能装上运行；列表、文件夹标签切换、左滑静音、
进聊天、输入并发送、表情面板、单击菜单（反应条 + 复制 / 删除）、长按多选（复选框 + 计数 + 删除）、列表页和聊天页的日夜切换都试过，
logcat 的 crash 缓冲区为空。小米 10.0.0.139 测试时已经掉线（adb devices 里没有），跳过。

## 摩擦点（按严重程度排序）

### 1. ~~【阻断】发布出来的 AAR 不能被任何宿主 dex：缺 5 个 `@IntDef` 注解类~~ 【已修复】

- **现象**：宿主 `assembleDebug` 在 `mergeExtDexDebug` 失败：
  `D8: Compilation of classes androidx.recyclerview.widget.RecyclerView, … requires its nest mates androidx.recyclerview.widget.RecyclerView$State$LayoutState (unavailable) to be on program or class path.`
- **原因**：AAR 的 `classes.jar` 里少了 5 个类：`RecyclerView$State$LayoutState`、`FastScroller$State`、`FastScroller$DragState`、
  `FastScroller$AnimationState`、`EmojiView$Type`。AGP 的 `extractReleaseAnnotations` 会把**非 public** 的 `@IntDef` 类型
  记进 `typedefs.txt`（导出到 `annotations.zip`），打 AAR 时从 `classes.jar` 删掉——和 `@Retention` 无关，改成 `CLASS` 实测照删不误。
  但库用 Java 17 编译，外层类的 `NestMembers` 属性仍指向它们，D8 校验 nest 时就报错。官方 androidx 是 Java 8 目标，没有 nest 属性，所以不出事。
  demo 用的是 `project(":tgui")`，不经过 AAR，所以 CI 之前发现不了。public 的嵌套 typedef（`RecyclerView.Orientation` 等）不会被删，没有问题。
- **修法**：去掉这 5 处的 `@IntDef`（注解类型和常量保留，只少了 lint 对取值的检查），`typedefs.txt` 变为空，5 个类都回到 AAR 里。
  本工程原来的绕过（`app/libs/tgui-stripped-typedefs.jar` + `android.useFullClasspathForDexingTransform=true`）已删除，debug 和 R8 release 都能直接构建。
- **防回归**：`.github/workflows/build.yml` 加了一步，先 `publishToMavenLocal` 再构建本工程的 debug + release。
  以后如果要给库里加 `@IntDef`，要么做成 public，要么别嵌套在类里。

### 2. 【高】库自带 RecyclerView fork，和 Material Components 等常见依赖直接冲突

- **现象**：在宿主里加一行 `implementation("com.google.android.material:material:1.12.0")`，构建失败，
  `Duplicate class androidx.recyclerview.widget.AdapterHelper found in modules recyclerview-1.1.0.aar … and tgui-11.4.2-ui2.aar …`，一共 **175 个** 重复类。
  只加 `androidx.recyclerview:recyclerview:1.3.2` 也一样。README 只写了“宿主不要再引入官方 recyclerview”，但 Material（以及 preference、viewpager2、paging 等）是**传递**引入的，宿主很难避免。
- **原因**：fork 用的是和官方完全相同的包名 `androidx.recyclerview.widget`。
- **宿主绕过**：`implementation("com.google.android.material:material:1.12.0") { exclude(group = "androidx.recyclerview") }` 可以编过（debug 和 release 都成功，APK 大 1.8 MB），
  但运行时 Material 里用 RecyclerView 的组件会绑到 fork 上，API 是否兼容**没验证**。
- **建议**：把 fork 改到自己的包（例如 `org.telegram.ui.recyclerview`，上游自己也只是复制了源码），这样能和官方 RecyclerView 共存；
  短期至少在 README 里写明 exclude 写法和风险。

### 3. 【高】宿主的 `R` 拿不到库资源；namespace 用 `org.telegram.messenger` 的宿主直接合并失败

- **现象 A**：照 README 示例写 `R.drawable.msg_copy`（宿主包下的 `R`），编译报 `cannot find symbol … variable drawable / location: class R`。
  AGP 8+ 新工程默认 `android.nonTransitiveRClass=true`，宿主自己的 `R` 不含库资源。要改成 `import org.telegram.messenger.R;`。README 里没提。
  另外库自己的 `gradle.properties` 设了 `nonTransitiveRClass=false`，demo 能直接用，外部宿主则不行，两边行为不一致。
- **现象 B**：把宿主 namespace 设成 `org.telegram.messenger`（任何 Telegram 分支 App 都是这个包），manifest 合并直接失败：
  `Namespace 'org.telegram.messenger' is used in multiple modules and/or libraries: AndroidManifest.xml, org.telegram.ui:tgui:11.4.2-ui2.`
  不打开 `nonTransitiveRClass=false` 时，宿主的 `org.telegram.messenger.R` 还会遮住库的 R，编译时就找不到 `R.drawable.msg_theme`。
- **原因**：库 namespace 是 `org.telegram.messenger`，类也都在上游原包名里（`org.telegram.ui.*`、`org.telegram.messenger.*`、`androidx.recyclerview.widget.*`）。
- **建议**：README 的示例代码统一写 `org.telegram.messenger.R`；长期把 namespace 换成独立的（例如 `org.telegram.tgui`），
  库内部用 `import org.telegram.tgui.R`（这样 Telegram 分支也能用）。类包名能否整体迁移另议，但至少 namespace 要避开 `org.telegram.messenger`。

### 4. 【中】`TelegramActivity` 启动时会调用根 Fragment 的 `createView()` 两次

- **现象**：我第一版在 `createView()` 里填对话数据，结果文件夹标签的未读计数全是双倍（All 6 / Unread 6，实际应为 3）。加日志看到：
  第一次来自 `TelegramActivity.onCreate → ActionBarLayout.addFragmentToStack → attachView`，第二次来自
  `TelegramActivity.onCreate → rebuildFragments → rebuildAllFragmentViews → showLastFragment → bringToFront`。
- **原因**：`TelegramActivity.onCreate` 里先 `addFragmentToStack` 再 `rebuildFragments`，根页面的视图被建了两次。demo 每次 `fillDialogs` 前会先清空，所以看不出来。
- **宿主绕过**：数据初始化放到 `onFragmentCreate()`（本工程已这样改）。
- **建议**：`TelegramActivity.onCreate` 去掉多余的 `rebuildFragments`（或只在需要时重建）；在 README 写清楚“`createView()` 可能被多次调用，不要在里面初始化数据”（上游也是这样的约定）。

### 5. 【中】`ChatActivityEnterViewDelegate.hasScheduledMessages()` 默认返回 `true`

- **现象**：只实现了 `onSendText` 的宿主，发出第一条消息后，输入栏右侧多出一个带红点的“定时消息”日历按钮，点了也没东西。
- **原因**：`ChatActivityEnterView.java` 里 `default boolean hasScheduledMessages() { return true; }`（其它 default 大多是中性值；52 个 default 方法里这是不中性的一个）。
- **建议**：默认改成 `false`。这个接口已经全部是 default 方法了，这点很好，只实现 `onSendText` 就能用，但默认值要逐个检查成“什么都不显示”。

### 6. 【中】聊天页不写 `getThemeDescriptions()` 就不跟着日夜切换

- **现象**：在聊天页点日夜切换，聊天页本身（ActionBar、壁纸、气泡、输入栏）**完全不变**；退回列表才发现主题已经切换了。
  补上 `getThemeDescriptions()`（ActionBar 三项 + 聊天颜色 key 的重绘回调 + `enterView.getThemeDescriptions()`）
  和 `NotificationCenter.didSetNewWallpapper` 监听（重设 `setBackgroundImage`）之后才正常。
- **原因**：库组件不会自己监听主题变化，全靠宿主页面登记。README 只说“登记的颜色会做动画”，没说**不登记就不刷新**，也没列出聊天页要登记哪些 key。
- **建议**：给 `ChatInputLayout` / `TextBubbleCell` 提供一个现成的 `ChatThemeHelper.getThemeDescriptions(actionBar, listView, contentView, enterView)`，并由 `ChatInputLayout` 自己监听 `didSetNewWallpapper`。

### 7. 【中】聊天页的搭建步骤全靠读 demo，README 没有最小示例

我照着 demo 的 `DemoChatFragment`（871 行）抄出最小聊天页，`ChatFragment.java` 仍有 369 行。必须做、但 README 没写的步骤：
- `actionBar.setAddToContainer(false)`，再手动 `contentView.addView(actionBar)` + `contentView.setActionBar(actionBar)`；
- `new ChatInputLayout(context, parentLayout)` → `setContentView(listView)` → `setEnterView(enterView)`，并且添加顺序有讲究；
- 列表的顶部 padding 要自己算 `ActionBar.getCurrentActionBarHeight() + statusBarHeight + dp(4)`；
- `onResume` / `onPause` 要转发给 `contentView` 和 `enterView`，`onFragmentDestroy` 要调 `enterView.onDestroy()`；
- 监听 `NotificationCenter.emojiLoaded` 后 `Emoji.invalidateAll(listView)`（README 提了一句）；
- 多选的 ActionMode（计数 `NumberTextView`、按钮、`showActionMode` / `hideActionMode`、逐行 `setCheckBoxVisible` / `setChecked`）全部由宿主实现，最小版约 60 行。
- **建议**：提供一个可继承的 `BaseChatFragment`（或 `ChatScreen` 组合类），内置上面的布局、生命周期转发、主题登记、多选 ActionMode，宿主只给消息列表和回调；README 放一个 30 行以内的最小示例。

### 8. 【中】聊天列表：`FilterTabsViewDelegate` 有 9 个抽象方法，标签栏挂法也没写

- **现象**：只想要“点标签切换列表”，也得实现 9 个方法（`onPageSelected`、`onPageScrolled`、`onSamePageSelected`、`getTabCounter`、`didSelectTab`、`isTabMenuVisible`、`onDeletePressed`、`onPageReorder`、`canPerformActions`），其中 6 个写空实现或固定返回值。
  还要知道先 `setDelegate` 再 `addTab`（`addTab` 内部会通过 delegate 算计数，demo 里有注释），之后调 `finishAddingTabs`、`actionBar.setExtraHeight(dp(44))`、`actionBar.addView(tabs, …, Gravity.BOTTOM)`，这些 README 都没写。
- 对比：`DialogsSwipeController.Delegate` 只有 `getSwipeAction` 是必须的，其它都是 default，好用很多（我一开始也照 demo 全写了，后来删到 2 个方法）。
- **建议**：`FilterTabsViewDelegate` 的非必要方法改成 default；加一个 `FilterTabsView.attachTo(ActionBar)` 帮手。

### 9. 【低】文档里没有 `DialogItem` / `TextBubbleMessage` 的字段说明

- **现象**：我按直觉写了 `getDialog().name`，编译报错，字段其实叫 `title`。只能去 sources jar 或 demo 里翻。
- **建议**：README 给 `DialogItem` 列一张字段表（`title`、`message`、`senderName`、`time`、`unreadCount`、`muted`、`pinned`、`sentState`…）。

### 10. 【低】APK 体积：最小宿主 release 约 10.9 MB，基本都来自 tgui

实测（同一宿主，`isMinifyEnabled = true`）：

| 配置 | APK |
| --- | --- |
| 不依赖 tgui 的空 App（release） | 22 KB |
| 本示例 debug | 14.2 MB |
| 本示例 release（R8） | **10.9 MB** |
| + `isShrinkResources = true` | 9.8 MB |
| + 只留 `arm64-v8a` | 8.6 MB |

release 里的大头：`assets/emoji` 5.2 MB（PNG，不再压缩，资源压缩也删不掉）、`res` 1.3 MB（含 500 KB 的 `default_pattern.svg` 和 46 个 Lottie 插图）、`assets/fonts` 0.8 MB、四个 ABI 的 `libtguilottie.so` 合计 1.45 MB、dex 0.6 MB。
- **建议**：README 写明体积和 `abiFilters` / `isShrinkResources` 的效果；把 46 个大幅 Lottie 插图拆成可选的 `tgui-illustrations` 模块；emoji 精灵图考虑 WebP 或按需下载。
- 顺带：README 原来写 emoji 精灵图“约 14 MB”，那是 `du` 按磁盘块算的（3599 个小文件），实际字节数是 5.2 MB，已在 README 里改正。

### 11. 【低】发布命令在没有 `local.properties` 的检出里直接失败

- **现象**：新 worktree 里跑 README 的 `./gradlew :tgui:publishToMavenLocal`，报 `SDK location not found`。要设 `ANDROID_HOME` 或建 `local.properties`。README 的“构建”一节只写了 NDK / CMake。
- **建议**：README 加一句 SDK 路径的要求。

## 顺利的部分（也是实测）

- POM 的传递依赖是对的：宿主只写一行 `implementation`，androidx core / annotation / dynamicanimation / customview / collection / interpolator / viewpager 都自动带进来，没有缺依赖的运行时崩溃。
- `consumer-rules.pro` 够用：宿主开 R8（`proguard-android-optimize.txt`）后，列表、聊天、表情面板（含 Lottie 图标）、消息菜单都正常，没有 R8 警告。
- Manifest：库只带 `WAKE_LOCK`；宿主照 README 加 `configChanges` / `adjustResize` / `@style/Theme.TMessages` 就能跑。
- `TelegramUI.init` 放在 `Application.onCreate` 里没遇到初始化顺序问题。
- `ChatActivityEnterViewDelegate` 全是 default 方法，只实现 `onSendText` 就能发消息（除了上面第 5 条）。

## 观察到但没定位

- 第一次打开 `MessageContextMenu` 后按返回键，菜单没关（焦点停在 `PopupWindow` 上），再按一次才关。只遇到一次，原因没查。

## 这次没测的

- 不用 `TelegramActivity` / `BaseFragment`（例如放进 AppCompat + Jetpack Navigation）能不能用：README 只写了 `TelegramActivity`，这次没试。
- 不用 `Theme.TMessages`，或忘了调 `TelegramUI.init` 会怎样。
- 语音录制、附件按钮、回复 / 编辑顶栏（本示例隐藏了录音按钮）。
- Material exclude 方案的运行时行为、`isShrinkResources = true` 的运行时行为（只量了体积）。
- 小米（掉线）和 Android 11+ 设备。
