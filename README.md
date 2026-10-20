# telegram-ui

把 Telegram Android 官方客户端（经典 UI）的原生 UI 组件抽成独立 Android Library（AAR），去掉账号、消息、网络等业务依赖，供其他项目直接复用。

- 基线：[DrKLO/Telegram](https://github.com/DrKLO/Telegram) `release-11.4.2-5469`（不追上游）
- 包名保持上游原样（`org.telegram.ui.*`、`org.telegram.messenger.*`），库 namespace 为 `org.telegram.messenger`，所以上游代码里的 `org.telegram.messenger.R` 不用改
- 第一阶段：纯 Java 组件；第二阶段接入 Lottie（上游 rlottie 原生库，CMake 构建出 `libtguilottie.so`，含 armeabi-v7a / arm64-v8a / x86 / x86_64）

## 目录

```
tgui/        库模块 → tgui/build/outputs/aar/tgui-release.aar
  src/main/cpp/  rlottie + lottie.cpp（从上游 jni/ 搬来，只保留 Lottie 部分）
demo/        演示应用（org.telegram.uidemo）
tools/       抽取/裁剪用的脚本（见下文）
upstream/    上游源码浅克隆，只读参考，已在 .gitignore 中
```

## 构建

需要 JDK 17 或 21（AGP 9.0.1 / Gradle 9.1.0，compileSdk 36，minSdk 21），以及 NDK `28.2.13676358` 和 CMake `3.22.1`（编 rlottie）：

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

坐标 `org.telegram.ui:tgui:11.4.2-ui1`。直接引用 AAR 文件时，宿主需要自己加 `androidx.core:core`、`androidx.annotation:annotation`、`androidx.dynamicanimation:dynamicanimation`、`androidx.customview:customview`、`androidx.collection:collection`。RecyclerView 用的是上游自带的 fork（`androidx.recyclerview.widget`，源码在库里），**宿主不要再引入官方 `androidx.recyclerview`**，否则类会冲突。

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

```java
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
| 主题 | `Theme`（内置 Classic / Dark / Arctic / Day / Night，从上游 `.attheme` 资源加载；强调色、自动夜间模式（定时 / 光线 / 跟随系统））、`ThemeDescription`、`ThemeColors`、`OKLCH` |
| 宿主 | `TelegramUI`（初始化 + 主题切换入口）、`TelegramActivity`（生命周期转发、系统栏颜色、日夜切换动画） |

## 剥离与 stub

业务类用最小 stub 代替，或直接删掉调用点：

| 上游 | 处理方式 |
| --- | --- |
| `ApplicationLoader` | 只保留 context / handler，由 `TelegramUI.init()` 初始化 |
| `LocaleController` | 直接读 Android 字符串资源（不再下载语言包），复数用 ICU 规则；`isRTL` 跟随系统语言 |
| `SharedConfig`、`MessagesController`、`UserConfig`、`LiteMode`、`BuildVars`、`FileLog` | 只留 UI 用到的开关和偏好设置；单账号；日志写到 logcat（tag `tgui`） |
| `Utilities` | 去掉 native 方法和加密相关代码，stack blur 改用纯 Java 实现 |
| `RLottieDrawable` / `RLottieImageView` | 上游原版，native 库换成 `libtguilottie.so`；`addParentView` 改收 `View`（无 `ImageReceiver`），`RLottieImageView` 去掉了按 `TLRPC.Document` 加载贴纸的分支。`BitmapsCache` 缓存目录改为 `cacheDir/acache` |
| `Emoji`、`AnimatedEmojiDrawable`、`AnimatedEmojiSpan` | 系统 emoji 原样显示；自定义 emoji 只保留常量和 `SwapAnimatedEmojiDrawable`，当普通 Drawable 容器用 |
| `BackupImageView` | 改成本地 Drawable/Bitmap 视图，支持圆角，不再依赖 `ImageReceiver` 和网络加载 |
| `EditTextEffects` | 去掉剧透（spoiler）、引用块和自定义 emoji 渲染 |
| `URLSpanReplacement` | 点击后用 `ACTION_VIEW` 打开链接 |
| `LaunchActivity` | 换成 `TelegramUI.Host` / `TelegramActivity` |

删掉的上游功能：聊天气泡和壁纸（`MessageDrawable`、`createChatResources`、壁纸加载 / 图案）、云端主题（`TL_theme`、主题上传）、Stories、Bot WebView 标签栏、群组通话画中画、`Bulletin` 提示条、剧透效果、ActionBar 搜索里的筛选标签、`BackButtonMenu`（长按返回的历史列表）、`TextCell` 的 sticker / 等级锁 / NEW 标记，以及 `CheckBoxCell.setUserOrChat`（换成了 `setUser(CharSequence, Drawable)`）。

## 已知限制

- Lottie 只带了库内引用到的 `R.raw` JSON（对话列表的归档 / 滑动动作图标、骰子 / 飞镖等），外加上游 APK 自带的 46 个大幅插画：11 只 Utya 小鸭子 `R.raw.utyan_*`、两步验证猴子 `tsv_setup_monkey_*` / `tsv_monkey_close`、设置和邮箱引导（`tsv_setup_*`、`email_*`）、钱包 `wallet_*`、`qr_login`、`filter_new` 等，以及一批白色线条图标（`passcode_lock`、`stats`、`fragment*`、`boosts` 等，上游画在彩色圆形上，可配合 `AlertDialog.Builder.setTopAnimationIsNew(true)` 使用），demo 的 "Lottie illustrations" 页分组展示全部动画。客户端里另一些鸭子（搜索无结果等空状态）和动态表情、贴纸是运行时从服务器贴纸包下载的，不在 APK 里；其它动画 JSON 由宿主放进自己的 `res/raw`，再 `setTopAnimation(R.raw.xxx, …)` / `new RLottieDrawable(R.raw.xxx, …)` 使用
- rlottie 的 pixman NEON 汇编（`pixman-arm*-neon-asm.S`）需要 GNU as，新 NDK 只有 clang 汇编器，所以所有 ABI 都走 rlottie 的 C 填充路径，纯色填充比上游略慢
- 不能播放 Telegram 贴纸 / TGS 文件的网络加载（没有 `ImageReceiver` / `FileLoader`）；本地文件可以用 `new RLottieDrawable(File, w, h, cacheOptions, limitFps)`
- 字符串只带了库内实际用到的 49 条英文，加上游自带的部分翻译（ar / de / es / it / ko / nl / pt-BR）；其它语言需要宿主覆盖资源
- 只在 Android 9 真机（Huawei MHA-AL00）上跑过 demo；平板布局和 Android 14+ 的边到边窗口还没验证
- `Theme` 里仍留着对话列表、个人资料相关的 paint/drawable 字段，没用到但可以安全忽略；后续可以继续瘦身

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
| `fiximports.py` / `restoreimports.py` / `autoimport.py` / `unusedimports.py` | 整理 import |
| `audit.py [Class...]` | 对比上游，列出被删掉的顶层成员 |
| `show.sh Class from to` | 带行号打印源码片段 |

## 许可证

上游 Telegram for Android 使用 **GPL v2 或更高版本**授权（见 `LICENSE`）。本库是它的衍生作品，同样受 GPL 约束：分发包含本库的应用时，必须按 GPL 公开该应用的源码。闭源项目请先确认合规性。另外 Telegram 的名称和图标是商标，demo 用的是中性图标。
