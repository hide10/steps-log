/** サーバの設定値。環境変数で上書きできる。 */
import path from "node:path";

export const PORT = Number(process.env.STEPS_PORT ?? 8430);

function homeDir(): string {
  return process.env.HOME ?? process.env.USERPROFILE ?? ".";
}

/** SQLite の置き場所。/server/data/ は .gitignore 済み。 */
export function dataDir(): string {
  return process.env.STEPS_DATA_DIR ?? path.join(import.meta.dir, "..", "data");
}

export function dbPath(): string {
  return path.join(dataDir(), "steps.db");
}

/** 端末が steps.json を push してくるデータ用リポジトリ（コードとは別リポジトリ）。 */
export function dataRepoDir(): string {
  return process.env.STEPS_DATA_REPO ?? path.join(homeDir(), "steps-data");
}

export function stepsJsonPath(): string {
  return path.join(dataRepoDir(), "steps.json");
}

/**
 * 同期の経路。既定は Google ドライブ。
 *
 * 2026-08-26 に GitHub から Drive へ統一した。git 経路は当面残すが、
 * 使うときだけ STEPS_SYNC_SOURCE=git を明示する。
 */
export type SyncSource = "git" | "drive";

export function syncSource(): SyncSource {
  return process.env.STEPS_SYNC_SOURCE === "git" ? "git" : "drive";
}

/** rclone のリモートパス。 */
export function driveRemote(): string {
  return process.env.STEPS_DRIVE_REMOTE ?? "gdrive:steps-app";
}

/** rclone の場所。PATH に無ければ RCLONE で指定する。 */
export function rclonePath(): string {
  return process.env.RCLONE ?? "rclone";
}

/** Drive から回収したファイルの置き場。 */
export function stagingDir(): string {
  return process.env.STEPS_STAGING ?? path.join(homeDir(), "steps-staging");
}
