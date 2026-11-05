# java-

仿照千千静听、Foobar 等主流播放器制作的 Java 桌面音乐播放器，提供本地音乐播放、曲库管理和歌词显示。

## 本次更新

- 采用 Java 17、Swing 与 FlatLaf，重做沉浸式深色播放器界面，支持无边框窗口、响应式曲库、封面呼吸动画和动态主题色背景。
- 使用 Java2D 矢量绘制窗口控制、播放、音量、模式和添加等图标，统一进度条与音量滑块配色。
- 增加歌曲搜索、双击播放、拖放导入、键盘快捷键、上一首/下一首、播放模式、进度定位和音量控制。
- 支持读取 MP3 的 ID3 标题、歌手、专辑和内嵌封面；缺少标签时回退到文件名。
- 增加 LRC 多行歌词滚动与点击歌词跳转，并按封面主色调过渡背景和控件强调色。

## 已解决的问题

- Maven 曾使用过时的 Java 5 默认编译级别；现明确使用 Java 17。
- 清理了重复声明为 `com.musicplayer.model.Lyric` 的源码，解决重复类编译失败。
- 修复播放器启动时 classpath 缺少 JLayer 的问题；在没有可用 Java Sound 输出线路的 Linux/WSL 环境中，回退到 PulseAudio `paplay` 输出。
- 移除了无法创建 JavaFX Media 播放器的运行依赖，MP3 解码改由 JLayer 完成。
- 使用不透明暗色窗口和按需动画重绘，避免部分 Linux/WSLg 环境下的透明窗口黑屏与闪烁。

## 运行

需要 JDK 17+ 和 Maven。Linux/WSL 若没有可用的 Java Sound 设备，需要安装并运行 PulseAudio `paplay`。

```bash
cd JavaMusicPlayer
./run.sh
```

默认曲库目录为 `JavaMusicPlayer/resources/music`，歌词目录为 `JavaMusicPlayer/resources/lyrics`。可通过窗口中的添加按钮或将 MP3 文件拖入曲库导入。

构建与测试：

```bash
mvn clean test
```

## 后续优化

- 修复并验证 `package.sh`，生成包含运行依赖、资源和正确清单的独立发行包。
- 增加频谱可视化与均衡器。
- 增加迷你悬浮窗和桌面歌词。
- 支持更多音频格式、播放队列持久化和封面缓存。
- 补充播放、歌词同步、跨平台音频输出和窗口交互的自动化测试。