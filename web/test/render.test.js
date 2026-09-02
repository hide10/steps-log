import { describe, expect, test } from "bun:test";
import { parseHTML } from "linkedom";

/**
 * index.html のインラインスクリプトを実際の DOM 上で走らせ、
 * 画面に出る中身を検証する。
 *
 * 集計そのものは aggregate.test.js で担保しているので、ここでは
 * 「読み込んだデータが正しく画面に反映されるか」を見る。
 */
async function renderWith(days, todayText, hash = "") {
  const html = await Bun.file(new URL("../index.html", import.meta.url)).text();
  const { document, window } = parseHTML(html);

  // localStorage は linkedom に無いので最小限の代替を用意する
  const store = new Map();
  const localStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
  };
  store.set("steps-log-data", JSON.stringify(days));

  const code = html.match(/<script type="module">([\s\S]*?)<\/script>/)[1]
    .replace(/^import[\s\S]*?from\s+"\.\/aggregate\.js";/m, "")
    // 「今日」を固定して結果を安定させる
    .replace(/function todayText\(\)[\s\S]*?\n}/, `function todayText(){return "${todayText}";}`);

  const mod = await import("../aggregate.js");
  // ブラウザなら必ずある location を用意する（#week などの初期表示に使う）
  const location = { hash, replace: () => {} };
  const fn = new Function(
    "document", "localStorage", "console", "alert", "location",
    ...Object.keys(mod),
    code,
  );
  fn(document, localStorage, console, () => {}, location, ...Object.values(mod));
  return { document, window };
}

describe("画面の描画", () => {
  const days = {
    "2026-08-24": 7000,
    "2026-08-25": 8000,
    "2026-08-26": 9000,
    "2026-08-20": 0,
  };

  test("読み込み済みなら本体を表示し、案内文を隠す", async () => {
    const { document } = await renderWith(days, "2026-08-26");
    expect(document.getElementById("content").hasAttribute("hidden")).toBe(false);
    expect(document.getElementById("placeholder").hasAttribute("hidden")).toBe(true);
  });

  test("今日の歩数がリングに出る", async () => {
    const { document } = await renderWith(days, "2026-08-26");
    expect(document.getElementById("ring").innerHTML).toContain("9,000");
  });

  test("目標達成なら達成表示になる", async () => {
    const { document } = await renderWith(days, "2026-08-26");
    expect(document.getElementById("ring").innerHTML).toContain("目標達成");
  });

  test("未達なら目標歩数のほうを出す", async () => {
    const { document } = await renderWith({ "2026-08-26": 100 }, "2026-08-26");
    const ring = document.getElementById("ring").innerHTML;
    expect(ring).toContain("6,000 歩");
    expect(ring).not.toContain("目標達成");
  });

  test("連続日数と記録日数が出る", async () => {
    const { document } = await renderWith(days, "2026-08-26");
    const stats = document.getElementById("stats").textContent;
    expect(stats).toContain("3日");     // 8/24,25,26 の3連続
    expect(stats).toContain("連続");
    expect(stats).toContain("4");       // 記録日数（0歩の日も含む）
  });

  test("一覧に日付と歩数が並ぶ", async () => {
    const { document } = await renderWith(days, "2026-08-26");
    const table = document.getElementById("table").textContent;
    expect(table).toContain("2026-08-26");
    expect(table).toContain("9,000");
    expect(table).toContain("2026-08-20");
  });

  test("グラフが本数ぶん描かれる", async () => {
    const { document } = await renderWith(days, "2026-08-26");
    const rects = document.getElementById("chart").querySelectorAll("rect");
    expect(rects.length).toBe(4);
  });

  test("データが空なら案内文を出す", async () => {
    const { document } = await renderWith({}, "2026-08-26");
    expect(document.getElementById("content").hasAttribute("hidden")).toBe(true);
    expect(document.getElementById("placeholder").hasAttribute("hidden")).toBe(false);
  });

  test("URL のハッシュで初期表示の期間を指定できる", async () => {
    const { document } = await renderWith(days, "2026-08-26", "#week");
    const pressed = document.getElementById("periods")
      .querySelector('[aria-pressed="true"]');
    expect(pressed.textContent).toBe("週");
    expect(document.getElementById("table").textContent).toContain("週の開始");
  });

  test("知らないハッシュは無視して日表示のままにする", async () => {
    const { document } = await renderWith(days, "2026-08-26", "#nonsense");
    const pressed = document.getElementById("periods")
      .querySelector('[aria-pressed="true"]');
    expect(pressed.textContent).toBe("日");
  });

  test("期間ボタンが4つある", async () => {
    const { document } = await renderWith(days, "2026-08-26");
    const buttons = document.getElementById("periods").querySelectorAll("button");
    expect(buttons.length).toBe(4);
    expect([...buttons].map((b) => b.textContent)).toEqual(["日", "週", "月", "年"]);
  });
});

describe("アクセントカラー", () => {
  const days = { "2026-08-26": 9000 };

  test("色の選択肢が Android 側と同じ数だけ出る", async () => {
    const { document } = await renderWith(days, "2026-08-26");
    const swatches = document.getElementById("accents").querySelectorAll("button");
    expect(swatches.length).toBe(6);
  });

  test("既定はブルーが選ばれている", async () => {
    const { document } = await renderWith(days, "2026-08-26");
    const pressed = document.getElementById("accents")
      .querySelector('[aria-pressed="true"]');
    expect(pressed.dataset.accent).toBe("BLUE");
  });
});
