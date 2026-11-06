#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

mvn clean package
printf '可执行 JAR：%s\n' "$PROJECT_DIR/target/JavaMusicPlayer-1.0-SNAPSHOT.jar"
