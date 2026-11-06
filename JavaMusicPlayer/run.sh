#!/bin/bash
set -e

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

mvn -q clean package
exec java -Dfile.encoding=UTF-8 -jar target/JavaMusicPlayer-1.0-SNAPSHOT.jar
