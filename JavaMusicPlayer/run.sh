#!/bin/bash
# Java音乐播放器一键运行脚本（修正 jlayer 依赖引入）
# 功能：清理-编译-运行全流程

# 定义绝对路径（避免相对路径歧义，核心优化）
PROJECT_DIR="/root/.ssh/JavaMusicPlayer"
JAR_PATH="$PROJECT_DIR/lib/jlayer-1.0.1.jar"
BIN_DIR="$PROJECT_DIR/bin"
SRC_DIR="$PROJECT_DIR/src/main/java"

# 1. 先检查 jlayer 包是否存在
if [ ! -f "$JAR_PATH" ]; then
    echo "错误：未找到 jlayer-1.0.1.jar，路径：$JAR_PATH"
    exit 1
fi

# 2. 清理旧 bin 目录并重建
echo "===== 清理旧编译文件 ====="
rm -rf "$BIN_DIR"
mkdir -p "$BIN_DIR"

# 3. 按依赖顺序编译（确保每一步类路径都正确引入 jlayer）
echo "===== 开始编译源码 ====="
# 编译 model 类（无第三方依赖，仅需源码目录）
javac -d "$BIN_DIR" -cp "$SRC_DIR" "$SRC_DIR/com/musicplayer/model/"*.java
if [ $? -ne 0 ]; then
    echo "model类编译失败！"
    exit 1
fi

# 编译 player 类（核心：明确引入 jlayer 包 + bin 目录 + 源码目录）
javac -d "$BIN_DIR" -cp "$JAR_PATH:$BIN_DIR:$SRC_DIR" "$SRC_DIR/com/musicplayer/player/MusicPlayer.java"
if [ $? -ne 0 ]; then
    echo "player类编译失败！请检查 jlayer 包路径或文件是否完整"
    exit 1
fi

# 编译 lyric 类（依赖 model 类，仅需 bin 目录 + 源码目录）
javac -d "$BIN_DIR" -cp "$BIN_DIR:$SRC_DIR" "$SRC_DIR/com/musicplayer/lyric/LyricManager.java"
if [ $? -ne 0 ]; then
    echo "lyric类编译失败！"
    exit 1
fi

# 编译 ui 类（依赖所有类，引入 jlayer 包 + bin 目录 + 源码目录）
javac -d "$BIN_DIR" -cp "$JAR_PATH:$BIN_DIR:$SRC_DIR" "$SRC_DIR/com/musicplayer/ui/MusicPlayerUI.java"
if [ $? -ne 0 ]; then
    echo "ui类编译失败！"
    exit 1
fi

# 4. 运行播放器
echo "===== 编译成功，启动播放器 ====="
java -cp "$BIN_DIR:$JAR_PATH" com.musicplayer.ui.MusicPlayerUI

# 运行结束
echo "===== 播放器已退出 ====="
