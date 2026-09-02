import type { Database } from "bun:sqlite";

export type Bucket = {
  /** 期間の識別子。週は「その週の月曜日」、月は YYYY-MM、年は YYYY */
  key: string;
  /** 記録がある日だけを分母にした平均（記録日平均） */
  average: number;
  total: number;
  /** 記録がある日数。0歩と記録された日も含む */
  daysRecorded: number;
  /** その期間の暦日数。欠損量を見せるために使う */
  daysInPeriod: number;
};

/**
 * その日が属する週の月曜日を求める SQL 式。
 *
 * `strftime('%Y-%W')` を使ってはいけない。月曜始まりではあるが、
 * 年をまたぐ週が分断される（2026-12-28(月)〜2027-01-03(日) は同一週なのに
 * `2026-52` と `2027-00` に割れる）。
 * また `date(d,'weekday 1','-7 days')` は月曜日そのものを1週前にずらす。
 */
const WEEK_START = `date(local_date, '-' || ((strftime('%w', local_date) + 6) % 7) || ' days')`;

/**
 * 平均の分母は「記録がある日」。
 * レコードが無い日は未計測なので分母に入れない（端末を持たずに過ごした日を
 * 0歩として平均を下げるのは実態と違う）。0歩と記録された日は分母に含める。
 */
function query(db: Database, groupExpr: string, limit: number): Omit<Bucket, "daysInPeriod">[] {
  const rows = db
    .query(
      `SELECT ${groupExpr} AS key,
              AVG(step_count)   AS average,
              SUM(step_count)   AS total,
              COUNT(*)          AS daysRecorded
         FROM daily_steps
        GROUP BY key
        ORDER BY key DESC
        LIMIT ?1`,
    )
    .all(limit) as { key: string; average: number; total: number; daysRecorded: number }[];
  return rows;
}

export function weekly(db: Database, limit = 26): Bucket[] {
  return query(db, WEEK_START, limit).map((r) => ({ ...r, daysInPeriod: 7 }));
}

export function monthly(db: Database, limit = 24): Bucket[] {
  return query(db, `strftime('%Y-%m', local_date)`, limit).map((r) => ({
    ...r,
    daysInPeriod: daysInMonth(r.key),
  }));
}

export function yearly(db: Database, limit = 20): Bucket[] {
  return query(db, `strftime('%Y', local_date)`, limit).map((r) => ({
    ...r,
    daysInPeriod: isLeapYear(Number(r.key)) ? 366 : 365,
  }));
}

/** key は "YYYY-MM" */
export function daysInMonth(key: string): number {
  const [y, m] = key.split("-").map(Number);
  return new Date(Date.UTC(y, m, 0)).getUTCDate();
}

export function isLeapYear(y: number): boolean {
  return (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
}

export type DayRow = { local_date: string; step_count: number; source: string };

export function recentDays(db: Database, limit = 60): DayRow[] {
  return db
    .query(
      `SELECT local_date, step_count, source
         FROM daily_steps
        ORDER BY local_date DESC
        LIMIT ?1`,
    )
    .all(limit) as DayRow[];
}

export function allDays(db: Database): DayRow[] {
  return db
    .query(`SELECT local_date, step_count, source FROM daily_steps ORDER BY local_date`)
    .all() as DayRow[];
}
