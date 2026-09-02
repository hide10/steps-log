package app.stepsapp.domain

/** インポート時に既存レコードとぶつかったときの解決方法。 */
enum class ImportMode {
    /** 既存を優先し、インポート側を捨てる。 */
    SKIP,

    /** インポート側で上書きする。 */
    OVERWRITE,

    /**
     * 歩数の大きいほうを採用する（既定）。
     * 歩数の誤差はほぼ「取りこぼし」なので、大きい値のほうが実態に近い。
     */
    MERGE,
}

/**
 * 1日ぶんの衝突を解決する。副作用を持たない純粋関数。
 *
 * @return 書き込むべき値。書き込む必要がなければ null
 */
fun resolveConflict(existing: ExportDay?, incoming: ExportDay, mode: ImportMode): ExportDay? {
    if (existing == null) return incoming

    return when (mode) {
        ImportMode.SKIP -> null
        ImportMode.OVERWRITE -> incoming
        ImportMode.MERGE ->
            // 同数なら既存のままにして無用な書き換えを避ける
            if (incoming.stepCount > existing.stepCount) incoming else null
    }
}

/**
 * インポートを適用した結果、実際に何件書き換わるかを数える。
 * 実行前に「上書きされる件数」を確認ダイアログで見せるために使う。
 */
fun countChanges(
    existing: Map<String, ExportDay>,
    incoming: List<ExportDay>,
    mode: ImportMode,
): Int = incoming.count { resolveConflict(existing[it.localDate], it, mode) != null }
