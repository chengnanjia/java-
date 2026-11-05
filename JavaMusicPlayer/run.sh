#!/bin/bash
set -e

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

mvn -q clean package dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
RUNTIME_CLASSPATH="$(<target/runtime-classpath.txt)"
exec java -Dfile.encoding=UTF-8 -cp "target/classes:$RUNTIME_CLASSPATH" com.musicplayer.ui.MainFrame
