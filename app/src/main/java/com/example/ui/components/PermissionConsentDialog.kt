package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.PendingTransferConsent

@Composable
fun TransferConsentDialog(
    pending: PendingTransferConsent,
    providerLabel: String,
    onApprove: (allowBooks: Boolean, allowImage: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var allowBooks by remember(pending) { mutableStateOf(pending.citations.isNotEmpty()) }
    var allowImage by remember(pending) { mutableStateOf(pending.imageBase64 != null) }

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Privacy permission check",
                tint = MaterialTheme.colorScheme.secondary
            )
        },
        title = {
            Text("Owner Data Transfer Permission")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Owner, SUKOON never sends your personal files or photos online silently. Approve what to share with $providerLabel:",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (pending.citations.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = allowBooks,
                                    onCheckedChange = { allowBooks = it }
                                )
                                Text(
                                    text = "Send ${pending.citations.size} relevant book passage(s)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            pending.citations.forEach { cit ->
                                Text(
                                    text = "• ${cit.documentTitle} (Page ${cit.pageNumber}): \"${cit.snippet.take(100)}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (pending.imageBase64 != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Checkbox(
                                checked = allowImage,
                                onCheckedChange = { allowImage = it }
                            )
                            Text(
                                text = "Send attached image to compatible Vision AI model",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApprove(allowBooks, allowImage) }) {
                Text("Allow & Ask SUKOON")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pending.citations.isNotEmpty() || pending.imageBase64 != null) {
                    OutlinedButton(onClick = { onApprove(false, false) }) {
                        Text("Ask Without Files")
                    }
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    )
}
