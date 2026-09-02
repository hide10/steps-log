import { Database } from "bun:sqlite";
import { mkdirSync } from "node:fs";
import { dataDir, dbPath } from "./config";

let db: Database | null = null;
let openedPath = "";

export function openDb(): Database {
  const p = dbPath();
  if (db && openedPath === p) return db;
  db?.close();
  mkdirSync(dataDir(), { recursive: true });
  db = new Database(p);
  openedPath = p;
  initSchema(db);
  return db;
}

/** テスト用にインメモリ DB を開く。 */
export function openMemoryDb(): Database {
  const mem = new Database(":memory:");
  initSchema(mem);
  return mem;
}

function initSchema(d: Database): void {
  d.exec(`
    PRAGMA journal_mode = WAL;

    -- 採用値。1日1レコード。集計はここだけを見る。
    -- レコードが無い日 = 未計測、step_count = 0 のレコード = 実際に0歩、として区別する。
    CREATE TABLE IF NOT EXISTS daily_steps (
      local_date TEXT PRIMARY KEY,
      step_count INTEGER NOT NULL,
      source     TEXT    NOT NULL,
      updated_at INTEGER NOT NULL
    );

    -- 端末側の生ログ。daily_steps を後から作り直せるように残す。
    CREATE TABLE IF NOT EXISTS step_readings_raw (
      local_date  TEXT    NOT NULL,
      source      TEXT    NOT NULL,
      step_count  INTEGER NOT NULL,
      recorded_at INTEGER NOT NULL,
      PRIMARY KEY (local_date, source, recorded_at)
    );
    CREATE INDEX IF NOT EXISTS idx_raw_date ON step_readings_raw(local_date);

    -- 取り込みの状態（最後に git pull した時刻など）
    CREATE TABLE IF NOT EXISTS sync_state (
      id             INTEGER PRIMARY KEY CHECK (id = 1),
      last_pulled_at INTEGER
    );
  `);
}
