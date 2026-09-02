package app.stepsapp.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.stepsapp.data.local.PrefsStore
import app.stepsapp.data.remote.FolderUploader
import app.stepsapp.data.remote.UploadResult

/**
 * steps.json を、ユーザーが選んだフォルダ（Google ドライブを含む）へ書き出す。
 *
 * OAuth も Google Cloud プロジェクトの登録も要らない。
 * 最初に一度フォルダを選べば、権限が永続化されるので以降は自動で書ける。
 */
class UploadRepository private constructor(private val context: Context) {

    private val prefs = PrefsStore.getInstance(context)
    private val uploader = FolderUploader(context)
    private val backup = BackupRepository.getInstance(context)

    val configured: Boolean get() = prefs.folderUri.isNotEmpty()

    /** 最後に書き出せた時刻。まだ一度も成功していなければ null。 */
    fun lastUploadAt(): Long? = prefs.lastUploadAt.takeIf { it > 0 }

    fun folderLabel(): String? =
        prefs.folderUri.takeIf { it.isNotEmpty() }
            ?.let { uploader.folderLabel(Uri.parse(it)) }

    /** フォルダ選択の結果を受け取り、権限を永続化して覚える。 */
    fun rememberFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                FolderUploader.PERSIST_FLAGS,
            )
        }.onFailure { Log.w(TAG, "権限を永続化できなかった", it) }
        prefs.folderUri = uri.toString()
    }

    fun forgetFolder() {
        prefs.folderUri.takeIf { it.isNotEmpty() }?.let { stored ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(stored),
                    FolderUploader.PERSIST_FLAGS,
                )
            }
        }
        prefs.folderUri = ""
    }

    suspend fun upload(): UploadResult {
        val stored = prefs.folderUri
        if (stored.isEmpty()) {
            return UploadResult.Failure("保存先のフォルダが選ばれていません")
        }
        val content = backup.exportJson()
        val result = uploader.upload(Uri.parse(stored), FolderUploader.FILE_NAME, content)
        when (result) {
            is UploadResult.Success -> prefs.lastUploadAt = System.currentTimeMillis()
            is UploadResult.Failure -> Log.w(TAG, "アップロードに失敗: ${result.message}")
        }
        return result
    }

    companion object {
        private const val TAG = "UploadRepository"

        @Volatile
        private var instance: UploadRepository? = null

        fun getInstance(context: Context): UploadRepository =
            instance ?: synchronized(this) {
                instance ?: UploadRepository(context.applicationContext).also { instance = it }
            }
    }
}
