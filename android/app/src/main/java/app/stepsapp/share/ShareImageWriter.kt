package app.stepsapp.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 共有画像の受け渡し。
 *
 * **キャッシュに1枚だけ置いて毎回上書きする。** 共有のたびにファイルが増えると、
 * 過去の歩数が写った画像が端末に溜まり続ける。名前を固定して使い回す。
 */
private const val SHARE_DIR = "share"
private const val SHARE_FILE = "steps-share.png"

fun writeShareImage(context: Context, bitmap: Bitmap): Uri {
    val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
    val file = File(dir, SHARE_FILE)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

fun shareImageIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "image/png"
    putExtra(Intent.EXTRA_STREAM, uri)
    // 受け取ったアプリがキャッシュ内の画像を読めるようにする
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
