#!/usr/bin/env bash
# ダッシュボードにローカルのホスト名を割り当てる。
#
# 同じ Caddyfile を他のサイトも使っていることがあるので、
# 必ず validate してから reload する。失敗したら元に戻す。
#
#   STEPS_HOST=steps.example.lan ./deploy/apply-caddy.sh
set -euo pipefail

# ホスト名と Caddyfile の場所は環境変数で変えられる
HOST="${STEPS_HOST:-steps.home.arpa}"
CADDYFILE="${CADDYFILE:-/etc/caddy/Caddyfile}"
BACKUP=/tmp/Caddyfile.before-steps.$(date +%s)

if grep -q "$HOST" "$CADDYFILE"; then
  echo "Caddyfile には既に $HOST がある（変更しない）"
else
  sudo cp "$CADDYFILE" "$BACKUP"
  echo "バックアップ: $BACKUP"

  sudo tee -a "$CADDYFILE" > /dev/null <<BLOCK

$HOST {
	import localtls
	reverse_proxy 127.0.0.1:8430
}
BLOCK

  if ! sudo caddy validate --config "$CADDYFILE" --adapter caddyfile > /dev/null 2>&1; then
    echo "構文エラー。元に戻す" >&2
    sudo cp "$BACKUP" "$CADDYFILE"
    exit 1
  fi
  echo "構文 OK"
fi

if grep -q "$HOST" /etc/hosts; then
  echo "/etc/hosts には既にエントリがある"
else
  printf "127.0.0.1\t%s\n" "$HOST" | sudo tee -a /etc/hosts > /dev/null
  echo "/etc/hosts に追記した"
fi

sudo systemctl reload caddy
echo "caddy を reload した"

sleep 2
echo "--- 既存サービスの疎通確認 ---"
printf '  %-24s %s\n' "$HOST" "$(curl -sk -o /dev/null -w '%{http_code}' "https://$HOST/" --max-time 5 || echo NG)"
