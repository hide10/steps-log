import { describe, expect, test } from "bun:test";
import { barChart, esc } from "../src/chart";

describe("SVG グラフ", () => {
  test("データが無ければグラフを描かない", () => {
    expect(barChart([])).toContain("データがありません");
  });

  test("本数ぶんの棒を描く", () => {
    const svg = barChart([
      { label: "2026-08-24", value: 100 },
      { label: "2026-08-25", value: 200 },
    ]);
    expect(svg.match(/<rect/g)).toHaveLength(2);
  });

  test("全て0でも高さ計算で壊れない", () => {
    const svg = barChart([{ label: "2026-08-24", value: 0 }]);
    expect(svg).toContain("<rect");
    expect(svg).not.toContain("NaN");
    expect(svg).not.toContain("Infinity");
  });

  test("最大値の棒が最も高い", () => {
    const svg = barChart([
      { label: "a", value: 10 },
      { label: "b", value: 100 },
    ]);
    const heights = [...svg.matchAll(/height="(\d+)" rx/g)].map((m) => Number(m[1]));
    expect(heights[1]).toBeGreaterThan(heights[0]);
  });

  test("ラベルをエスケープする", () => {
    const svg = barChart([{ label: '<script>&"', value: 1 }]);
    expect(svg).not.toContain("<script>");
    expect(svg).toContain("&lt;script&gt;");
  });

  test("HTML エスケープ", () => {
    expect(esc('<a href="x">&</a>')).toBe("&lt;a href=&quot;x&quot;&gt;&amp;&lt;/a&gt;");
  });
});
