package app.stepsapp.share

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.annotation.DrawableRes
import app.stepsapp.R

/**
 * 写真を選ばないときの背景の柄。
 *
 * **画像は持ち込まずコードで描く。** アイコンの肉球と同じ方針で、
 * リポジトリに素材を抱えずに済み、端の解像度も気にしなくてよい。
 *
 * どれも**暗めに寄せてある**。文字は白しか使わないので、
 * 明るい柄を混ぜると歩数が読めなくなる。
 */
enum class ShareBackground(
    val label: String,
    @DrawableRes val photo: Int? = null,
    /** 街の写真か。自然の写真と行を分けて並べるのに使う */
    val city: Boolean = false,
) {
    /** いまのアクセント色から作る斜めのグラデーション。 */
    ACCENT("テーマ"),

    /** アクセント色の無地。いちばん邪魔をしない。 */
    PLAIN("無地"),

    /** 夜明け。濃紺から橙へ。 */
    DAWN("夜明け"),

    /** 夕暮れ。紫から桃へ。 */
    DUSK("夕暮れ"),

    /** 森。深緑。 */
    FOREST("森"),

    /** 海。藍から青緑へ。 */
    OCEAN("海"),

    /** 夜。ほぼ黒に小さな点を散らす。 */
    NIGHT("夜"),

    /** アクセント色の地に肉球を散らす。このアプリらしい一枚になる。 */
    PAWS("肉球"),

    /** アクセント色の地に輪を重ねる。達成リングのオマージュ。 */
    RINGS("リング"),

    /** 斜めの縞。 */
    STRIPES("しま"),

    // --- ここから下は柄ではなく、同梱した写真を敷く ---
    //
    // 写真は手元の画像生成（imggen）で作ったもの。拾い物を使わないので
    // 出どころがはっきりしていて、権利の心配も要らない。
    // **どれも暗めで中央が静かな絵を選んである。** 白い文字が中央に乗るため。

    PHOTO_DAWN("並木道", R.drawable.share_bg_dawn),
    PHOTO_FOREST("森", R.drawable.share_bg_forest),
    PHOTO_RIVER("川沿い", R.drawable.share_bg_river),
    PHOTO_SHORE("海辺", R.drawable.share_bg_shore),
    PHOTO_RIDGE("雲海", R.drawable.share_bg_ridge),
    PHOTO_AUTUMN("落ち葉", R.drawable.share_bg_autumn),

    // 街のほう。ジョグ・ラン・ウォーキングの絵柄。
    // 夜の街と雨あがり（石畳の路地）は建物に囲まれた場所なので、
    // 最初は自然に入れていたが街へ移した
    PHOTO_RUNNER("ランナー", R.drawable.share_bg_runner, city = true),
    PHOTO_BRIDGE("橋", R.drawable.share_bg_bridge, city = true),
    PHOTO_WATERFRONT("湾岸", R.drawable.share_bg_waterfront, city = true),
    PHOTO_TRACK("トラック", R.drawable.share_bg_track, city = true),
    PHOTO_STREET("夜の通り", R.drawable.share_bg_street, city = true),
    PHOTO_CROSSING("交差点", R.drawable.share_bg_crossing, city = true),
    PHOTO_RAIN("雨あがり", R.drawable.share_bg_rain, city = true),
    PHOTO_STAIRS("階段", R.drawable.share_bg_stairs, city = true),
    ;

    /** 柄ではなく写真か。写真は敷くだけで、この上に幕がかかる。 */
    val isPhoto: Boolean get() = photo != null

    companion object {
        val DEFAULT = ACCENT
    }
}

/** 背景を描く。写真を敷くときはこの関数を呼ばない。 */
internal fun drawBackgroundStyle(
    canvas: Canvas,
    size: Int,
    style: ShareBackground,
    accentRgb: Int,
) {
    when (style) {
        ShareBackground.ACCENT ->
            gradient(canvas, size, darken(accentRgb, 0.66f), darken(accentRgb, 0.30f))

        ShareBackground.PLAIN ->
            fill(canvas, size, darken(accentRgb, 0.52f))

        ShareBackground.DAWN ->
            gradient(canvas, size, Color.rgb(20, 28, 70), Color.rgb(196, 104, 54))

        ShareBackground.DUSK ->
            gradient(canvas, size, Color.rgb(58, 26, 82), Color.rgb(190, 88, 118))

        ShareBackground.FOREST ->
            gradient(canvas, size, Color.rgb(16, 48, 34), Color.rgb(42, 92, 58))

        ShareBackground.OCEAN ->
            gradient(canvas, size, Color.rgb(12, 36, 74), Color.rgb(28, 100, 108))

        ShareBackground.NIGHT -> {
            gradient(canvas, size, Color.rgb(10, 12, 22), Color.rgb(30, 34, 58))
            stars(canvas, size)
        }

        ShareBackground.PAWS -> {
            gradient(canvas, size, darken(accentRgb, 0.70f), darken(accentRgb, 0.42f))
            scatteredPaws(canvas, size, accentRgb)
        }

        ShareBackground.RINGS -> {
            gradient(canvas, size, darken(accentRgb, 0.68f), darken(accentRgb, 0.38f))
            rings(canvas, size, accentRgb)
        }

        ShareBackground.STRIPES -> {
            fill(canvas, size, darken(accentRgb, 0.58f))
            stripes(canvas, size, accentRgb)
        }

        // 写真は呼び出し側が敷くので、ここでは何も描かない
        else -> Unit
    }
}

private fun fill(canvas: Canvas, size: Int, color: Int) {
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply { this.color = color })
}

/** 左上から右下へ。真横や真下より奥行きが出る。 */
private fun gradient(canvas: Canvas, size: Int, from: Int, to: Int) {
    val paint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, size * 0.6f, size.toFloat(),
            from, to, Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
}

/**
 * 小さな点を散らす。
 *
 * **乱数は使わず、決まった位置に置く。** 同じ設定なら毎回同じ絵が出るほうが、
 * プレビューで見たものがそのまま共有される、という約束を守れる。
 */
private fun stars(canvas: Canvas, size: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255) }
    val points = listOf(
        0.12f to 0.10f, 0.28f to 0.06f, 0.46f to 0.14f, 0.63f to 0.05f, 0.81f to 0.12f,
        0.92f to 0.24f, 0.07f to 0.27f, 0.36f to 0.22f, 0.71f to 0.20f, 0.20f to 0.38f,
        0.55f to 0.33f, 0.88f to 0.40f, 0.14f to 0.52f, 0.94f to 0.60f, 0.05f to 0.70f,
    )
    points.forEachIndexed { i, (x, y) ->
        // 大きさを3種類で回して、粒がそろいすぎないようにする
        val r = size * (if (i % 3 == 0) 0.0045f else if (i % 3 == 1) 0.0030f else 0.0022f)
        canvas.drawCircle(size * x, size * y, r, paint)
    }
}

/** 肉球を散らす。文字が乗る中央は避けて、四隅寄りに置く。 */
private fun scatteredPaws(canvas: Canvas, size: Int, accentRgb: Int) {
    val spots = listOf(
        Triple(0.14f, 0.13f, 0.115f),
        Triple(0.84f, 0.09f, 0.085f),
        Triple(0.90f, 0.33f, 0.060f),
        Triple(0.09f, 0.36f, 0.070f),
        Triple(0.17f, 0.86f, 0.095f),
        Triple(0.86f, 0.80f, 0.110f),
        Triple(0.62f, 0.93f, 0.060f),
    )
    spots.forEach { (x, y, w) ->
        drawPawMark(canvas, size * x, size * y, size * w, Color.argb(38, 255, 255, 255))
    }
    // ひとつだけアクセント色を薄く乗せて、単調にならないようにする
    drawPawMark(
        canvas, size * 0.14f, size * 0.13f, size * 0.115f,
        lighten(accentRgb, 0.2f) and 0x33FFFFFF,
    )
}

/** 輪を重ねる。達成リングのオマージュ。中央の文字にかからないよう縁へ寄せる。 */
private fun rings(canvas: Canvas, size: Int, accentRgb: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(46, 255, 255, 255)
        strokeWidth = size * 0.016f
    }
    canvas.drawCircle(size * 0.16f, size * 0.14f, size * 0.30f, paint)
    canvas.drawCircle(size * 0.88f, size * 0.86f, size * 0.34f, paint)

    paint.color = lighten(accentRgb, 0.35f) and 0x40FFFFFF
    paint.strokeWidth = size * 0.022f
    canvas.drawCircle(size * 0.92f, size * 0.16f, size * 0.20f, paint)
    canvas.drawCircle(size * 0.10f, size * 0.90f, size * 0.24f, paint)
}

/** 斜めの縞。太さを変えて機械的に見えないようにする。 */
private fun stripes(canvas: Canvas, size: Int, accentRgb: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = lighten(accentRgb, 0.30f) and 0x2EFFFFFF
    }
    var i = 0
    var x = -size.toFloat()
    while (x < size * 2f) {
        paint.strokeWidth = size * (if (i % 3 == 0) 0.030f else 0.014f)
        canvas.drawLine(x, size.toFloat(), x + size, 0f, paint)
        x += size * 0.11f
        i++
    }
}
