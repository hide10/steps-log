package com.kbcode.steps.billing

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LogoOwnershipPreferencesTest {
    @Test fun persistsOnlyBooleanAndNewInstallStartsUnowned() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("logo_purchase", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertFalse(LogoOwnershipPreferences(context).owned)
        LogoOwnershipPreferences(context).owned = true
        assertTrue(LogoOwnershipPreferences(context).owned)
        assertEquals(mapOf("owned" to true), prefs.all)
        LogoOwnershipPreferences(context).owned = false
        assertFalse(LogoOwnershipPreferences(context).owned)
    }
}
