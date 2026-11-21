package com.kbcode.steps.testing

import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * ui-test-manifest は debug バリアントにしか入らないため、release の Robolectric 環境には
 * ComponentActivity が登録されていない。製品マニフェストを変えずテスト時のみ登録する。
 * createAndroidComposeRule より外側（小さい order）で適用すること。
 */
class ComponentActivityRegistrationRule : ExternalResource() {
    override fun before() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager)
            .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}
