package com.budgetsphere.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.budgetsphere.app.data.*
import java.io.File

@Composable fun Receipts(data: WorkspaceData,tx: TransactionEntity,store: ReceiptStore,vm: AppViewModel,hidden: Boolean) {
    val context=LocalContext.current;val scope=data.workspace.scope
    var preview by remember {mutableStateOf<AttachmentEntity?>(null)};var remove by remember {mutableStateOf<AttachmentEntity?>(null)}
    var cameraFile by remember {mutableStateOf<File?>(null)}
    val currentScope by rememberUpdatedState(scope)
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->if(uri!=null)vm.perform("Receipt added"){store.import(currentScope,tx.id,uri)}}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {success->val file=cameraFile;if(success && file!=null)vm.perform("Receipt added"){try{store.import(currentScope,tx.id,Uri.fromFile(file))}finally{file.delete();cameraFile=null}} else {file?.delete();cameraFile=null}}
    if(hidden) {Text("Receipts are hidden for privacy.");return}
    SectionCard("Receipts") {
        data.attachments.filter {it.transactionId==tx.id}.forEach {attachment->
            Row {
                AsyncImage(store.file(scope,attachment.thumbnail),"Receipt thumbnail",modifier=Modifier.size(72.dp).clickable {preview=attachment})
                TextButton(onClick={preview=attachment}){Text("View")};TextButton(onClick={remove=attachment}){Text("Remove")}
            }
        }
        Row {OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text("Gallery")};Spacer(Modifier.width(8.dp));OutlinedButton(onClick={
            try {val dir=File(context.cacheDir,"camera").apply {mkdirs()};val file=File.createTempFile("receipt-",".jpg",dir);cameraFile=file;camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.files",file))}catch(_:Exception){cameraFile?.delete();vm.notify("No camera is available. Choose an image from Gallery.")}
        }){Text("Camera")}}
        Text("Images stay in private app storage. Maximum import size: 20 MB.",style=MaterialTheme.typography.bodySmall)
    }
    if(!hidden) preview?.let {attachment->Dialog(onDismissRequest={preview=null}){Surface {Column(Modifier.padding(16.dp)){AsyncImage(store.file(scope,attachment.filename),"Receipt image",modifier=Modifier.fillMaxWidth().heightIn(max=500.dp));TextButton(onClick={preview=null}){Text("Close")}}}}}
    remove?.let {attachment->ConfirmDialog("Remove receipt?","This permanently removes the saved image.",{remove=null},{vm.perform("Receipt removed"){store.remove(scope,attachment.id)};remove=null})}
}
