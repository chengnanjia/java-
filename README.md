# java-

仿照千千静听、Foobar 等主流播放器制作的 Java 桌面音乐播放器，提供本地音乐播放、曲库管理和歌词显示。

## 本次更新

- 保留 Java 17、Swing、FlatLaf 的现有播放器界面和 MainFrame 播放入口，移除未使用的旧 UI、播放器与歌词实现。
- 新增 Maven 构建忽略规则，停止跟踪编译产物；MP3 播放依赖统一由 Maven 管理。
- 使用 Maven Shade Plugin 将运行依赖打入可执行 JAR，清单入口为 `com.musicplayer.ui.MainFrame`。
- 加入 JUnit 5、Maven Surefire 和 UTF-8 源码编码配置，为后续自动化测试做准备。
- 从 WAV/PCM 输出链路扩展到 FLAC 解码，支持 8/16/24 位单声道与立体声，并按 STREAMINFO 计算时长。

## 已解决的问题

- Maven 曾使用过时的 Java 5 默认编译级别；现明确使用 Java 17。
- 移除了已由 MainFrame 播放实现替代的旧 UI、播放器和歌词模型。
- 修复播放器启动时 classpath 缺少 JLayer 的问题；在没有可用 Java Sound 输出线路的 Linux/WSL 环境中，回退到 PulseAudio `paplay` 输出。
- 移除了无法创建 JavaFX Media 播放器的运行依赖，MP3 解码改由 JLayer 完成。
- 使用不透明暗色窗口和按需动画重绘，避免部分 Linux/WSLg 环境下的透明窗口黑屏与闪烁。

## 运行

需要 JDK 17+ 和 Maven。Linux/WSL 若没有可用的 Java Sound 设备，需要安装并运行 PulseAudio `paplay`。

```bash
cd JavaMusicPlayer
./run.sh
```

也可以直接运行打包后的单一 JAR：

```bash
cd JavaMusicPlayer
./package.sh
java -jar target/JavaMusicPlayer-1.0-SNAPSHOT.jar
```

JAR 内含应用及 Maven 运行依赖。当前默认曲库目录为 `JavaMusicPlayer/resources/music`，歌词目录为 `JavaMusicPlayer/resources/lyrics`；由于曲库持久化和与工作目录解耦的资源定位尚未实现，运行 JAR 时需将工作目录设为 `JavaMusicPlayer`。也可通过窗口添加按钮或拖入 MP3 文件。

FLAC 解码使用 `org.jflac:jflac-codec:1.5.2`，该依赖按 LGPL-2.1 许可发布。分发包含该依赖的应用时，请遵守 LGPL 的再分发要求，并随包提供相应许可证及源码获取方式；当前 Shade JAR 尚未完成许可证材料的打包配置。

`JavaMusicPlayer/src/test/resources/audio` 中的短音频是合成正弦波夹具，使用 ffmpeg 的 FLAC 编码器生成（分别为 44.1 kHz/16 位/双声道和 32 kHz/24 位/单声道）；SeekTable 测试在临时副本中加入最小测试表，不修改原始夹具。

运行测试：

```bash
mvn clean test
```

仅打包：

```bash
mvn clean package
```

## 后续优化

- 增加用户目录配置、曲库与播放状态持久化，并让默认资源定位不依赖启动目录。
- 重构播放队列，完善列表循环、顺序停止、随机洗牌和播放历史。
- 增强 LRC 编码、标签、逐字时间戳解析与歌曲级偏移设置。
- 增加曲库与多歌单管理、文件夹扫描和 M3U8 导入导出。
- 统一后台任务和播放错误反馈，补充播放器边界场景的自动化测试。
- 增加频谱可视化、均衡器、迷你悬浮窗、桌面歌词和更多音频格式支持。
