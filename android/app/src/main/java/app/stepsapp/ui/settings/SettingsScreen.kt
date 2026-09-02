package app.stepsapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.stepsapp.BuildConfig
import app.stepsapp.domain.Accent
import app.stepsapp.domain.DistanceUnit
import app.stepsapp.domain.Goal
import app.stepsapp.domain.HeightUnit
import app.stepsapp.domain.WeightUnit
import app.stepsapp.domain.ThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenBackup: () -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // フォルダを選ぶ。ドライブのフォルダもここから選べる
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> vm.onFolderPicked(uri) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("見た目", style = MaterialTheme.typography.titleMedium)
            Text("達成リングとグラフの色", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Accent.entries.forEach { accent ->
                    val selected = state.accent == accent
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(accent.rgb))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable { vm.setAccent(accent) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            HorizontalDivider()

            Text("目標", style = MaterialTheme.typography.titleMedium)
            Text(
                "1日の目標歩数。ホームの達成リングと連続日数の判定に使います。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "現在: %,d 歩".format(state.goalSteps),
                style = MaterialTheme.typography.bodyLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Goal.PRESETS.forEach { preset ->
                    FilterChip(
                        selected = state.goalSteps == preset,
                        onClick = { vm.setGoal(preset) },
                        label = { Text("%,d".format(preset)) },
                    )
                }
            }

            HorizontalDivider()

            Text("単位", style = MaterialTheme.typography.titleMedium)
            UnitRow("距離", DistanceUnit.entries, state.distanceUnit, { it.label }) {
                vm.setDistanceUnit(it)
            }
            UnitRow("身長", HeightUnit.entries, state.heightUnit, { it.label }) {
                vm.setHeightUnit(it)
            }
            UnitRow("体重", WeightUnit.entries, state.weightUnit, { it.label }) {
                vm.setWeightUnit(it)
            }

            HorizontalDivider()

            Text("体格（任意）", style = MaterialTheme.typography.titleMedium)
            Text(
                "距離と消費カロリーの推定に使います。あくまで概算です。" +
                    "身長が未設定なら平均的な歩幅を使い、体重が未設定ならカロリーは表示しません。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.heightCm,
                onValueChange = { vm.setHeight(it) },
                label = { Text("身長 (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.strideCm,
                onValueChange = { vm.setStride(it) },
                label = { Text("歩幅 (cm)") },
                supportingText = {
                    Text("空欄なら身長から推定します（身長 × 0.45）")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.weightKg,
                onValueChange = { vm.setWeight(it) },
                label = { Text("体重 (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Text("書き出し / 読み込み", style = MaterialTheme.typography.titleMedium)
            Text(
                "手元にファイルとして残したいときや、機種変更で移すときに使います。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onOpenBackup) { Text("書き出し / 読み込み画面へ") }

            HorizontalDivider()

            Text("バックアップ先", style = MaterialTheme.typography.titleMedium)
            Text(
                "1日1回 steps.json を書き出します。" +
                    "フォルダ選択で Google ドライブを選べば、そのままドライブに保存されます。" +
                    "端末内のフォルダでもかまいません。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = state.folderLabel?.let { "保存先: $it" } ?: "保存先が未設定です",
                style = MaterialTheme.typography.bodyLarge,
            )
            // 日次ジョブが回っているかを目で確かめられるようにする
            Text(
                text = state.lastUploadAt?.let {
                    "最後の書き出し: " + java.text.SimpleDateFormat(
                        "M月d日 HH:mm", java.util.Locale.JAPAN,
                    ).format(java.util.Date(it))
                } ?: "まだ書き出していません",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { folderPicker.launch(null) }, enabled = !state.busy) {
                Text(if (state.folderLabel == null) "保存先を選ぶ" else "保存先を変える")
            }
            if (state.folderLabel != null) {
                Button(onClick = { vm.uploadNow() }, enabled = !state.busy) {
                    Text(if (state.busy) "書き出し中…" else "いますぐ書き出す")
                }
                OutlinedButton(onClick = { vm.forgetFolder() }, enabled = !state.busy) {
                    Text("保存先を解除する")
                }
            }

            // どの版が入っているか、端末を見るだけで分かるようにする。
            // 配布した APK と手元のビルドが混ざったときに要る
            Text(
                "バージョン ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.dismissMessage() },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.dismissMessage() }) { Text("OK") }
            },
        )
    }
}

/** 単位の選び方。項目が2つなので、チップを横に並べるだけで足りる。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> UnitRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(56.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}
