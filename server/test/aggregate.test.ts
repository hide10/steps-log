import { describe, expect, test } from "bun:test";
import { openMemoryDb } from "../src/db";
import { daysInMonth, isLeapYear, monthly, weekly, yearly } from "../src/aggregate";

function seed(rows: [string, number][]) {
  const db = openMemoryDb();
  const stmt = db.query(
    `INSERT INTO daily_steps (local_date, step_count, source, updated_at)
     VALUES (?1, ?2, 'SENSOR', 0)`,
  );
  for (const [date, steps] of rows) stmt.run(date, steps);
  return db;
}

describe("週の集計", () => {
  test("月曜始まりでまとまる", () => {
    // 2026-08-24 は月曜。24(月)〜30(日) が同じ週。
    const db = seed([
      ["2026-08-24", 1000],
      ["2026-08-25", 2000],
      ["2026-08-30", 3000],
      ["2026-08-31", 9999], // 翌週の月曜
    ]);
    const w = weekly(db);
    const target = w.find((b) => b.key === "2026-08-24")!;
    expect(target.daysRecorded).toBe(3);
    expect(target.total).toBe(6000);
    expect(target.average).toBe(2000);
    // 翌週は別バケット
    expect(w.find((b) => b.key === "2026-08-31")!.daysRecorded).toBe(1);
  });

  test("月曜日そのものが1週前にずれない", () => {
    // date(d,'weekday 1','-7 days') を使うとここが壊れる
    const db = seed([["2026-08-24", 500]]);
    expect(weekly(db)[0].key).toBe("2026-08-24");
  });

  test("年をまたぐ週が分断されない", () => {
    // 2026-12-28(月)〜2027-01-03(日) は同一週。
    // strftime('%Y-%W') だと 2026-52 と 2027-00 に割れてしまう。
    const db = seed([
      ["2026-12-28", 1000],
      ["2026-12-31", 2000],
      ["2027-01-01", 3000],
      ["2027-01-03", 4000],
    ]);
    const w = weekly(db);
    const target = w.filter((b) => b.key === "2026-12-28");
    expect(target).toHaveLength(1);
    expect(target[0].daysRecorded).toBe(4);
    expect(target[0].total).toBe(10000);
  });

  test("月をまたぐ週は1つのバケットにまとまる", () => {
    // 2026-08-31(月)〜2026-09-06(日)
    const db = seed([
      ["2026-08-31", 1000],
      ["2026-09-01", 2000],
    ]);
    const w = weekly(db);
    expect(w).toHaveLength(1);
    expect(w[0].key).toBe("2026-08-31");
    expect(w[0].daysRecorded).toBe(2);
  });

  test("日曜日は前の月曜の週に属する", () => {
    const db = seed([["2026-08-30", 700]]); // 日曜
    expect(weekly(db)[0].key).toBe("2026-08-24");
  });
});

describe("平均の分母", () => {
  test("記録が無い日は分母に入れない", () => {
    // 3日分しか記録が無い週。平均は 3 で割る（7 で割らない）
    const db = seed([
      ["2026-08-24", 3000],
      ["2026-08-25", 3000],
      ["2026-08-26", 3000],
    ]);
    const w = weekly(db)[0];
    expect(w.daysRecorded).toBe(3);
    expect(w.average).toBe(3000);
    expect(w.daysInPeriod).toBe(7);
  });

  test("0歩と記録された日は分母に含める", () => {
    // 「未計測」と「実際に0歩」を区別する。0歩は平均を下げる。
    const db = seed([
      ["2026-08-24", 3000],
      ["2026-08-25", 0],
    ]);
    const w = weekly(db)[0];
    expect(w.daysRecorded).toBe(2);
    expect(w.average).toBe(1500);
  });
});

describe("月と年の集計", () => {
  test("月ごとにまとまる", () => {
    const db = seed([
      ["2026-08-01", 1000],
      ["2026-08-31", 3000],
      ["2026-09-01", 5000],
    ]);
    const m = monthly(db);
    const aug = m.find((b) => b.key === "2026-08")!;
    expect(aug.daysRecorded).toBe(2);
    expect(aug.average).toBe(2000);
    expect(aug.daysInPeriod).toBe(31);
  });

  test("年ごとにまとまる", () => {
    const db = seed([
      ["2026-01-01", 1000],
      ["2026-12-31", 3000],
      ["2027-01-01", 5000],
    ]);
    const y = yearly(db);
    expect(y.find((b) => b.key === "2026")!.average).toBe(2000);
    expect(y.find((b) => b.key === "2027")!.daysRecorded).toBe(1);
  });

  test("閏年の日数を正しく数える", () => {
    expect(isLeapYear(2024)).toBe(true);
    expect(isLeapYear(2026)).toBe(false);
    expect(isLeapYear(2000)).toBe(true);
    expect(isLeapYear(1900)).toBe(false);

    const db = seed([["2024-02-29", 1234]]);
    expect(yearly(db)[0].daysInPeriod).toBe(366);
    expect(monthly(db)[0].daysInPeriod).toBe(29);
  });

  test("各月の日数", () => {
    expect(daysInMonth("2026-01")).toBe(31);
    expect(daysInMonth("2026-02")).toBe(28);
    expect(daysInMonth("2024-02")).toBe(29);
    expect(daysInMonth("2026-04")).toBe(30);
    expect(daysInMonth("2026-12")).toBe(31);
  });

  test("2月29日は閏年の週にも正しく入る", () => {
    // 2024-02-29 は木曜。その週の月曜は 2024-02-26
    const db = seed([["2024-02-29", 800]]);
    expect(weekly(db)[0].key).toBe("2024-02-26");
  });
});
