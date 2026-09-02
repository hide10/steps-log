# デプロイ（任意）

**サーバレスの Web ビューア（`web/index.html`）を使うなら、ここは要りません。**
ブラウザで開いて `steps.json` を選ぶだけで動きます。

以下は「PC に常駐サーバを置いて、ローカルのホスト名で見たい」場合の手順です。

## 1. データの置き場所を用意する

端末が書き出した `steps.json` を PC に持ってくる。

- **Google ドライブ経由（既定）**: `rclone` の remote を用意し、
  `STEPS_DRIVE_REMOTE`（既定 `gdrive:steps-app`）に設定する
- **git 経由**: `STEPS_SYNC_SOURCE=git` にして、`STEPS_DATA_REPO`
  （既定 `~/steps-data`）に clone しておく

## 2. 常駐させる

```bash
sed -e "s|@HOME@|$HOME|g" deploy/steps-app.service.example \
  > ~/.config/systemd/user/steps-app.service
systemctl --user daemon-reload
systemctl --user enable --now steps-app.service
systemctl --user status steps-app.service
```

WSL などで自動起動させたいときは `loginctl enable-linger "$USER"` も実行する。

## 3. ホスト名を割り当てる（任意）

```bash
STEPS_HOST=steps.example.lan ./deploy/apply-caddy.sh
```

追記 → `caddy validate` → 失敗したら自動で巻き戻す、という手順になっている。

> **注意**: 同じ Caddyfile を他のサイトも使っている場合、構文エラーで
> reload に失敗すると全部巻き添えになる。必ず validate してから reload すること。

## 4. 確認

```bash
curl -sf http://127.0.0.1:8430/ | head -3
```

初回は `/sync` を開いてデータを取り込む。

## 環境変数

| 変数 | 既定 | 用途 |
| --- | --- | --- |
| `STEPS_PORT` | 8430 | 待ち受けポート |
| `STEPS_SYNC_SOURCE` | `drive` | `drive` か `git` |
| `STEPS_DRIVE_REMOTE` | `gdrive:steps-app` | rclone の remote |
| `STEPS_STAGING` | `~/steps-staging` | Drive から回収したファイルの置き場 |
| `STEPS_DATA_REPO` | `~/steps-data` | git 経路のときの clone 先 |
| `STEPS_DATA_DIR` | `server/data` | SQLite の置き場 |
| `RCLONE` | `rclone` | rclone が PATH に無いとき |
