# steps-app - Claude Code 設定

> 設計方針は [`AGENTS.md`](AGENTS.md) を参照。

## ブランチ運用(厳守)

- **main に直接コミット・プッシュしてはならない**
- コード変更は必ずフィーチャーブランチを作成し、PR 経由でマージする

## GitHub 操作は Haiku に委譲

`gh` コマンド全般(issue / pr / repo / api)は必ず Haiku サブエージェントに委譲する
(`github-haiku` スキル)。git のローカル操作(commit / branch / merge)はメインが行う。

## 定型作業: 実機への反映

コード変更が終わったら `./dev.sh` を実行する。ビルド → テスト → 実機への
install -r まで通る。**ビルドが新しくなったら実機にも入れ直すこと**
(毎日使って気づいたことを次の改善に回すため)。

WSL2 で開発する場合、adb は Windows 側のバイナリを呼べばよく、
ソースを Windows 側へコピーする必要は無い。以前は「実機デバッグは
Windows の Android Studio」という分業でコピーしていたが、
WSL2 から実機に入れられるので不要(2026-09-06 に廃止)。

## 端末側の初回セットアップ

歩数の取りこぼしを防ぐため、実機で以下を設定する:

1. アプリに「身体活動」(ACTIVITY_RECOGNITION)権限を許可
2. **バッテリー使用量を「制限なし」に設定**
   (Samsung の "Sleeping apps" 等の OEM 独自省電力は
   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` でも防ぎきれないため手動設定が確実)
