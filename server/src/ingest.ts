import type { Database } from "bun:sqlite";

/** アプリが書き出す steps.json の形。Android 側の domain/Backup.kt と対応する。 */
export type Backup = {
  schemaVersion?: number;
  appVersion?: string;
  timeZone?: string;
  exportedAt?: number;
  days?: { localDate: string; stepCount: number; source: string; updatedAt: number }[];
  raw?: { localDate: string; source: string; stepCount: number; recordedAt: number }[];
};

export type IngestResult = {
  days: number;
  raw: number;
};

/**
 * 端末が上げた steps.json を取り込む。
 *
 * 端末が正なので日次の値はそのまま上書きする。
 * 同じファイルを何度取り込んでも結果が変わらない（冪等）。
 */
export function ingest(db: Database, backup: Backup): IngestResult {
  const days = backup.days ?? [];
  const raw = backup.raw ?? [];

  const upsertDay = db.query(
    `INSERT INTO daily_steps (local_date, step_count, source, updated_at)
     VALUES (?1, ?2, ?3, ?4)
     ON CONFLICT(local_date) DO UPDATE SET
       step_count = excluded.step_count,
       source     = excluded.source,
       updated_at = excluded.updated_at`,
  );
  const insertRaw = db.query(
    `INSERT INTO step_readings_raw (local_date, source, step_count, recorded_at)
     VALUES (?1, ?2, ?3, ?4)
     ON CONFLICT(local_date, source, recorded_at) DO NOTHING`,
  );

  const run = db.transaction(() => {
    for (const d of days) {
      if (!isValidDate(d.localDate)) throw new Error(`日付の形式が不正: ${d.localDate}`);
      if (!Number.isInteger(d.stepCount) || d.stepCount < 0) {
        throw new Error(`歩数が不正: ${d.localDate} -> ${d.stepCount}`);
      }
      upsertDay.run(d.localDate, d.stepCount, d.source, d.updatedAt ?? 0);
    }
    for (const r of raw) {
      insertRaw.run(r.localDate, r.source, r.stepCount, r.recordedAt);
    }
  });
  run();

  return { days: days.length, raw: raw.length };
}

export function isValidDate(s: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(s);
}

export function parseBackup(text: string): Backup {
  const parsed = JSON.parse(text) as unknown;
  if (typeof parsed !== "object" || parsed === null) throw new Error("JSON の形式が不正");
  return parsed as Backup;
}
