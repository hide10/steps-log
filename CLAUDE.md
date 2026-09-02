# steps-app - Claude Code 設定

> 設計方針は [`AGENTS.md`](AGENTS.md) を参照。

## ブランチ運用(厳守)

- **main に直接コミット・プッシュしてはならない**
- コード変更は必ずフィーチャーブランチを作成し、PR 経由でマージする

## GitHub 操作は Haiku に委譲

`gh` コマンド全般(issue / pr / repo / api)は必ず Haiku サブエージェントに委譲する
(`github-haiku` スキル)。git のローカル操作(commit / branch / merge)はメインが行う。

## 定型作業: Windows 側へのファイルコピー

開発は WSL2、実機デバッグは Windows 側 Android Studio という分業。
WSL2 側でできるのは `assembleDebug` / `test` によるビルド検証まで。

- **WSL2 ソース**: `<このリポジトリ>/android/app/src/`
- **Windows 先**: `/mnt/c/Users/<あなた>/projects/steps-app-android/app/src/`

コード変更の完了時に、変更ファイルのコピーを提案すること。

## 端末側の初回セットアップ

歩数の取りこぼしを防ぐため、実機で以下を設定する:

1. アプリに「身体活動」(ACTIVITY_RECOGNITION)権限を許可
2. **バッテリー使用量を「制限なし」に設定**
   (Samsung の "Sleeping apps" 等の OEM 独自省電力は
   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` でも防ぎきれないため手動設定が確実)
