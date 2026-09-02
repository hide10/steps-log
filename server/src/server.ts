import { Hono } from "hono";
import { PORT } from "./config";
import { openDb } from "./db";
import { allDays, monthly, recentDays, weekly, yearly, type Bucket } from "./aggregate";
import { barChart, esc } from "./chart";
import { lastPulledAt, maybePullInBackground, pullAndIngest } from "./sync";

export const app = new Hono();

const CSS = `
  :root { color-scheme: light dark; }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 24px; font-family: system-ui, sans-serif; line-height: 1.6; }
  main { max-width: 900px; margin: 0 auto; }
  h1 { font-size: 1.4rem; margin: 0 0 4px; }
  h2 { font-size: 1.1rem; margin: 32px 0 8px; }
  .muted { opacity: .65; font-size: .85rem; }
  .scroll { overflow-x: auto; padding-bottom: 4px; }
  .bar { fill: #4c8bf5; }
  .tick { font-size: 9px; text-anchor: middle; fill: currentColor; opacity: .6; }
  table { border-collapse: collapse; width: 100%; font-size: .9rem; }
  th, td { text-align: right; padding: 6px 8px; border-bottom: 1px solid rgba(128,128,128,.25); }
  th:first-child, td:first-child { text-align: left; }
  .empty { opacity: .6; }
  nav a, .btn { display: inline-block; margin-right: 12px; }
`;

function layout(title: string, body: string): string {
  return `<!doctype html><html lang="ja"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title><style>${CSS}</style></head>
<body><main>${body}</main></body></html>`;
}

function bucketTable(buckets: Bucket[], unitLabel: string): string {
  if (buckets.length === 0) return `<p class="empty">データがありません</p>`;
  const rows = buckets
    .map(
      (b) =>
        `<tr><td>${esc(b.key)}</td>` +
        `<td>${Math.round(b.average).toLocaleString()}</td>` +
        `<td>${b.total.toLocaleString()}</td>` +
        `<td>${b.daysRecorded} / ${b.daysInPeriod}</td></tr>`,
    )
    .join("");
  return `<table><thead><tr>
      <th>${esc(unitLabel)}</th><th>平均</th><th>合計</th><th>記録日数</th>
    </tr></thead><tbody>${rows}</tbody></table>`;
}

function section(title: string, buckets: Bucket[], unitLabel: string): string {
  const chart = barChart(
    buckets
      .slice()
      .reverse()
      .map((b) => ({ label: b.key, value: Math.round(b.average) })),
  );
  return `<h2>${esc(title)}</h2>${chart}${bucketTable(buckets, unitLabel)}`;
}

app.get("/", (c) => {
  const db = openDb();
  // リクエストは待たせず、必要なら裏で引き直す
  maybePullInBackground(db);

  const days = recentDays(db, 30);
  const pulled = lastPulledAt(db);
  const pulledText = pulled ? new Date(pulled).toLocaleString("ja-JP") : "まだ取り込んでいません";

  const total = db.query("SELECT COUNT(*) AS n FROM daily_steps").get() as { n: number };

  const body = `
    <h1>歩数ログ</h1>
    <p class="muted">記録日数 ${total.n} 日 ・ 最終取り込み ${esc(pulledText)}</p>
    <nav>
      <a class="btn" href="/sync">いま取り込む</a>
      <a class="btn" href="/export.csv">CSV をダウンロード</a>
    </nav>

    <h2>直近30日</h2>
    ${barChart(
      days
        .slice()
        .reverse()
        .map((d) => ({ label: d.local_date, value: d.step_count })),
    )}

    ${section("週ごとの平均（月曜始まり）", weekly(db, 26), "週の開始")}
    ${section("月ごとの平均", monthly(db, 24), "月")}
    ${section("年ごとの平均", yearly(db, 20), "年")}

    <p class="muted">平均は記録がある日だけを分母にしています（未計測の日は含めません）。
    「記録日数」は 記録がある日 / その期間の暦日数 です。</p>
  `;
  return c.html(layout("歩数ログ", body));
});

app.get("/sync", async (c) => {
  const db = openDb();
  const result = await pullAndIngest(db);
  const body = `<h1>取り込み</h1>
    <p>${esc(result.message)}</p>
    <p><a href="/">戻る</a></p>`;
  return c.html(layout("取り込み", body), result.ok ? 200 : 500);
});

app.get("/export.csv", (c) => {
  const db = openDb();
  const rows = allDays(db)
    .map((d) => `${d.local_date},${d.step_count},${d.source}`)
    .join("\n");
  const csv = `local_date,step_count,source\n${rows}\n`;
  return new Response(csv, {
    headers: {
      "content-type": "text/csv; charset=utf-8",
      "content-disposition": 'attachment; filename="steps.csv"',
    },
  });
});

if (import.meta.main) {
  console.log(`steps-app server listening on http://127.0.0.1:${PORT}`);
  Bun.serve({ port: PORT, fetch: app.fetch });
}
