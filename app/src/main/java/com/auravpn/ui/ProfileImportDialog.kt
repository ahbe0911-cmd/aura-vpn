package com.auravpn.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

private const val MAX_IMPORT_BYTES = 1024 * 1024

@Composable
fun ProfileImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("فایل قابل خواندن نیست")
                stream.use { input ->
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMPORT_BYTES) throw IllegalArgumentException("حجم فایل بیشتر از یک مگابایت است")
                        bytes.write(buffer, 0, count)
                    }
                    content = bytes.toString(Charsets.UTF_8.name())
                    error = null
                }
            } catch (e: Exception) {
                error = e.message ?: "خطا در خواندن فایل"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن کانفیگ یا اشتراک") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("لینک یا محتوای اشتراک را وارد کنید.")
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it; error = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    minLines = 4,
                    maxLines = 8,
                    placeholder = { Text("vless://...") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        val pasted = clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (pasted.isBlank()) error = "کلیپ‌بورد متنی ندارد"
                        else { content = pasted; error = null }
                    }) { Text("چسباندن") }
                    OutlinedButton(onClick = {
                        picker.launch(arrayOf("text/*", "application/json", "application/x-yaml", "application/octet-stream"))
                    }) { Text("انتخاب فایل") }
                }
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(content) }, enabled = content.isNotBlank()) { Text("وارد کردن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
