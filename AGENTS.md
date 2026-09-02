# steps-app

> 自分専用の歩数計測・ログ化システム。Android アプリで歩数を記録し、
> ローカル Web ダッシュボードで週/月/年の平均を見る。

## 構成

| 領域 | 技術 |
|------|------|
| Android | Kotlin 2.1.0 / Jetpack Compose + Material 3 / Room / WorkManager / Health Connect |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| Compile SDK | 36 (connect-client 1.1.0 の要求) |
| Gradle / AGP | 8.13 / 8.11.1 |
| サーバ | Bun + TypeScript + Hono + bun:sqlite |
| 同期 | Google ドライブの `steps.json`（2026-08-26 に GitHub から変更） |

```
steps-app/
├── android/    # Kotlin + Compose
└── server/     # Bun + Hono + bun:sqlite
```

## 設計方針

### 歩数の取得: 2ソース対応、ただし「足さない」

`SENSOR`(TYPE_STEP_COUNTER) と `HEALTH_CONNECT` の両方から取得するが、
**1日の採用値は必ずどちらか一方**とする。合算しないことが重複カウント防止の核心。

- **採るのは大きいほう**（2026-08-31 変更）。歩数の食い違いはほぼ「取りこぼし」なので、
  大きい値のほうが実態に近い。同数なら Health Connect（ウェアラブル分も含むため）
- 当日のリアルタイム表示のみ SENSOR を使う(HC は最大1分粒度のバッチ)

> **なぜ固定優先をやめたか。** 当初は「HC が使えるならそちら、駄目なら SENSOR」
> だった。しかし実機で、センサーが 10,250 歩を数えている日に HC が 5,045 しか
> 返さない状態が続いた（HC にデータを書く側の同期が遅れていた）。
> 「読めない」だけでなく**「読めるが少ない」**場合があることが抜けていた。
> 合算しない原則はそのままで、選び方だけを「大きいほう」に変えた。

#### Health Connect の重複排除は aggregate に任せる

`aggregate()` は**ユーザーが設定したアプリの優先順位リストに基づいて重複を自動で除外し、
最優先アプリの値だけを残す**（公式ドキュメント "Aggregate data affected by
user-selected apps priorities"）。したがって `dataOriginFilter` で
端末由来のデータを選り分ける必要はない。

> When you perform an aggregate read, the Aggregate API accounts for any duplicate
> data and keeps only the data from the app with the highest priority.

**注意**: 当初「2026年6月の SPN 変更に対応するため `getCurrentDeviceDataSource()` で
動的取得せよ」という方針で計画していたが、**この API は connect-client の
1.1.0 にも 1.2.0-alpha05 にも存在しない**（aar を展開して確認済み）。
そもそも aggregate 側が重複を処理するため不要。実在しない API を前提にしないこと。

### バックグラウンド記録: FGS を使わない

`TYPE_STEP_COUNTER` はハードウェアカウンタで Doze 中も数え続けるため、
常駐サービスは構造的に不要。WorkManager の定期実行(15分間隔)で読み出す。

- 再起動でセンサーの累積値がリセットされる → `今回値 < 前回値` で再起動を検知しオフセットを打ち直す
- `BOOT_COMPLETED` で WorkManager を組み直し、基準値を再初期化する

### データの持ち方

- `local_date` は**端末ローカルの暦日**(`YYYY-MM-DD`)。UTC 変換はしない
- **レコードが無い日 = 未計測**、**`step_count = 0` のレコード = 実際に0歩**。厳密に区別する
- `daily_steps`(採用値)は `step_readings_raw`(生ログ)から常に作り直せるようにしておく

### 週の集計(ハマりどころ)

**`strftime('%Y-%W')` でグルーピングしてはいけない。** 年をまたぐ週が分断される
(2026-12-28(月)〜2027-01-03(日) は同一週だが `2026-52` と `2027-00` に割れる)。
また `date(d,'weekday 1','-7 days')` は月曜日そのものを1週前にずらすバグがある。

正しくは「その週の月曜日」を次の式で求め、その日付でグルーピングする:

```sql
date(local_date, '-' || ((strftime('%w', local_date) + 6) % 7) || ' days')
```

### 平均の分母

**記録がある日だけを分母にする**(記録日平均)。端末を持たずに過ごした日を0歩として
平均を下げるのは実態と違うため。明示的に0歩と記録された日は分母に含める。
画面には「集計対象日数 / 期間日数」を併記して欠損量を可視化する。

## バックアップ先は Google ドライブに統一（2026-08-26 決定）

当初は GitHub private repo に上げていたが、**筋が悪いという判断で Drive に統一**した。
GitHub 方式（PAT + Contents API）は残骸を残さず削除する。

理由: 歩数の記録はコードではなくデータで、リポジトリの履歴として持つのは用途が合わない。
PC 側で rclone の `gdrive:` リモートが使える場合は、
取り込み経路もそちらに揃うほうが素直。

**世代管理の穴に注意**: git は自動で履歴を持ってくれたが、Drive は上書きすると
前の版が残らない。PC 側で取り込んだあとに別のリポジトリへ commit するか、
Drive 側を日付別ファイルにするか、どちらかで補う必要がある。

## 本家（StepsApp）の参照先（2026-08-30 追記）

**機能を伸ばすときは本家の公式ページを見る。** まとめ記事や二次情報ではなく、
一次情報にあたること（機能一覧が更新されるのはこちらが先）。

- Google Play: https://play.google.com/store/apps/details?id=com.stepsappgmbh.stepsapp&hl=ja
- App Store: https://apps.apple.com/jp/app/stepsapp-%E6%AD%A9%E6%95%B0%E8%A8%88/id1037595083

見るのは**説明文の機能一覧とレビュー**。レビューは褒められている点（伸ばす対象）と
不満点（同じ轍を踏まないための材料）の両方に効く。実際、
「目標を変えると過去が全部未達成になる」という不満は、うちも同じ作りだったことが
これで分かった。

ただし**取り込むかどうかは方針が優先する。** 本家の主要機能でも下記は入れない。

## 対象外とする機能（2026-08-26 決定）

**他人と比べる・争う要素はすべて作らない。** 本家 StepsApp の主要機能である
チャレンジ / チームチャレンジ / ランキング / フレンド / チャットは対象外。
これは自分専用アプリなので、他人が必要な機能は前提と衝突する。

競争の楽しさが欲しい場合は**過去の自分と比べる**形に読み替える
（前週比・前月比、自己ベスト、目標ペースとの差）。

## コーディング規約

- ViewModel: `AndroidViewModel` + `StateFlow<UiState>`
- Composable: stateless、状態は ViewModel から `collectAsStateWithLifecycle()`
- DI フレームワークは使わない(`@Volatile` シングルトンで足りる規模)
- 日本語 UI テキスト
- **再起動オフセット計算・日跨ぎ・ソース採用ルール・衝突解決は
  Android 非依存の純粋関数として切り出し、必ず JVM ユニットテストを書く**

## ビルドと検証

```bash
# Android(WSL2 で完結)
cd android
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ANDROID_HOME=<Android SDK のパス> ./gradlew assembleDebug
ANDROID_HOME=<Android SDK のパス> ./gradlew test

# サーバ
cd server
bun test
bun src/server.ts    # http://127.0.0.1:8430
```

環境ごとのパスは `dev.env` に書く(`dev.env.example` 参照)。
`./dev.sh` でビルドからテスト、実機インストールまで一息で通る。
