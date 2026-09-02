package app.stepsapp.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import app.stepsapp.domain.ShareCard

/** 共有画像は正方形。SNS のタイムラインで切れにくい。 */
const val SHARE_IMAGE_SIZE = 1080

/** 四辺の余白。文字はこの内側にしか置かない。 */
private const val MARGIN = 0.075f

/**
 * 共有カードを 1 枚の画像に描く。
 *
 * **プレビューと共有される画像は同じこの関数で作る。** 別々に描くと
 * 「プレビューに無かったものが写っていた」という事故が起きうる。
 * 何を描くかは [ShareCard] だけが決め、ここでは元データを参照しない。
 *
 * 達成リングは載せない。当初は画面と同じリングを中央に置いていたが、
 * 円の内側は歩数しか入らず、日付や距離を足すとはみ出した。
 *
 * **中身は縦に積んで中央に置く。** 以前は歩数を右上・ロゴを左下に置いていたので、
 * 何も足していないと対角だけが埋まった すかすかの絵になっていた。
 */
fun renderShareImage(
    card: ShareCard,
    ratio: Float,
    accentRgb: Int,
    background: Bitmap? = null,
    style: ShareBackground = ShareBackground.DEFAULT,
    size: Int = SHARE_IMAGE_SIZE,
): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    if (background == null) drawBackgroundStyle(canvas, size, style, accentRgb)
    if (background != null) drawPhoto(canvas, background, size)
    if (background != null) drawScrim(canvas, size)

    drawContent(canvas, size, card, accentRgb)
    drawLogo(canvas, size, accentRgb)

    return bmp
}

private fun drawPhoto(canvas: Canvas, src: Bitmap, size: Int) {
    // 縦横比を保ったまま中央を切り出して正方形に敷く
    val side = minOf(src.width, src.height)
    val left = (src.width - side) / 2
    val top = (src.height - side) / 2
    canvas.drawBitmap(
        src,
        Rect(left, top, left + side, top + side),
        Rect(0, 0, size, size),
        Paint(Paint.FILTER_BITMAP_FLAG),
    )
}

/**
 * 写真を敷いたときだけ、文字が乗るところに薄い幕をかける。
 *
 * 全面を一様に暗くすると、選んだ写真が台無しになる。
 * 中身を中央に積むようにしたので、幕も**中央から下がいちばん濃くなる**ようにした。
 * 上端は薄いままにして、写真の主役が写っていることが多い上半分を残す。
 */
private fun drawScrim(canvas: Canvas, size: Int) {
    val paint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, size.toFloat(),
            intArrayOf(
                Color.argb(40, 0, 0, 0),
                Color.argb(145, 0, 0, 0),
                Color.argb(145, 0, 0, 0),
                Color.argb(115, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.30f, 0.78f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
}

/**
 * 中身を縦に積んで中央に置く。
 *
 * **積む高さを先に合計してから描く。** そうしないと、項目を足すたびに
 * 絵の重心が下へずれていく。何も足していないときでも、歩数だけで
 * 1枚の絵として成り立つ配置にしてある。
 */
private fun drawContent(canvas: Canvas, size: Int, card: ShareCard, accentRgb: Int) {
    val stepsSize = size * 0.19f
    val unitSize = size * 0.048f
    val lineSize = size * 0.038f
    val distanceSize = size * 0.060f
    val trendHeight = size * 0.15f
    val gap = size * 0.026f

    // 目標と連続日数は短いので1行にまとめる。行数が増えるほど間延びするため
    val lines = buildList {
        card.date?.let { add(it) }
        listOfNotNull(card.goal, card.streak)
            .takeIf { it.isNotEmpty() }
            ?.let { add(it.joinToString("　")) }
    }
    val distanceText = card.distanceValue
        ?.let { "$it ${card.distanceUnit.orEmpty()}".trim() }

    var total = stepsSize + gap * 0.3f + unitSize
    lines.forEach { total += gap * 0.6f + lineSize }
    if (distanceText != null) total += gap * 0.7f + distanceSize
    if (card.trend.isNotEmpty()) total += gap + trendHeight

    // 下端はロゴに譲り、その手前までを縦の中央とみなす
    val bottomLimit = size - size * MARGIN * 2f - size * 0.055f
    var y = (bottomLimit - total) / 2f + stepsSize
    val cx = size / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = stepsSize
    }
    canvas.drawText("%,d".format(card.steps), cx, y, paint)

    y += gap * 0.3f + unitSize
    paint.typeface = Typeface.DEFAULT
    paint.textSize = unitSize
    paint.color = Color.argb(215, 255, 255, 255)
    canvas.drawText("歩", cx, y, paint)

    paint.textSize = lineSize
    lines.forEach { line ->
        y += gap * 0.6f + lineSize
        canvas.drawText(line, cx, y, paint)
    }

    if (distanceText != null) {
        y += gap * 0.7f + distanceSize
        paint.textSize = distanceSize
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(distanceText, cx, y, paint)
    }

    if (card.trend.isNotEmpty()) {
        drawTrend(canvas, size, card.trend, accentRgb, top = y + gap, height = trendHeight)
    }
}

/**
 * 直近の推移を折れ線で描く。
 *
 * 目盛りも数値も出さない。**縦軸は最小値〜最大値で正規化しているだけ**なので、
 * 形は分かっても実数は読み取れない。載せる情報を増やしすぎないための割り切り。
 */
private fun drawTrend(
    canvas: Canvas,
    size: Int,
    values: List<Long>,
    accentRgb: Int,
    top: Float,
    height: Float,
) {
    val m = size * MARGIN
    val left = m
    val right = size - m
    val bottom = top + height

    val max = values.max()
    val min = values.min()
    val span = (max - min).toFloat()
    val stepX = (right - left) / (values.size - 1)

    val points = values.mapIndexed { i, v ->
        // 全部同じ値なら真ん中に水平線を引く（0除算も避ける）
        val t = if (span <= 0f) 0.5f else (v - min) / span
        left + stepX * i to bottom - (bottom - top) * t
    }

    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = size * 0.013f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = lighten(accentRgb, 0.30f)
    }

    // 折れ線のままだと角が立つので、中点を通る二次ベジェで丸める
    val path = Path().apply {
        moveTo(points[0].first, points[0].second)
        for (i in 1 until points.size) {
            val (px, py) = points[i - 1]
            val (cx, cy) = points[i]
            quadTo(px, py, (px + cx) / 2f, (py + cy) / 2f)
        }
        lineTo(points.last().first, points.last().second)
    }
    canvas.drawPath(path, line)

    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    points.dropLast(1).forEach { (x, y) -> canvas.drawCircle(x, y, size * 0.011f, dot) }

    // 最後の点＝今日。ここだけ輪をつけて位置を分かりやすくする
    val (lx, ly) = points.last()
    canvas.drawCircle(lx, ly, size * 0.023f, dot)
    canvas.drawCircle(
        lx, ly, size * 0.023f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.010f
            color = lighten(accentRgb, 0.30f)
        },
    )
}

/**
 * 下端の中央にアプリの印。
 *
 * 肉球と名前をひとかたまりとして扱い、その全体を中央に置く。
 * 距離は中身のほう（[drawContent]）に移した。四隅に散らすのをやめたため。
 */
private fun drawLogo(canvas: Canvas, size: Int, accentRgb: Int) {
    val baseline = size - size * MARGIN
    val pawWidth = size * 0.055f
    val gap = size * 0.018f

    val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        textAlign = Paint.Align.LEFT
        textSize = size * 0.036f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val text = "歩数ログ"
    val left = (size - (pawWidth + gap + name.measureText(text))) / 2f

    // 肉球の中心を、並びの文字の視覚的な中心（ベースラインより少し上）に合わせる
    drawPawMark(
        canvas,
        cx = left + pawWidth / 2f,
        cy = baseline - size * 0.013f,
        w = pawWidth,
        color = lighten(accentRgb, 0.35f),
    )
    canvas.drawText(text, left + pawWidth + gap, baseline, name)
}

/**
 * アプリアイコンと同じ肉球。ロゴ画像を持ち込まずに済むよう円だけで描く。
 *
 * 座標は `ic_launcher_foreground.xml` の肉球を、**幅を 1.0 として正規化した比率**。
 * アイコン側は指4つが中心線に対して左右対称に並んでいる。
 *
 * 以前はこの対応が取れておらず、指の並びが掌より右へ約2割ずれていた。
 * 縦位置も並びの文字より上に浮いていた。**アイコンから比率を起こすこと。**
 *
 * @param cx 肉球の中心（左右）
 * @param cy 肉球の中心（上下）
 * @param w  肉球の幅
 */
internal fun drawPawMark(canvas: Canvas, cx: Float, cy: Float, w: Float, color: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    // 掌球。アイコンでは下側が3つの山になっているが、この大きさでは円で十分
    canvas.drawCircle(cx, cy + w * 0.217f, w * 0.361f, paint)

    // 指球。外側の2つは少し小さく、下がった位置に付く
    val toes = listOf(
        Triple(-0.385f, -0.148f, 0.124f),
        Triple(-0.145f, -0.273f, 0.136f),
        Triple(0.145f, -0.273f, 0.136f),
        Triple(0.385f, -0.148f, 0.124f),
    )
    toes.forEach { (dx, dy, r) ->
        canvas.drawCircle(cx + w * dx, cy + w * dy, w * r, paint)
    }
}

internal fun darken(rgb: Int, amount: Float): Int = Color.rgb(
    (Color.red(rgb) * (1 - amount)).toInt(),
    (Color.green(rgb) * (1 - amount)).toInt(),
    (Color.blue(rgb) * (1 - amount)).toInt(),
)

internal fun lighten(rgb: Int, amount: Float): Int = Color.rgb(
    (Color.red(rgb) + (255 - Color.red(rgb)) * amount).toInt(),
    (Color.green(rgb) + (255 - Color.green(rgb)) * amount).toInt(),
    (Color.blue(rgb) + (255 - Color.blue(rgb)) * amount).toInt(),
)
