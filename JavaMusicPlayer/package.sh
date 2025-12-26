#!/bin/bash
# Java音乐播放器一键打包脚本
# 功能：编译项目 → 复制资源 → 创建清单 → 打包JAR

# 定义项目路径（自动获取当前目录）
PROJECT_DIR=$(pwd)
# 编译输出目录
BIN_DIR="$PROJECT_DIR/bin"
# 依赖包路径
LIB_DIR="$PROJECT_DIR/lib"
# 资源目录
RESOURCES_DIR="$PROJECT_DIR/resources"
# 输出JAR文件名
JAR_NAME="JavaMusicPlayer.jar"


# 步骤1：编译项目（先清理旧编译文件）
echo "===== 1. 编译项目 ====="
rm -rf "$BIN_DIR"
./run.sh
# 关闭自动启动的播放器窗口（若需要）
sleep 2
pkill -f "java -cp $BIN_DIR:$LIB_DIR/jl-1.0.1.jar com.musicplayer.ui.MusicPlayerUI"


# 步骤2：复制依赖包和资源到编译目录
echo "===== 2. 复制依赖和资源 ====="
# 复制jlayer依赖包
cp "$LIB_DIR/jl-1.0.1.jar" "$BIN_DIR/"
# 复制resources目录（音乐、歌词）
cp -r "$RESOURCES_DIR" "$BIN_DIR/"


# 步骤3：在编译目录创建MANIFEST.MF清单文件
echo "===== 3. 创建MANIFEST.MF ====="
cat > "$BIN_DIR/MANIFEST.MF" << 'MANIFEST_EOF'
Manifest-Version: 1.0
Main-Class: com.musicplayer.ui.MusicPlayerUI
Class-Path: jl-1.0.1.jar
MANIFEST_EOF


# 步骤4：进入编译目录打包JAR
echo "===== 4. 打包JAR文件 ====="
cd "$BIN_DIR" || exit 1
jar cvfm "$JAR_NAME" MANIFEST.MF com/ resources/ jl-1.0.1.jar


# 步骤5：将JAR文件复制到项目根目录
echo "===== 5. 复制JAR到项目根目录 ====="
cp "$JAR_NAME" "$PROJECT_DIR/"


# 完成提示
echo "===== 打包完成！ ====="
echo "输出JAR文件：$PROJECT_DIR/$JAR_NAME"
echo "运行方式：java -jar $PROJECT_DIR/$JAR_NAME"
