import { describe, expect, test } from "bun:test";
import { openMemoryDb } from "../src/db";
import { ingest, isValidDate, parseBackup, type Backup } from "../src/ingest";
import { allDays } from "../src/aggregate";

const sample: Backup = {
  schemaVersion: 1,
  timeZone: "Asia/Tokyo",
  exportedAt: 1000,
  days: [
    { localDate: "2026-08-24", stepCount: 8123, source: "HEALTH_CONNECT", updatedAt: 1 },
    { localDate: "2026-08-25", stepCount: 0, source: "SENSOR", updatedAt: 2 },
  ],
  raw: [{ localDate: "2026-08-24", source: "SENSOR", stepCount: 40000, recordedAt: 10 }],
};

describe("取り込み", () => {
  test("日次と生ログを取り込む", () => {
    const db = openMemoryDb();
    const r = ingest(db, sample);
    expect(r.days).toBe(2);
    expect(r.raw).toBe(1);
    expect(allDays(db)).toHaveLength(2);
  });

  test("同じファイルを2回取り込んでも重複しない（冪等）", () => {
    const db = openMemoryDb();
    ingest(db, sample);
    ingest(db, sample);

    expect(allDays(db)).toHaveLength(2);
    const raw = db.query("SELECT COUNT(*) AS n FROM step_readings_raw").get() as { n: number };
    expect(raw.n).toBe(1);
  });

  test("端末側で更新された値で上書きされる", () => {
    const db = openMemoryDb();
    ingest(db, sample);
    ingest(db, {
      days: [{ localDate: "2026-08-24", stepCount: 9999, source: "SENSOR", updatedAt: 5 }],
    });

    const row = allDays(db).find((d) => d.local_date === "2026-08-24")!;
    expect(row.step_count).toBe(9999);
    expect(row.source).toBe("SENSOR");
  });

  test("0歩の記録は残る（未計測として消えない）", () => {
    const db = openMemoryDb();
    ingest(db, sample);
    const row = allDays(db).find((d) => d.local_date === "2026-08-25")!;
    expect(row.step_count).toBe(0);
  });

  test("空のバックアップでも壊れない", () => {
    const db = openMemoryDb();
    expect(ingest(db, {})).toEqual({ days: 0, raw: 0 });
  });

  test("日付の形式が不正なら例外にする", () => {
    const db = openMemoryDb();
    expect(() =>
      ingest(db, { days: [{ localDate: "2026/08/24", stepCount: 1, source: "SENSOR", updatedAt: 0 }] }),
    ).toThrow("日付の形式が不正");
  });

  test("負の歩数は例外にする", () => {
    const db = openMemoryDb();
    expect(() =>
      ingest(db, { days: [{ localDate: "2026-08-24", stepCount: -1, source: "SENSOR", updatedAt: 0 }] }),
    ).toThrow("歩数が不正");
  });

  test("不正な行があれば1件も書き込まない（トランザクション）", () => {
    const db = openMemoryDb();
    expect(() =>
      ingest(db, {
        days: [
          { localDate: "2026-08-24", stepCount: 100, source: "SENSOR", updatedAt: 0 },
          { localDate: "こわれた", stepCount: 200, source: "SENSOR", updatedAt: 0 },
        ],
      }),
    ).toThrow();
    expect(allDays(db)).toHaveLength(0);
  });

  test("日付の検証", () => {
    expect(isValidDate("2026-08-24")).toBe(true);
    expect(isValidDate("2026-8-4")).toBe(false);
    expect(isValidDate("")).toBe(false);
  });

  test("JSON をパースできる", () => {
    expect(parseBackup(JSON.stringify(sample)).days).toHaveLength(2);
    expect(() => parseBackup("null")).toThrow("JSON の形式が不正");
  });
});

describe("同期経路の選択", () => {
  test("既定は Google ドライブ", async () => {
    // 2026-08-26 に GitHub から Drive へ統一した
    delete process.env.STEPS_SYNC_SOURCE;
    const { syncSource } = await import("../src/config");
    expect(syncSource()).toBe("drive");
  });

  test("明示すれば git も選べる", async () => {
    process.env.STEPS_SYNC_SOURCE = "git";
    const { syncSource } = await import("../src/config");
    expect(syncSource()).toBe("git");
    delete process.env.STEPS_SYNC_SOURCE;
  });
});
