package com.kbcode.steps.billing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

class LogoBillingViewModel(application: Application) : AndroidViewModel(application) {
    val manager = BillingManager(PlayBillingGateway(application), LogoOwnershipPreferences(application), viewModelScope)
    override fun onCleared() {
        manager.close()
        super.onCleared()
    }
}
