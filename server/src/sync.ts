import { existsSync } from "node:fs";
import path from "node:path";
import type { Database } from "bun:sqlite";
import { ingest, parseBackup } from "./ingest";
import { mkdirSync } from "node:fs";
import {
  dataRepoDir,
  driveRemote,
  rclonePath,
  stagingDir,
  stepsJsonPath,
  syncSource,
} from "./config";

/** 前回の取り込みからこの時間が経っていれば、ページ表示のついでに引き直す。 */
const PULL_INTERVAL_MS = 5 * 60 * 1000;

let pulling = false;

export function lastPulledAt(db: Database): number | null {
  const row = db.query("SELECT last_pulled_at AS t FROM sync_state WHERE id = 1").get() as
    | { t: number | null }
    | null;
  return row?.t ?? null;
}

function markPulled(db: Database, at: number): void {
  db.query(
    `INSERT INTO sync_state (id, last_pulled_at) VALUES (1, ?1)
     ON CONFLICT(id) DO UPDATE SET last_pulled_at = excluded.last_pulled_at`,
  ).run(at);
}

/**
 * 設定された経路でデータを回収して取り込む。
 *
 * GitHub と Drive は排他。両方に上げると復元元が2つになり、
 * どちらが正なのか曖昧になるため。
 */
export async function pullAndIngest(db: Database): Promise<{ ok: boolean; message: string }> {
  return syncSource() === "drive" ? pullFromDrive(db) : pullFromGit(db);
}

/**
 * rclone で Google ドライブから回収する。
 *
 * rclone の gdrive: リモートを使う。
 * `move` ではなく `copy --update` にする。
 * Drive 側は端末が上書きし続けるバックアップ元でもあるので消さない。
 */
async function pullFromDrive(db: Database): Promise<{ ok: boolean; message: string }> {
  const staging = stagingDir();
  mkdirSync(staging, { recursive: true });

  const proc = Bun.spawn(
    [rclonePath(), "copy", driveRemote(), staging, "--update", "--quiet"],
    { stdout: "pipe", stderr: "pipe" },
  );
  const code = await proc.exited;
  if (code !== 0) {
    const err = await new Response(proc.stderr).text();
    return { ok: false, message: `rclone に失敗: ${err.trim() || "終了コード " + code}` };
  }

  const file = path.join(staging, "steps.json");
  if (!existsSync(file)) {
    return { ok: false, message: `steps.json が見つからない: ${file}` };
  }

  const text = await Bun.file(file).text();
  const result = ingest(db, parseBackup(text));
  markPulled(db, Date.now());
  return { ok: true, message: `${result.days}日分を取り込んだ（Drive）` };
}

/** データ用リポジトリを git pull して steps.json を取り込む。 */
async function pullFromGit(db: Database): Promise<{ ok: boolean; message: string }> {
  const repo = dataRepoDir();
  if (!existsSync(path.join(repo, ".git"))) {
    return { ok: false, message: `データ用リポジトリが見つからない: ${repo}` };
  }

  const proc = Bun.spawn(["git", "pull", "--ff-only", "--quiet"], {
    cwd: repo,
    stdout: "pipe",
    stderr: "pipe",
  });
  const code = await proc.exited;
  if (code !== 0) {
    const err = await new Response(proc.stderr).text();
    return { ok: false, message: `git pull に失敗: ${err.trim()}` };
  }

  const file = stepsJsonPath();
  if (!existsSync(file)) {
    return { ok: false, message: `steps.json が見つからない: ${file}` };
  }

  const text = await Bun.file(file).text();
  const result = ingest(db, parseBackup(text));
  markPulled(db, Date.now());
  return { ok: true, message: `${result.days}日分を取り込んだ（GitHub）` };
}

/**
 * ページ表示のついでに、必要なら裏で取り込む。
 *
 * リクエストを待たせないよう await しない。取り込みが終わったあとに
 * 再読み込みすれば最新が出る、という割り切り。
 */
export function maybePullInBackground(db: Database): void {
  if (pulling) return;
  const last = lastPulledAt(db);
  if (last !== null && Date.now() - last < PULL_INTERVAL_MS) return;

  pulling = true;
  pullAndIngest(db)
    .then((r) => {
      if (!r.ok) console.warn(`[sync] ${r.message}`);
    })
    .catch((e) => console.warn("[sync] 取り込みに失敗", e))
    .finally(() => {
      pulling = false;
    });
}
