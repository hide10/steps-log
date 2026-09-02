package app.stepsapp.ui.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stepsapp.domain.SharePreset
import app.stepsapp.domain.ShareOptions
import app.stepsapp.domain.achievedRatio
import app.stepsapp.domain.buildShareCard
import app.stepsapp.share.ShareBackground
import app.stepsapp.share.renderShareImage
import app.stepsapp.share.shareImageIntent
import app.stepsapp.share.writeShareImage
import app.stepsapp.ui.home.HomeViewModel
import app.stepsapp.ui.theme.LocalAccentColors

/**
 * SNS 共有用の画像を作る画面。
 *
 * **プレビューに出しているのは共有される画像そのもの**（同じ [renderShareImage] の出力）。
 * 歩数は行動の記録でもあるので、何が写るかを見てから出せることを最優先にした。
 */
@Composable
fun ShareScreen(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = LocalAccentColors.current

    var options by remember { mutableStateOf(ShareOptions.DEFAULT) }
    var background by remember { mutableStateOf<Bitmap?>(null) }
    var style by remember { mutableStateOf(ShareBackground.DEFAULT) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        // 選ばれなかった場合は今の背景を保つ（キャンセルで消えると操作しづらい）
        if (uri != null) background = decodeBackground(context, uri)
    }

    val card = buildShareCard(
        steps = state.todaySteps,
        date = state.today,
        goalSteps = state.goal.dailySteps,
        streakDays = state.streak,
        distance = state.distance,
        distanceUnit = state.distanceUnit,
        // 選んでいる期間のまま「直近の推移」として使う
        trend = state.buckets.takeLast(7).map { it.total },
        options = options,
    )

    // 同梱の写真を選んでいるときは、それを背景として敷く。
    // 端末から選んだ写真があればそちらが優先
    val presetPhoto = remember(style) {
        style.photo?.let { BitmapFactory.decodeResource(context.resources, it) }
    }
    val backdrop = background ?: presetPhoto

    val image = remember(card, backdrop, style, accent.primary) {
        renderShareImage(
            card = card,
            ratio = achievedRatio(state.todaySteps, state.goal),
            accentRgb = accent.primary.toArgb(),
            background = backdrop,
            style = style,
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "共有する画像のプレビュー",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
            )

            Text(
                "この画像がそのまま共有されます",
                style = MaterialTheme.typography.bodySmall,
            )

            // 写真を敷いているあいだは柄が見えないので、出さない
            if (background == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("背景の写真（自然）", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ShareBackground.entries
                                .filter { it.isPhoto && !it.city }
                                .forEach { candidate ->
                                    FilterChip(
                                        selected = style == candidate,
                                        onClick = { style = candidate },
                                        label = { Text(candidate.label) },
                                    )
                                }
                        }

                        Text("背景の写真（街）", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ShareBackground.entries
                                .filter { it.isPhoto && it.city }
                                .forEach { candidate ->
                                    FilterChip(
                                        selected = style == candidate,
                                        onClick = { style = candidate },
                                        label = { Text(candidate.label) },
                                    )
                                }
                        }

                        Text("背景の柄", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ShareBackground.entries.filterNot { it.isPhoto }.forEach { candidate ->
                                FilterChip(
                                    selected = style == candidate,
                                    onClick = { style = candidate },
                                    label = { Text(candidate.label) },
                                )
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("雛形", style = MaterialTheme.typography.titleSmall)
                    // 毎回チェックを付け直さずに済むように。個別の足し引きは下でできる
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SharePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = SharePreset.of(options) == preset,
                                onClick = { options = preset.options },
                                label = { Text(preset.label) },
                            )
                        }
                    }

                    Text("載せるもの", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "連続日数・距離・グラフは、日付と組み合わせると生活のパターンが読めます。" +
                            "必要なときだけ足してください。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle("日付", options.includeDate) {
                            options = options.copy(includeDate = it)
                        }
                        Toggle("目標", options.includeGoal) {
                            options = options.copy(includeGoal = it)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle("連続日数", options.includeStreak) {
                            options = options.copy(includeStreak = it)
                        }
                        Toggle("距離", options.includeDistance) {
                            options = options.copy(includeDistance = it)
                        }
                        Toggle("グラフ", options.includeTrend) {
                            options = options.copy(includeTrend = it)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (background == null) "背景に写真を使う" else "写真を選び直す")
                }
                if (background != null) {
                    OutlinedButton(onClick = { background = null }) { Text("写真を外す") }
                }
            }

            Button(
                onClick = {
                    val uri = writeShareImage(context, image)
                    context.startActivity(
                        android.content.Intent.createChooser(shareImageIntent(uri), "共有"),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("共有する")
            }
        }
    }
}

@Composable
private fun Toggle(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label) },
    )
}

/**
 * 背景写真を読み込む。
 *
 * 共有画像は 1080px なので、それ以上の解像度は要らない。
 * 大きな写真をそのまま読むと OutOfMemory になりうるので間引いて読む。
 */
private fun decodeBackground(context: android.content.Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > app.stepsapp.share.SHARE_IMAGE_SIZE * 2) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}
