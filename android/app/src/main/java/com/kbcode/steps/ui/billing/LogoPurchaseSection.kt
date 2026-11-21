package com.kbcode.steps.ui.billing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kbcode.steps.R
import com.kbcode.steps.billing.BillingStatus
import com.kbcode.steps.billing.LogoBillingState

@Composable
fun LogoPurchaseSection(state: LogoBillingState, onBuy: () -> Unit, onRestore: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.billing_remove_logo), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.billing_free_features), style = MaterialTheme.typography.bodyMedium)
            if (state.owned) {
                Text(stringResource(R.string.billing_owned))
            } else {
                state.product?.let { product ->
                    // The store product name duplicates the section title above
                    // (both read "Remove logo"), so only the price button is shown.
                    Button(onClick = onBuy, enabled = state.canBuy) {
                        Text(stringResource(R.string.billing_buy, product.price))
                    }
                }
            }
            val message = when (state.displayStatus) {
                BillingStatus.IDLE, BillingStatus.CHECKING -> R.string.billing_checking
                BillingStatus.PURCHASING -> R.string.billing_purchasing
                BillingStatus.PENDING -> R.string.billing_pending
                BillingStatus.ACKNOWLEDGING -> R.string.billing_acknowledging
                BillingStatus.CANCELED -> R.string.billing_canceled
                BillingStatus.OFFLINE -> R.string.billing_offline
                BillingStatus.UNAVAILABLE -> R.string.billing_unavailable
                BillingStatus.ERROR -> R.string.billing_error
                BillingStatus.NOTHING_TO_RESTORE -> R.string.billing_nothing_to_restore
                BillingStatus.READY, null -> null
            }
            message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onRestore, enabled = !state.busy) {
                Text(stringResource(R.string.billing_restore))
            }
        }
    }
}
