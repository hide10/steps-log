#!/usr/bin/env bash
# ビルド → テスト → 実機インストールを一息でやる。
#
# 実機で毎日使ってみて気づいたことを次の改善に回す（PDCA）ための入口。
# テストが落ちたらインストールしない。
#
# 環境ごとの違いは dev.env に書く（dev.env.example を参照）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 環境ごとの設定。無くても既定値で動く
# shellcheck disable=SC1091
[ -f "$ROOT/dev.env" ] && . "$ROOT/dev.env"

: "${ADB:=adb}"
: "${JAVA_HOME:=/usr/lib/jvm/java-17-openjdk-amd64}"
: "${ANDROID_HOME:=$HOME/android-sdk}"
# APK をいったん置く場所。WSL2 から Windows の adb を使う場合は
# Windows から見えるパスにする必要がある（dev.env で指定）
: "${APK_STAGE:=}"
: "${APK_STAGE_WIN:=}"
# 対象の実機。空なら最初に見つかった実機（エミュレータ以外）を使う
: "${DEVICE_SERIAL:=}"

export JAVA_HOME ANDROID_HOME

echo "== Android: ビルドとテスト =="
(cd "$ROOT/android" && ./gradlew assembleDebug testDebugUnitTest -q)

results="$ROOT/android/app/build/test-results/testDebugUnitTest"
passed=$(grep -h -o 'tests="[0-9]*"' "$results"/*.xml | grep -o '[0-9]*' | paste -sd+ | bc)
failed=$(grep -h -o 'failures="[0-9]*"' "$results"/*.xml | grep -o '[0-9]*' | paste -sd+ | bc)
echo "   テスト ${passed} 件 / 失敗 ${failed}"
[ "$failed" = "0" ] || { echo "テストが落ちているのでインストールしない" >&2; exit 1; }

echo "== server: テスト =="
(cd "$ROOT/server" && bun test 2>&1 | tail -3)

echo "== web: テスト =="
# node_modules は git 管理外なので、クローン直後や別の場所へ移したときは空になる。
# 毎回 install を走らせても、入っていれば一瞬で終わる
(cd "$ROOT/web" && bun install --silent && bun test test/ 2>&1 | tail -3)

echo "== 実機へインストール =="
if ! command -v "$ADB" > /dev/null 2>&1 && [ ! -x "$ADB" ]; then
  echo "adb が見つからない（ADB=$ADB）。dev.env で指定してください" >&2
  exit 1
fi

# Windows の adb は出力に CR を混ぜるので落としてから判定する。
# エミュレータが同時に繋がっていることがあるので実機だけを選ぶ
serial="$DEVICE_SERIAL"
if [ -z "$serial" ]; then
  serial=$("$ADB" devices | tr -d '\r' | awk '$2=="device" && $1 !~ /^emulator/ {print $1; exit}')
fi
if [ -z "$serial" ]; then
  echo "実機が見つからない。USB を挿して USB デバッグを許可してください" >&2
  exit 1
fi

apk="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
install_path="$apk"
if [ -n "$APK_STAGE" ]; then
  cp "$apk" "$APK_STAGE"
  install_path="${APK_STAGE_WIN:-$APK_STAGE}"
fi

"$ADB" -s "$serial" install -r "$install_path" | tr -d '\r' | tail -1
echo "   端末: $serial"

echo "== 今の記録 =="
tmp=$(mktemp -d)
for f in steps.db steps.db-wal steps.db-shm; do
  "$ADB" -s "$serial" exec-out "run-as app.stepsapp cat databases/$f" \
    > "$tmp/${f/steps.db/d.db}" 2>/dev/null || true
done
sqlite3 -header -column "$tmp/d.db" \
  'SELECT localDate, stepCount, source FROM daily_steps ORDER BY localDate DESC LIMIT 5;' 2>/dev/null \
  || echo "   (DB をまだ読めない)"
rm -rf "$tmp"
