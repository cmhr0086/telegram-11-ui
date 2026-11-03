# 路线图进度与交接

## 新任务 A–F（2026-11-03）

用户已合并 PR #1；远端 `dev/ui-lib` 基线为 `3834565`。当前工作分支 `codex/phase-c`，基于阶段 B 的 `344a205`。每阶段结束推送并创建目标为 `dev/ui-lib` 的 PR；下阶段从上一阶段分支继续，不等待合并，不自行合并，不改 main。下文旧路线图的“未合并”是当时记录，已由此状态更新。

上游再次核实为 `release-11.4.2-5469`。基线 `:tgui:assembleRelease :demo:assembleDebug` 通过（TEMP/phase-a-baseline.log）。继续复用 tgui、原包名、RecyclerView fork 与 GPL 头，不引入网络层或平行模块。

| 阶段 | 状态 | 剩余工作 |
| --- | --- | --- |
| A 贴纸与 GIF | 已完成，限制见下 | PR #2，库/消费端/仪器及 MuMu 真操作通过；WEBM 首帧降级 |
| B 附件菜单 | 已实现并推送 PR #3 | 主体/五页/权限/选择/确认与宿主回调；简化交互和人工补验见下 |
| C 更多消息 | 进行中 | 未读/服务/群聊/转发/联系人/音乐/位置已完成；投票/文件缩略图和键盘待实现 |
| D 输入增强 | 未开始 | 提及、表情联想、命令、发送预览 |
| E 查看器升级 | 未开始 | 缩略图条、宿主操作、播放控制、相册分组 |
| F 聊天页与资料页 | 未开始 | ChatFragmentBase、demo/sample 接入、群组频道资料 |

roadmap_strings.xml 已并回对应模块的 strings.xml。阶段 A/B 已按功能提交并发布 PR #2/#3，库/demo、消费端及仪器检查通过；不能将 C–F 计为完成。

### 阶段 C 当前交接

- 已提交并推送未读 f4d10cd、服务 7db39c1、群聊 4441ce7、转发864201e；[PR #4](https://github.com/cmhr0086/telegram-11-ui/pull/4) 为 draft，目标 dev/ui-lib，汇总 A–F 状态和未验证部分。C 尚有后续功能，保持 draft；不合并，不改 main。联系人cc8d05a已提交推送。音乐00140ca已提交推送。位置气泡已实现并通过最终构建和设备验收，随本次独立提交推送。
- ChatUnreadCell 经 tools/pull 搬运，保留原尺寸/密度资源/GPL；增加主题刷新、demo 锚点跳转和宿主预览。库release/demo/仪器构建及host debug/R8通过（TEMP/phase-c-unread-build.log、phase-c-unread-demo.log、phase-c-unread-host.log）；设备component=unread通过，实测聊天菜单跳转及夜→日刷新，截图TEMP/unread-night.png、unread-day.png。
- 服务消息已完成：复用 ChatActionCell 原文字胶囊，移植 MessageObject 文字分支为纯 ServiceMessage（普通群返回/大型群加入、邀请、改名、各媒体类型置顶、自定义），用户/置顶目标通过回调交宿主。demo 菜单定位和点击置顶跳原文、日夜显示通过；host 信息按钮预览。库release/demo/test/publish及host debug/R8通过（TEMP/phase-c-service-build.log、phase-c-service-test-build.log、phase-c-service-host.log）；component=service通过（phase-c-service-device.log）。覆盖模板占位、emoji span、同文字换ID、取消点击、两类群加入、20字符预览、清空layout和日期复用。初次测试把span插入顺序误认为文字顺序，已按真实文本位置排序并依实际layout坐标触摸，生产回调断言保留。截图service-day.png、service-night.png、service-pin.png。未迁移服务卡片/礼物、按压高亮、逐链接无障碍导航。
- 群聊已完成：Sender/原ID姓名配色、组首姓名、42dp头像/48dp列、300秒同发送者合并，复用既有各类气泡；贴纸/圆视频/纯emoji按无背景分支绘制旁侧名字，列表ChatAvatarDecoration复用上游可视组吸底逻辑。demo菜单可切换，sample使用群聊模型；旧API仍可默认行内绘制。头像padding/透明度、API21比较器和贴纸姓名8dp偏移已修；贴纸/圆视频保持原尺寸上限而非再扣头像列缩小。
- 群聊设备检查发现首次Theme初始化把姓名画笔覆盖成默认12px，首条与后续字号不同，修正后保留原断言通过。component=group覆盖首次/同对象重绑/长姓名、颜色、300秒/未知ID边界、Photo姓名间隔和圆角、旁侧锚点、原贴纸/圆直径、长组末行屏外头像、clipToPadding=false+padding及半透明、宿主Drawable bounds恢复。最终库release/demo/test/publish及host debug/R8通过（TEMP/phase-c-group-size-build.log、phase-c-group-size-host.log）；设备phase-c-group-size-device.log通过。共享Theme修复后完整旧功能回归含服务/群聊已通过（phase-c-group-full-device.log；当时输出结尾漏列Group名称，但runner在返回前已执行该检查，现输出已补）。
- MuMu群聊真操作：开关群聊、滚动、日夜切换；TGS及GIF继续动，圆视频点中播放并出现00:05/进度环，相册右列打开正确第11/17张后返回。截图group-night.png、group-stickers.png、group-round-size2.png、group-round-play.png、group-album-night.png、group-album-viewer.png、group-text-night.png。动画头像、头像点击、管理员头衔/Premium多色姓名、圆视频播放放大姓名过渡未移植；复杂RTL/平板需人工检查。
- 转发头：`ForwardBackground` 经 tools/pull 搬入，只有版权头补充；从固定上游 ChatMessageCell/AvatarSpan 裁剪两行来源、1.23倍字号头像、匿名分支、原颜色/位置/按压动画。`ForwardedFrom`/`onForwardPressed` 由宿主提供来源并处理跳转，无网络层。文字/照片/语音/文件/相册/GIF顶部及贴纸/圆视频旁侧接入，纯emoji保留大号显示；旁侧回复与来源共用服务背景但独立点击。
- 转发审查修复：侧回复不覆盖媒体，短转发+长回复的纯emoji联合限宽，扁贴纸保留完整侧头高度；圆视频时长锚定视频底部，不随扩行下移。保留贴纸比例/圆视频0.6倍原直径；未通过缩小媒体满足窄屏断言。240dp群聊旁侧剩余宽度可能不足一个省略号，来源为空时不响应点击；设备检查区分这一边界和400dp可见来源，而非要求隐藏来源触发回调。
- 转发最终构建通过：库release/demo/debugAndroidTest/publishToMavenLocal（TEMP/phase-c-forward-final-build.log），外部host debug/R8 release（phase-c-forward-host.log）。完整MuMu Android15回归通过（phase-c-forward-full-device.log），包括8类气泡×收发×240/400dp、匿名/同对象替换/取消/detach/宿主头像bounds恢复、侧回复与来源隔离、800×80扁贴纸行高和大号emoji长回复；旧TGS完整周期/GIF临时重绑续播/收藏尺寸检查也通过。
- MuMu真操作：转发菜单定位，普通来源点击显示正确ID，日夜切换，群聊开关，纯emoji来源+回复显示，匿名文件头，图片本体打开第1/17张查看器并返回，贴纸旁侧来源回调和回复跳原消息。截图TEMP/forward-night.png、forward-day.png、forward-source-tap.png、forward-emoji.png、forward-photo-viewer.png、forward-stickers.png、forward-sticker-source.png、forward-sticker-reply.png。TGS在贴纸截图间持续变化。圆视频侧头已查看；本轮未重新完成人工播放验收，真实播放器已有完整设备回归覆盖。
- 转发限制：PSA/导入/Saved特殊头、来源加载状态、动态头像未移植；仅回复不含转发的纯emoji仍沿用旧普通文字气泡。240dp窄群聊旁侧文字可能省略为空；复杂RTL/平板/大字体需人工检查。README/CHANGELOG和demo/sample已更新，无破坏性API变更或新依赖。
- 联系人：从上游ChatMessageCell TYPE_CONTACT 7485/11301/11698/13929/3062等分支裁剪ContactBubbleCell；46dp头像/22dp圆角、15/13dp字号、270dp宽上限、群聊减102dp、正文宽减111dp、36dp动作/60dp按钮区、原整卡及按钮ripple/bounce。Contact元数据和canMessage/canAdd来自宿主，onContactAction报告VIEW/MESSAGE/ADD；单按钮整卡执行该动作，双按钮整卡VIEW。联系人配色独立于群发送者与转发来源，发出名片按上游覆写outReply色。复用既有回复/转发/反应/选择，未知号码和动作文案使用资源。
- 联系人验证：库release/demo/test/publish通过（TEMP/phase-c-contact-final-build.log）；独立host debug/R8 release通过（phase-c-contact-final-host.log）；component=contact定向及完整旧功能回归通过（phase-c-contact-device.log、phase-c-contact-full-device.log）。覆盖收发/240和400dp/两套provider色/群聊+转发+回复/46dp头像与bounds恢复/空长号码/单和双按钮身份/取消重绑/清空/选择模式。初次测试缺MediaDelegate抽象方法导致测试编译失败，已补；颜色断言最初固定第0色，与实际联系人ID规则不符，改为按ID核对，生产代码未为错误断言改色。
- 联系人真操作：日间Message/Add各触发正确宿主回调，夜间群聊模式、长按多选正常；附件Contact选择Alice→预览→Send后出现可点的名片，View Contact回调正确。截图TEMP/contact-day.png、contact-add.png、contact-message.png、contact-night.png、contact-select.png、contact-sent.png、contact-view.png；无AndroidRuntime崩溃输出。文档/demo/sample已更新，无新依赖或通讯录权限，示例动作以Toast展示。vCard解析、电话格式化、实际通讯录操作、动态头像及Premium多色侧线由宿主或后续承担；平板/复杂RTL/大字体需人工补验。
- 音乐完成：MusicBubbleCell 从固定上游 ChatMessageCell DOCUMENT_ATTACH_TYPE_MUSIC 的7632/10592/11655/12211/3567等布局/绘制/触摸分支裁剪，保留原GPL头、270dp上限、82dp主体、44dp播放圆钮、原文字/时长/菜单/颜色及100ms performer→seek过渡。musicTitle/musicPerformer/audioPaused 和既有MediaDelegate回调由宿主驱动，支持说明/反应/群聊/转发/回复；不引入播放器到库或新增运行依赖。
- 音乐审查修复：caption间距/末行时间换行、静止后首次播放的100ms过渡、按压和选中色、loading取消命中、同对象改方向颜色、下载刷新、拖动预览被tick覆盖、链接色与取消父拦截。LocalMusicPlayer串行seek并保留最新目标，等待OnSeekComplete时不采旧位置；切曲迟到回调按player身份拒绝。首次构建菜单图标未导入，改用既有bot_music后通过。
- 最终库release/demo/debugAndroidTest/publishToMavenLocal通过（TEMP/phase-c-music-final-build.log），host debug/R8 release通过（phase-c-music-final-host.log）。完整MuMu Android15设备回归通过（phase-c-music-full-device.log），包含之前TGS完整周期/GIF临时重绑和收藏尺寸；音乐覆盖240/400dp收发、群/转发/回复、取消重绑/选择/清空、三种下载状态、拖动时tick与取消、暂停elapsed、同模型方向颜色，以及真实MediaPlayer推进/暂停/连续seek/恢复/切曲/完成释放、焦点丢失和重复stop。主题provider的isDark分支有断言，实际日夜颜色以UI操作核验。
- MuMu实际音乐操作：菜单定位，播放后0:01、暂停1.5秒不动、拖到0:08、恢复并切另一条到0:02，前一条回演唱者；下载→进度/取消→下载箭头、音乐菜单、长按选中1条、日夜和群聊布局通过，无AndroidRuntime崩溃。截图TEMP/music-{night,play,pause,pause2,seek,switch,download,cancel,day,menu,select,group}.png。未观察真实扬声器输出，不把进度推进写成听觉验证。
- 音乐限制：无流式mini下载/封面覆盖/歌词/后台服务和通知播放；说明样式显示但无独立链接点击。demo用生成的12秒PCM音阶，只打包demo，与语音/圆视频互斥；页面离开/重建/选择时停止。sample展示AAR与宿主回调，主demo提供真实播放器。其它Android版本、平板/复杂RTL/大字体及更多音频格式需人工检查。
- 位置完成：从固定上游 ChatMessageCell TYPE_GEO 普通坐标/venue 分支裁剪 LocationBubbleCell，保留 GPL 头、195dp 地图高、285dp 最大背景宽、群聊让位、原图钉/圆角、15/13dp 地点文字及回复/转发/反应/时间布局。地图由宿主 Drawable 提供，原 map_pin 多密度资源与 map_placeholder.svg 逐文件核对一致；缺图走上游静态 alpha70 占位，无地图 SDK/网络/GPS。新增 Location 模型和 onLocationPressed（默认回落既有媒体回调），旧 API 兼容；demo/sample 坐标、地点及附件选择→预览→发送均接入。
- 位置审查修复：纯坐标含反应时仍用 mediaBackground，反应与时间按原分支定位；图钉只在宿主地图存在时显示，绘制后恢复宿主 Drawable bounds。PhotoBubbleCell 只释放自己持有的父列表拦截，重复 DOWN 不先释放，避免 RecyclerListView 清空点击任务；取消/重绑/离窗分别覆盖。占位测试最初误认为 SVG 中心必不透明，诊断确认该点本来透明，改为区域可见性检查和白底红蓝混合强度断言，未通过改生产底色来迁就测试。
- 位置最终构建：库 release/demo/debugAndroidTest/publishToMavenLocal 通过（TEMP/phase-c-location-final-build.log），独立 host debug/R8 release 通过（phase-c-location-final-host.log）。完整 MuMu Android15 仪器回归通过（phase-c-location-full-device.log），包括旧 TGS 完整周期、GIF 重绑续播和收藏尺寸。位置专项覆盖 240/400dp、收发、坐标/地点、长 RTL 地址、群聊/转发/回复/反应、原尺寸/圆角、空图与 alpha70、Drawable bounds、回调身份、取消/长按/选择/重绑/清空，以及拦截拥有权与重复 DOWN。
- MuMu 位置真操作：日夜主题、地图回调显示 0.000/0.000、缺图占位、附件位置选择→确认→发送、长按多选均通过。截图 TEMP/location-{day,venue,callback,night,missing,sent,final-tap,final-select}.png；最终 APK 重查点击和选择。示例地图明确标示为示意图，不是真实地理数据。未移植实时位置/GPS、地图加载渐变与 shimmer；坐标有效性、地图内容/定位居中和实际跳转由宿主负责，平板/大字体/其它 Android 版本仍需人工检查。
- 下一项：投票气泡，再做文件缩略图、inline/reply keyboard。阶段 C 仍未完成，D–F 未开始；不得把已完成项当作整个目标完成。

### 阶段 B 当前交接

- ChatAttachAlert 与 Photo/Document/Contacts/Poll/LocationLayout 均先 tools/pull 再裁剪；只使用本地 Attachment 模型与宿主回调。顶部标签是本任务要求的布局调整；把手拖动展开/折叠，原 BottomSheet 负责弹出/关闭。完整列表与弹窗的上游整页拖拽联动尚未移植，不可声称逐像素相同。
- AttachmentAccess 发起运行时图片/视频权限，兼容 Android14 选中照片授权；文件使用 ACTION_OPEN_DOCUMENT，联系人不申请系统联系人权限，地图由宿主提供。配置变化销毁 Activity 时取消待处理回调，由新宿主重新打开；不冒用旧 Activity 回调。
- 相册后台查询 content URI、按时间混排照片视频，选择按 URI 隔离；焦点恢复重新检查授权，权限申请中禁止焦点刷新抢占回调。代码已修单类媒体授权被误当完整权限、前1000图片排除视频、迟到回调和销毁后setter更新。
- 投票保留255/100与2..10、匿名/多选，无quiz/拖动排序；位置无GPS/live/在线POI。发送前预览为精简确认列表及字幕，未迁移PhotoViewer编辑器。
- 库 release、demo debug/androidTest、publishToMavenLocal 通过（TEMP/phase-b-api-build.log）；消费端 debug/R8 release 通过（TEMP/phase-b-final-host.log）。全部仪器通过（TEMP/phase-b-final-device.log），覆盖权限状态、联系人快照、poll边界、文件迟到回调、宿主地图替换、分页隔离、展开/收起、确认快照/字幕/双击只发送一次。
- MuMu Android15真实操作通过：首次拒绝→重新请求→系统仅选两张→网格仅显示两张→多选Send(2)→caption→缩略图确认→发送；日/夜主题重新打开菜单均正常，截图TEMP/attachment-preview.png与attachment-dark.png。全授权、API21–33、旋转/系统回收、文件供应商撤回URI权限需要人工补验。宿主demo收到媒体后显示名称/URI摘要，真实媒体模型仍由宿主准备。
- 无新增运行依赖。已按功能提交并推送 [PR #3](https://github.com/cmhr0086/telegram-11-ui/pull/3)，目标dev/ui-lib；未合并。包含用户反馈的两项贴纸修复，后续从本分支继续阶段C。

### 阶段 A 实现及验证记录

- 用户反馈修复（在 phase-b 续接）：收藏图标原图 38dp 被 CENTER 裁切，恢复上游 StickerTabView 的 33×36dp 标签/24dp 图标/26dp 包封面。已新增实测尺寸断言。
- 发送后贴纸冻结已真实复现：180帧素材停在第46帧，4次bind中3次临时detach，10秒无wrap。根因是isShown在临时detach时为false，使renderer暂停，而fork的attachViewToParent不派发window attach；StickerBubbleCell/GifBubbleCell在真实可见绘制时重新同步动画状态，保留显式暂停、不重置进度。修后完整TGS周期/循环及全部现有仪器通过（TEMP/sticker-fix-device.log），发布库/demo/消费端debug+R8通过；GIF三次临时重绑后继续自然像素切换、TGS显式暂停/恢复/离窗释放的追加回归也通过（TEMP/phase-b-final-device.log）。实际发送后连续截图确认从蛋到小鸭完整运动，TEMP/sticker-sent-fixed{,2}.png。修复提交83b85da与578bcf2。

- 分功能提交：字符串合并 `943f5a2`；宿主模型/本地解码 `1aa016a`；贴纸气泡 `0ddb2f8`；GIF 气泡 `04384fe`；长按预览 `18d12d5`；贴纸包弹窗 `2162c22`；原面板恢复与 demo/host 接线 `c2ae32e`。
- 原 EmojiView pager 恢复三页，复制上游 ExtendedGridLayoutManager/Size 和原 GIF/贴纸图标；72dp 列、82dp 格、66dp 图像、36dp 包栏及 100dp GIF 行，收藏/最近/包由宿主快照驱动。业务删除与加载由 MediaSource/模型/回调替换，无新增第三方运行依赖。
- ContentPreviewViewer 从原类导入裁剪：200ms、移动10dp、120ms进出、180遮罩透明度、1300ms操作菜单及滑动换预览；StickersAlert 五列/82dp/48dp上下栏/200ms选中预览。删除网络、安装、账户、Premium和编辑器等分支；未移植完整模糊、滑出菜单、包弹窗拖拽联动及表情面板拖拽展开。
- StickerBubbleCell 保留上游手机0.5/平板0.4上限和服务时间色，透明背景；GifBubbleCell 复用 PhotoBubbleCell。贴纸回复/转发头暂未布局，GIF沿用照片回复/说明。WEBM 固定降级为首帧，解码失败显示宿主缩略图；API21–27 Movie fallback、平板/RTL需人工补验。
- 首轮真实触摸测试发现普通短按未发送：预览组件无条件解除拦截会让 RecyclerListView 清掉点击任务。现仅在预览实际接管手势后解除，测试走真实 dispatchTouchEvent，不放宽断言。
- 构建 `:tgui:assembleRelease :demo:assembleDebug :demo:assembleDebugAndroidTest :tgui:publishToMavenLocal` 通过，TEMP/phase-a-touch-fix.log；samples/host-app debug + R8 release 通过，TEMP/phase-a-host.log。
- MuMu Android15完整仪器输出：`UndoView + AnimatedEmojiDeviceChecks + AlbumDeviceChecks + RoundVideoDeviceChecks + StickerMediaDeviceChecks + StickerBubbleDeviceChecks + EmojiMediaDeviceChecks passed`。新增检查覆盖真TGS帧/GIF像素推进与暂停、WEBP/真实VP9 WEBM首帧、异步取消/迟到回调、detach重绑、原件保留、透明几何/一次时间/点击身份、页类型/禁用页/预览取消/发送隔离。
- MuMu 真操作通过：GIF/WEBP 短按发送；持续长按出现大图和 Send / 收藏 / View sticker pack；移除默认收藏并加入 TGS 鸭子后收藏页更新；包预览点选及 Send 后聊天出现鸭子；夜→日主题切换无崩溃。证据 TEMP/sticker-{gif-send,sent,hold,hold-menu,added-fav,pack-preview,pack-selected,pack-send,day}.png。日间输入 placeholder/展开箭头偏浅是实际观察，尚未定位，保留为后续检查项；收藏持久化由宿主负责，本demo不持久化。
- 已推送 [PR #2](https://github.com/cmhr0086/telegram-11-ui/pull/2)，目标 dev/ui-lib，不自行合并。最终 build 日志 TEMP/phase-a-final.log；cee4e65 避免demo快照使用API24的removeIf，保留minSdk21兼容。下一阶段从phase-a继续。

最后更新：2026-11-03。工作分支：`codex/roadmap`，基线 `origin/dev/ui-lib` @ `7eb17f0`，PR 目标 `dev/ui-lib`。禁止修改或推送 main，完成后推送工作分支并创建 PR，不自行合并。

已推送并创建 [PR #1：恢复 UndoView、本地动态表情、相册与圆形视频](https://github.com/cmhr0086/telegram-11-ui/pull/1)，目标 `dev/ui-lib`，未合并。六个实现/修复提交已在远端；本地和设备验收通过。GitHub Actions 已触发，最后查询为运行中，不能把它记成 CI 已通过。

## 当前方向

继续扩展已有 `tgui`，直接搬运并裁剪 Telegram Android `release-11.4.2-5469`。保留上游包名、GPL 版权声明和 `org.telegram.tgui.recyclerview` fork；新增示例放进现有 demo。旧 `codex/ui-library` 及其 core/ui/独立 demo 方案已放弃，未合并或带入本分支。

先完整阅读 AGENTS.md、README.md、CHANGELOG.md。本机 upstream 已核实为指定 tag，提交 `fb2e545101f41303f1e2712de2e7611a9335f1c3`，只读参考。使用 tools/pull.py 搬类和资源，不复制账号、网络等大块业务代码；业务操作改为模型或回调。

## 按顺序执行

| 项目 | 状态 | 证据与剩余工作 |
| --- | --- | --- |
| UndoView 撤销条 | 已完成 | 已通过 tools/pull.py 导入并裁剪归档/删除路径；保留归档 Lottie、删除 5 秒倒计时和原动画参数，已接入对话列表单项/批量操作。修复后 release/demo 构建通过；7 项设备边界测试已通过。单项/批量/过滤页原位恢复、超时、主题切换后数据保持均通过；外部宿主 debug/R8 release 通过。夜间缺字已在独立基线复现，确认是原有问题。 |
| AnimatedEmojiDrawable / AnimatedEmojiSpan | 已完成 | 宿主本地 JSON/gzip TGS 经现有 rlottie 实际渲染；原输入栏/气泡/发送路径通过模拟器截图及原生帧、像素检查，release/demo、AAR 消费端 debug/R8 release 构建通过。边界测试见下。 |
| 相册分组 | 已完成 | 已抽取 GroupedMessages 的特殊/通用布局；复用照片气泡组成相册，逐格查看器、整组多选、2–10 张和日夜切换已操作。release/demo、设备断言、AAR debug/R8 release 均通过，单 View 与上游多 cell 的 inset 差异已明确记录。 |
| 圆形视频消息 | 已完成 | 上游普通圆视频分支与阴影，宿主模型/回调；demo MediaPlayer 实播/暂停恢复/切换/长按/夜间均操作，设备自动检查真实首帧、暂停/恢复和资源释放。构建及 AAR debug/R8 release 通过，限制见下。 |
| 低风险限制补齐 | 已完成一项 | 修复回归中定位的 CRLF 主题色解析问题，夜间缺字在设备上已恢复，保持壁纸字节偏移。其它候选不扩范围。 |

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

UndoView `886b99a`、本地动态表情 `ecb00e3`、相册 `a3d5ef2`、主题 CRLF 修复 `871d103`、圆形视频 `1832e76`，以及 TGS 流关闭/异常源 `b7fd21b` 已分别提交并推送。PR #1 已创建，目标 dev/ui-lib，未合并；main 未改动。接续任务从此文件和 README 的组件限制开始，不重新建立 core/ui，不重复实现已经完成的四项。

## 正在定位的观察

- MuMu 上切换夜间后对话列表标题、消息和筛选标签不可见，日间恢复。已创建 detached 基线工作树 `D:\Codex\telegram-ui-baseline-check` @ 7eb17f0，使用独立 applicationId 诊断，构建并安装独立包后复现同样症状，确认是本次改动前存在的问题；主工作树源代码未因该诊断改变，保留工作树供后续排查。
- 上述问题现已定位并修复，见下面“主题 CRLF 修复”；保留基线工作树和截图作修复前证据。
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
- 消费端 debug/R8 release 通过，TEMP/roadmap-theme-round-host.log。定向 UI 回归夜间列表的 All/Unread/Personal、姓名/消息/时间恢复可见，切回日间正常，TEMP/theme-night-fixed.png、theme-day-fixed.png。

## 圆形视频

- RoundVideoBubbleCell 从固定 upstream ChatMessageCell 的普通圆视频分支裁剪；普通直径0.6最小侧，圆形 center crop、17dp时长条/6dp圆角/服务底50%、未读点、3dp圆头0x9fffffff进度环和单个时间/发送状态；复用照片 PhotoRenderer 与 RadialProgress2。RoundVideoShadow 用 tools/pull.py 原样搬入并在 Theme 初始化。业务媒体控制器换成纯模型/MediaDelegate 回调。
- demo LocalRoundVideoPlayer 在透明列表后、壁纸前放圆形 TextureView，首帧后只隐藏 cell 缩略图，cell 继续画环/状态且可点暂停。MediaPlayer 本地播放、居中裁剪、音频焦点、暂停/恢复、另一条播放、离屏/页面暂停/主题重建和选择状态都回收播放器/Surface/覆盖层。现有语音播放与圆视频互相停止。
- 修复审查与真实 UI 发现的问题：暂停后时长跳回总时长，新增 videoPaused 区分暂停会话和停止；圆视频下载状态刷新分支缺失；原 Theme 声明阴影却未初始化；隐藏缩略图时原先也隐藏环且不能点暂停；圆外触摸不播放、移动和detach取消长按/释放父拦截锁。
- 构建：release/demo/测试 APK/publishToMavenLocal，TEMP/roadmap-theme-round-final.log；消费端 debug/R8 release，roadmap-theme-round-host.log。最终完整设备仪器包括 Theme/Undo/Emoji/Album/Round，输出全部 passed。
- RoundVideoDeviceChecks：240/400dp × 收发，直径/中心裁剪/框内边界/一次时间；圆内点击/圆外不播放/移动取消/长按与detach取消；隐藏缩略图仍画半圈进度并可点；暂停剩余时长保持；下载/加载/停止绘制；另用真正本地 MediaPlayer 等待首帧和进度>600ms，暂停450ms位置稳定、恢复400ms进度推进，stop后 player/surface/layer 均null且状态清空、重复stop安全。
- 真实 UI：视频帧/环/倒计时变化、暂停两秒画面不动、恢复至结束回播放图标、切第二条停止第一条、离屏返回能重播、退出页面、长按整条计数1、夜间圆形预览/时间；TEMP/round-play1.png、round-play2.png、round-pause1.png、round-pause2.png、round-resume.png、round-finished.png、round-switch.png、round-return.png、round-replay.png、round-select.png、round-night.png。暂停修复后剩余00:04保持两秒，恢复到00:03：round-pause-fixed.png、round-pause-fixed2.png、round-resume-fixed.png。
- 未验证扬声器实际输出或音轨；退出/离屏无声不作为听觉观察结论，资源释放有设备断言。播放扩大/过渡、拖环seek、转写、阅后即焚、PiP、圆消息自身reply/reactions未移植，README已明确；其它气泡原功能保留，demo不向圆消息显示未实现的反应菜单。

## 最终审查

- 修复本地 TGS 流未显式关闭的问题（原文件输入虽关闭，gzip inflater 会延迟至 GC）：解压流纳入 try-with-resources，成功/异常均释放。补充截断 gzip 和超过 4 MiB 解压上限的回退断言，仍保留宿主原件。
- 四个优先项均已实现，没有因编译卡住而跳过整项。未移植的上游分支、尺寸差异及人工验证范围已在各段/README记录；不将缺省、回调、模拟下载或未验证音频描述为真实服务功能。
- 原无障碍描述 `LOC_ERR:AccDescrUnreadCount_other` 留待后续，未为此改动 LocaleController 的全局行为。其它 Android 版本、平板、复杂 RTL/超大字体、实际扬声器与长时间后台仍需人工补测。
- 最终构建：`:tgui:assembleRelease :demo:assembleDebug :demo:assembleDebugAndroidTest :tgui:publishToMavenLocal` 通过（TEMP/roadmap-final-build.log）；最后发布 AAR 的消费端 debug + R8 release 通过（roadmap-final-host.log）。
- 最终设备输出 `RoadmapInstrumentation: UndoView + AnimatedEmojiDeviceChecks + AlbumDeviceChecks + RoundVideoDeviceChecks passed`（roadmap-final-instrumentation.log），包含 ThemeFileDeviceChecks、损坏/超限压缩源以及实际 MediaPlayer 首帧/暂停/恢复/释放断言。不要仅凭 adb exit code 判定成功。
- 2026-11-03 fetch 核实 origin/dev/ui-lib 仍为7eb17f0，无需重放已有提交。工作分支未修改 main，未合并旧 codex/ui-library。截图与日志在本机 TEMP，不作为已上传的远端附件；PR 记录本地证据及未验证范围。
