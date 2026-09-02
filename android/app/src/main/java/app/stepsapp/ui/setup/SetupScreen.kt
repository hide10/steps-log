package app.stepsapp.ui.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import app.stepsapp.data.local.HealthConnectReader
import app.stepsapp.data.local.PrefsStore
import app.stepsapp.data.repository.StepsRepository
import app.stepsapp.domain.Goal
import app.stepsapp.ui.common.GoalRing
import kotlinx.coroutines.launch

/**
 * 初回セットアップ。
 *
 * 権限まわりは端末の設定に散らばっていて、自分でたどり着くのが難しい。
 * とくに電池の最適化は場所が機種ごとに違う。ここでまとめて案内する。
 *
 * **どの手順も飛ばせる。** 強制すると拒否したときに行き止まりになるため。
 */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PrefsStore.getInstance(context) }
    val repo = remember { StepsRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    var index by remember { mutableStateOf(0) }
    var goal by remember { mutableStateOf(prefs.goalSteps) }

    val step = SetupStep.ALL[index]

    fun next() {
        if (index < SetupStep.COUNT - 1) index++ else {
            prefs.setupDone = true
            onDone()
        }
    }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { next() }   // 許可でも拒否でも先へ進む

    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { next() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepDots(index)

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (step == SetupStep.GOAL) {
                    GoalRing(
                        steps = goal,
                        goalSteps = goal,
                        ratio = 1f,
                        achieved = false,
                        diameter = 150,
                    )
                }
                Text(
                    step.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    step.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (step == SetupStep.GOAL) {
                    // 目標は履歴に積む。あとで変えても、この日からの判定は変わらない
                    GoalPicker(goal) { goal = it; scope.launch { repo.setGoal(it) } }
                }
            }
        }

        Button(
            onClick = {
                when (step) {
                    SetupStep.WELCOME, SetupStep.GOAL -> next()
                    SetupStep.ACTIVITY -> requestActivity(context, activityLauncher::launch, ::next)
                    SetupStep.HEALTH_CONNECT ->
                        healthLauncher.launch(HealthConnectReader.PERMISSIONS)
                    SetupStep.BATTERY -> { openBatterySettings(context); next() }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(step.primary) }

        step.secondary?.let { label ->
            TextButton(onClick = { next() }) { Text(label) }
        }
    }
}

private fun requestActivity(
    context: Context,
    launch: (String) -> Unit,
    skip: () -> Unit,
) {
    val permission = Manifest.permission.ACTIVITY_RECOGNITION
    val already = ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED
    when {
        already -> skip()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> launch(permission)
        else -> skip()   // API 28 以下はこの権限が存在しない
    }
}

/**
 * 電池の最適化の除外を求める画面へ送る。
 *
 * 直接ダイアログを出す方法もあるが、機種によっては出せないので、
 * 一覧の画面を開いて自分で選んでもらう。確実に届く。
 */
private fun openBatterySettings(context: Context) {
    runCatching {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoring = power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        if (ignoring) return

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

@Composable
private fun StepDots(current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        repeat(SetupStep.COUNT) { i ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}

@Composable
private fun GoalPicker(selected: Long, onSelect: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(4_000L, 6_000L, 8_000L, 10_000L).forEach { preset ->
            FilterChip(
                selected = selected == preset,
                onClick = { onSelect(preset) },
                label = { Text("%,d".format(preset)) },
            )
        }
    }
}
