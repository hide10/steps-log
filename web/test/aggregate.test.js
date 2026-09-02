import { describe, expect, test } from "bun:test";
import {
  aggregate, achievedRatio, compareAverages, currentStreak,
  daysInMonth, isLeapYear, longestStreak, weekStart,
} from "../aggregate.js";

describe("週の境界（Kotlin/SQL と一致していること）", () => {
  test("SQL の期待値 1200 日ぶんと完全に一致する", async () => {
    const text = await Bun.file(new URL("./sql-week-starts.csv", import.meta.url)).text();
    const lines = text.split("\n").filter((l) => l.trim());
    expect(lines.length).toBe(1200);
    for (const line of lines) {
      const [date, expected] = line.split(",");
      expect(`${date} -> ${weekStart(date)}`).toBe(`${date} -> ${expected}`);
    }
  });

  test("月曜日はそれ自身が週の開始日", () => {
    expect(weekStart("2026-08-24")).toBe("2026-08-24");
  });

  test("年をまたぐ週が分断されない", () => {
    for (const d of ["2026-12-28", "2026-12-31", "2027-01-01", "2027-01-03"]) {
      expect(weekStart(d)).toBe("2026-12-28");
    }
  });
});

describe("集計", () => {
  test("平均の分母は記録がある日だけ", () => {
    const w = aggregate({ "2026-08-24": 3000, "2026-08-25": 3000, "2026-08-26": 3000 }, "week")[0];
    expect(w.daysRecorded).toBe(3);
    expect(w.average).toBe(3000);
    expect(w.daysInPeriod).toBe(7);
  });

  test("0歩の記録は分母に含める", () => {
    const w = aggregate({ "2026-08-24": 3000, "2026-08-25": 0 }, "week")[0];
    expect(w.daysRecorded).toBe(2);
    expect(w.average).toBe(1500);
  });

  test("年をまたぐ週が1バケットになる", () => {
    const w = aggregate({
      "2026-12-28": 1000, "2026-12-31": 2000,
      "2027-01-01": 3000, "2027-01-03": 4000,
    }, "week");
    expect(w.length).toBe(1);
    expect(w[0].total).toBe(10000);
  });

  test("閏年の日数", () => {
    expect(isLeapYear(2024)).toBe(true);
    expect(isLeapYear(2026)).toBe(false);
    expect(daysInMonth("2024-02")).toBe(29);
    expect(daysInMonth("2026-02")).toBe(28);
  });

  test("新しい期間が先頭に来る", () => {
    const m = aggregate({ "2026-08-01": 1, "2026-09-01": 2 }, "month");
    expect(m[0].key).toBe("2026-09");
  });
});

describe("ストリーク", () => {
  const goal = 6000;

  test("連続達成を数える", () => {
    expect(currentStreak(
      { "2026-08-24": 7000, "2026-08-25": 8000, "2026-08-26": 9000 }, "2026-08-26", goal,
    )).toBe(3);
  });

  test("未計測の日では切れない", () => {
    expect(currentStreak(
      { "2026-08-24": 7000, "2026-08-26": 9000 }, "2026-08-26", goal,
    )).toBe(2);
  });

  test("0歩の記録では切れる", () => {
    expect(currentStreak(
      { "2026-08-24": 7000, "2026-08-25": 0, "2026-08-26": 9000 }, "2026-08-26", goal,
    )).toBe(1);
  });

  test("当日が未達でも切らない", () => {
    expect(currentStreak(
      { "2026-08-24": 7000, "2026-08-25": 8000, "2026-08-26": 100 }, "2026-08-26", goal,
    )).toBe(2);
  });

  test("記録が無ければ0", () => {
    expect(currentStreak({}, "2026-08-26", goal)).toBe(0);
  });

  test("最長ストリーク", () => {
    expect(longestStreak({
      "2026-08-01": 9000, "2026-08-02": 9000, "2026-08-03": 9000,
      "2026-08-04": 100,
      "2026-08-05": 9000, "2026-08-06": 9000,
    }, goal)).toBe(3);
  });
});

describe("達成率と比較", () => {
  test("達成率は1で頭打ち", () => {
    expect(achievedRatio(3000, 6000)).toBe(0.5);
    expect(achievedRatio(99999, 6000)).toBe(1);
  });

  test("期間の途中では同じ経過日数までで比べる", () => {
    const diff = compareAverages(
      { "2026-08-01": 10000, "2026-08-02": 10000 },
      { "2026-07-01": 10000, "2026-07-02": 10000, "2026-07-03": 0, "2026-07-04": 0 },
      2,
    );
    expect(diff).toBe(0);
  });
});
