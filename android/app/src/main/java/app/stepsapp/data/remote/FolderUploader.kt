package app.stepsapp.data.remote

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface UploadResult {
    data class Success(val fileName: String) : UploadResult
    data class Failure(val message: String) : UploadResult
}

/**
 * ユーザーが選んだフォルダへ steps.json を書き出す。
 *
 * **Google ドライブのフォルダを選べる。** ドライブアプリが
 * DocumentsProvider を公開しており、実機で `FLAG_SUPPORTS_CREATE` と
 * `FLAG_SUPPORTS_IS_CHILD` が立っていることを確認済み。
 *
 * この方式なら **OAuth も Google Cloud プロジェクトの登録も要らない。**
 * ユーザーは最初に一度フォルダを選ぶだけで、以降は権限が永続化されるので
 * バックグラウンドからも書き込める。
 *
 * ドライブに限らず、端末内のフォルダや他のクラウドのフォルダも選べる。
 */
class FolderUploader(private val context: Context) {

    /**
     * @param treeUri フォルダ選択で得た URI（権限が永続化されている前提）
     * @param fileName 書き出すファイル名
     * @param content 中身
     */
    suspend fun upload(
        treeUri: Uri,
        fileName: String,
        content: String,
    ): UploadResult = withContext(Dispatchers.IO) {
        runCatching {
            val dir = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@runCatching UploadResult.Failure("フォルダを開けませんでした")
            if (!dir.canWrite()) {
                return@runCatching UploadResult.Failure(
                    "このフォルダには書き込めません。別のフォルダを選んでください",
                )
            }

            // 同じ名前のファイルがあれば中身だけ差し替える。
            // 消してから作り直すと、失敗したときにデータが無くなるため。
            val existing = dir.findFile(fileName)
            val target = existing ?: dir.createFile(MIME_JSON, fileName)
                ?: return@runCatching UploadResult.Failure("ファイルを作成できませんでした")

            // "wt" は truncate。付けないと前の内容が末尾に残る
            context.contentResolver.openOutputStream(target.uri, "wt")?.use {
                it.write(content.toByteArray())
            } ?: return@runCatching UploadResult.Failure("ファイルを開けませんでした")

            UploadResult.Success(target.name ?: fileName)
        }.getOrElse {
            Log.w(TAG, "書き出しに失敗", it)
            UploadResult.Failure(it.message ?: it.toString())
        }
    }

    /** 選んだフォルダの表示名。設定画面に出して、どこに保存するか分かるようにする。 */
    fun folderLabel(treeUri: Uri): String? = runCatching {
        DocumentFile.fromTreeUri(context, treeUri)?.name
    }.getOrNull()

    companion object {
        private const val TAG = "FolderUploader"
        private const val MIME_JSON = "application/json"

        /** PC 側の rclone がこの名前で回収する。 */
        const val FILE_NAME = "steps.json"

        /** 端末を変えても読めるよう、権限は永続化して持つ。 */
        const val PERSIST_FLAGS =
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
