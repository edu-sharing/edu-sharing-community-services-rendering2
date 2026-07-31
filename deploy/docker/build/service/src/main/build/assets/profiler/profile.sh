#!/bin/sh
set -eu

SOURCE_PATH="$(cd "$(dirname "$0")" >/dev/null && pwd)"
OLD_PATH="$(pwd)"
cd "${SOURCE_PATH}"

./async-profiler-2.0-linux-x64/profiler.sh -e itimer -f jfr -f report.jfr -d "${1:-10}" 1
java -cp ./async-profiler-2.0-linux-x64/build/converter.jar jfr2flame report.jfr report.html

cd "${OLD_PATH}"
