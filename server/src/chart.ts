/**
 * 依存を増やさないため、SVG は自前で組み立てる。
 * notes-app と同じく「Bun 標準 + Hono だけ」で通す方針。
 */

export function esc(s: string): string {
  return s
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

export type BarDatum = { label: string; value: number };

/**
 * 横スクロールできる棒グラフ。
 * 値が全て0でも潰れないように、最大値には下限を設ける。
 */
export function barChart(data: BarDatum[], opts: { height?: number; barWidth?: number } = {}): string {
  if (data.length === 0) return `<p class="empty">データがありません</p>`;

  const height = opts.height ?? 160;
  const barWidth = opts.barWidth ?? 28;
  const gap = 6;
  const labelBand = 34;
  const width = data.length * (barWidth + gap) + gap;
  const max = Math.max(1, ...data.map((d) => d.value));

  const bars = data
    .map((d, i) => {
      const h = Math.round((d.value / max) * height);
      const x = gap + i * (barWidth + gap);
      const y = height - h;
      return (
        `<rect x="${x}" y="${y}" width="${barWidth}" height="${h}" rx="3" class="bar">` +
        `<title>${esc(d.label)}: ${d.value.toLocaleString()} 歩</title></rect>` +
        `<text x="${x + barWidth / 2}" y="${height + 14}" class="tick">${esc(shortLabel(d.label))}</text>`
      );
    })
    .join("");

  return (
    `<div class="scroll"><svg width="${width}" height="${height + labelBand}" ` +
    `viewBox="0 0 ${width} ${height + labelBand}" role="img">${bars}</svg></div>`
  );
}

/** 軸ラベルが潰れないよう、日付は末尾だけ見せる。 */
function shortLabel(label: string): string {
  const parts = label.split("-");
  if (parts.length === 3) return `${parts[1]}/${parts[2]}`;
  if (parts.length === 2) return parts[1];
  return label;
}
