package com.budgetsphere.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.budgetsphere.app.data.*
import com.budgetsphere.app.domain.*
import java.io.File

@Composable
fun Receipts(
    data: WorkspaceData,
    tx: FinancialTransaction,
    store: ReceiptStore,
    vm: AppViewModel,
    hidden: Boolean,
) {
    val context = LocalContext.current
    val scope = data.workspace.scope
    var preview by remember { mutableStateOf<Attachment?>(null) }
    var remove by remember { mutableStateOf<Attachment?>(null) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraFile = cameraPath?.let { path ->
        File(path).takeIf {
            it.parentFile?.canonicalFile == File(context.cacheDir, "camera").canonicalFile
        }
    }
    val currentScope by rememberUpdatedState(scope)
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) vm.perform("Receipt added") { store.import(currentScope, tx.id, uri) }
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = cameraFile
            if (success && file != null)
                vm.perform("Receipt added") {
                    try {
                        store.import(currentScope, tx.id, Uri.fromFile(file))
                    } finally {
                        file.delete()
                        cameraPath = null
                    }
                }
            else {
                file?.delete()
                cameraPath = null
            }
        }
    if (hidden) {
        Text("Receipts are hidden for privacy.")
        return
    }
    SectionCard("Receipts") {
        Text("Private attachments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        data.attachments
            .filter { it.transactionId == tx.id }
            .forEach { attachment ->
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    AsyncImage(
                        store.file(scope, attachment.thumbnail),
                        "Receipt thumbnail",
                        modifier = Modifier.size(72.dp).clickable { preview = attachment },
                        onError = {
                            vm.notify(
                                "Receipt image is missing or unreadable. Remove it and add it again."
                            )
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { Text("Receipt image", style = MaterialTheme.typography.bodyMedium); Text("Stored privately", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { preview = attachment }) { Text("View") }
                    IconButton(onClick = { remove = attachment }) { Icon(Icons.Outlined.DeleteOutline, "Remove receipt", tint = MaterialTheme.colorScheme.error) }
                }
                }
            }
        Row {
            FilledTonalButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) { Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Gallery") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    try {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File.createTempFile("receipt-", ".jpg", dir)
                        cameraPath = file.path
                        camera.launch(
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.files",
                                file,
                            )
                        )
                    } catch (_: Exception) {
                        cameraFile?.delete()
                        vm.notify("No camera is available. Choose an image from Gallery.")
                    }
                }
            ) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Camera") }
        }
        Text(
            "Images stay in private app storage. Maximum import size: 20 MB.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (!hidden)
        preview?.let { attachment ->
            Dialog(onDismissRequest = { preview = null }) {
                Surface {
                    Column(Modifier.padding(16.dp)) {
                        AsyncImage(
                            store.file(scope, attachment.filename),
                            "Receipt image",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                            onError = {
                                vm.notify(
                                    "Receipt image is missing or unreadable. Remove it and add it again."
                                )
                            },
                        )
                        TextButton(onClick = { preview = null }) { Text("Close") }
                    }
                }
            }
        }
    remove?.let { attachment ->
        ConfirmDialog(
            "Remove receipt?",
            "This permanently removes the saved image.",
            { remove = null },
            {
                vm.perform("Receipt removed") { store.remove(scope, attachment.id) }
                remove = null
            },
        )
    }
}
