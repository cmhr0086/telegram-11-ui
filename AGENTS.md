# AGENTS.md — 交接说明（给 Codex 等编码代理）

## 项目是什么

把 Telegram Android 官方客户端（DrKLO/Telegram `release-11.4.2-5469`，不追上游）的经典 UI 组件抽成独立 Android 库 `tgui`（AAR），去掉账号、消息、网络等业务依赖。

**项目已经做了很多，不是从零开始。** 动手前先读 `README.md`（尤其“包含的组件”“剥离与 stub”“已知限制”“路线图”）和 `CHANGELOG.md` 的 `Unreleased` 段，不要重建已有模块。

## 工作方式（必须遵守）

- **分支**：所有工作在 `dev/ui-lib` 上进行，或从它拉出子分支，PR 目标为 `dev/ui-lib`。**禁止改动 `main`**。
- **路线**：这个库不是“自己写一套底座再重写组件”，而是**把上游代码原样搬进来再裁剪**：
  - 包名保持上游原样（`org.telegram.ui.*`、`org.telegram.messenger.*`）；库 namespace 是 `org.telegram.tgui`，搬进来的代码把 `import org.telegram.messenger.R;` 换成 `import org.telegram.tgui.R;`
  - 业务类（`ApplicationLoader`、`MessagesController`、`SharedConfig`…）已经有最小 stub，优先复用 stub，不要新建平行的工具类
  - 需要大改的组件改成“模型驱动”，参考现有的 `DialogCell` + `DialogItem`、`TextBubbleCell` + `TextBubbleMessage`、`ProfileFragment` + `Profile`
  - RecyclerView 用的是上游 fork，包名 `org.telegram.tgui.recyclerview`，不要换成官方 androidx 版
- **数值照搬**：尺寸、颜色 key、动画时长和插值器按上游原值移植，不要凭感觉改。
- **保留 GPLv2 版权头。**

## 上游源码与工具

`upstream/` 是上游浅克隆，只读参考，已被 `.gitignore` 忽略，**新环境里没有，需要先拉取**：

```bash
git clone --depth 1 --branch release-11.4.2-5469 https://github.com/DrKLO/Telegram upstream
```

`tools/` 里的脚本都依赖 `upstream/`，用法见 README 的“抽取工具”一节。常用流程：
`pull.py class <FQCN>` 搬类 → `deps.py` 查缺的依赖 → `build.sh` + `errs.py` 按类汇总编译错误 → `errmembers.py` / `prune.py` / `dropbranch.py` 删掉业务成员 → `pull.py missing` 补资源 → `audit.py` 核对删了什么。

## 构建与验证

- JDK 17 或 21，AGP 9.0.1 / Gradle 9.1.0，compileSdk 36，minSdk 21
- NDK `28.2.13676358`、CMake `3.22.1`（编 rlottie）；需要 `ANDROID_HOME` 或 `local.properties`
- 每完成一步都要通过：

```bash
./gradlew :tgui:assembleRelease :demo:assembleDebug
```

- 改动公开 API 或资源时，再跑一次 AAR 消费端验证（CI 也会跑）：

```bash
./gradlew :tgui:publishToMavenLocal && (cd samples/host-app && ./gradlew :app:assembleDebug :app:assembleRelease)
```

- 环境里装不了 Android SDK 时，在 PR 里明确写“未本地编译”，依赖 CI 结果，不要声称已验证。

## 提交规范

- 每个组件或功能单独一个 commit，说明搬了什么、删了什么、为什么
- 新组件要在 `demo/src/main/java/org/telegram/uidemo/` 加示例，并同步更新 `README.md` 和 `CHANGELOG.md` 的 `Unreleased`
- 进度、遇到的问题、跳过的部分写进 `docs/PROGRESS.md`；任务中断后，下一次从这个文件接着做
- 解决不了的问题记录后跳过，不要为了编译通过去复制大块业务代码

## 下一步可选方向（来自 README 路线图）

优先级由维护者决定，开工前先确认：

1. 阶段 C：动态表情 / 自定义表情的真实渲染（`AnimatedEmojiDrawable` / `AnimatedEmojiSpan`，rlottie 已就绪）
2. 阶段 D：`EmojiView` 的贴纸 / GIF 页，数据由宿主提供
3. 媒体气泡后续：视频、圆形视频、相册分组
4. 对话列表的撤销条（`UndoView`）
5. `SuggestEmojiView`、提及列表、`MessageSendPreview`、机器人键盘
6. “已知限制”里列出的缺口
