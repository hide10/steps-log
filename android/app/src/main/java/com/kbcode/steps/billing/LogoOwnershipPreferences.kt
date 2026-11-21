package com.kbcode.steps.billing

import android.content.Context

class LogoOwnershipPreferences(context: Context) : LogoOwnershipStore {
    private val preferences = context.getSharedPreferences("logo_purchase", Context.MODE_PRIVATE)
    override var owned: Boolean
        get() = preferences.getBoolean("owned", false)
        set(value) { preferences.edit().putBoolean("owned", value).apply() }
}
