package com.clubhouselinks.android

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class DraftResident(
    val first: String, val last: String, val phone: String, val address: String,
    val selected: Boolean = true
)

@Composable
fun BoardResidentDirectoryImport(
    boardId: Int,
    token: String,
    community: String,
    inviteCode: String,
    onClose: () -> Unit,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var drafts by remember { mutableStateOf<List<DraftResident>>(emptyList()) }
    var filename by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                message = ""
                report = ""
                try {
                    val result = withContext(Dispatchers.IO) {
                        val resolver = context.contentResolver
                        val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                        } ?: "directory.pdf"
                        val bytes = resolver.openInputStream(uri)?.use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > 6 * 1024 * 1024)
                                    throw IllegalArgumentException("File exceeds the 6 MB limit")
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: throw IllegalArgumentException("Unable to read selected document")
                        Pair(name, ClubhouseApi.boardRosterExtract(boardId, token, name, bytes))
                    }
                    filename = result.first
                    val json = result.second.optJSONArray("residents") ?: JSONArray()
                    drafts = (0 until json.length()).mapNotNull { index ->
                        json.optJSONObject(index)?.let { row ->
                            DraftResident(
                                row.optString("first_name"), row.optString("last_name"),
                                row.optString("phone"), row.optString("address")
                            )
                        }
                    }
                    message = if (drafts.isEmpty()) "No residents found. Try another document."
                        else "Review every record. Missing or incorrect details must be fixed before import."
                } catch (ex: Exception) {
                    message = ex.message ?: "Unable to read resident directory"
                } finally {
                    busy = false
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Import Resident Directory", style = MaterialTheme.typography.headlineSmall)
        Text(community, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = inviteCode, onValueChange = {}, enabled = false,
            label = { Text("Community Invite Code") }, modifier = Modifier.fillMaxWidth()
        )
        Text("PDF, CSV, XLSX, PNG or JPG · Maximum 6 MB. Documents are analyzed, not permanently stored.",
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            picker.launch(arrayOf(
                "application/pdf", "text/csv",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "image/png", "image/jpeg", "*/*"
            ))
        }, enabled = !busy && report.isEmpty()) {
            Text(if (filename.isEmpty()) "Choose Resident Directory" else "Choose Another File")
        }
        if (filename.isNotEmpty()) Text("Selected: $filename", style = MaterialTheme.typography.bodySmall)
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (report.isNotEmpty()) {
            Text(report)
            Button(onClick = onImported) { Text("Back to Residents") }
        } else if (drafts.isNotEmpty()) {
            val eligible = drafts.count {
                it.selected && it.first.isNotBlank() && it.last.isNotBlank() &&
                    it.phone.filter(Char::isDigit).length in 10..11 && it.address.isNotBlank()
            }
            Text("${drafts.size} extracted · $eligible selected and complete")
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(drafts) { index, item ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row {
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { selected ->
                                        drafts = drafts.toMutableList().apply {
                                            set(index, item.copy(selected = selected))
                                        }
                                    }
                                )
                                Text("Resident ${index + 1}")
                            }
                            OutlinedTextField(
                                item.first, { v ->
                                    drafts = drafts.toMutableList().apply { set(index,item.copy(first=v)) }
                                }, label = { Text("First Name") }, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                item.last, { v ->
                                    drafts = drafts.toMutableList().apply { set(index,item.copy(last=v)) }
                                }, label = { Text("Last Name") }, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                item.phone, { v ->
                                    drafts = drafts.toMutableList().apply { set(index,item.copy(phone=v)) }
                                }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                item.address, { v ->
                                    drafts = drafts.toMutableList().apply { set(index,item.copy(address=v)) }
                                }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            Button(
                enabled = !busy && eligible > 0,
                onClick = {
                    scope.launch {
                        busy = true
                        message = ""
                        try {
                            val data = JSONArray()
                            drafts.filter {
                                it.selected && it.first.isNotBlank() && it.last.isNotBlank() &&
                                    it.phone.filter(Char::isDigit).length in 10..11 && it.address.isNotBlank()
                            }.forEach { row ->
                                data.put(JSONObject()
                                    .put("first_name",row.first.trim())
                                    .put("last_name",row.last.trim())
                                    .put("phone",row.phone.trim())
                                    .put("address",row.address.trim()))
                            }
                            val response = ClubhouseApi.boardRosterImport(boardId,token,data)
                            report = "${response.optInt("created")} residents approved; ${response.optInt("skipped")} skipped (duplicates or invalid records)."
                            val results = response.optJSONArray("results") ?: JSONArray()
                            val issues = (0 until results.length()).mapNotNull { i ->
                                results.optJSONObject(i)?.takeIf { it.optString("status") == "skipped" }
                                    ?.let { "Row ${it.optInt("row")}: ${it.optString("message")}" }
                            }
                            if (issues.isNotEmpty()) report += "\n" + issues.joinToString("\n")
                        } catch (ex: Exception) {
                            message = ex.message ?: "Import failed"
                        } finally { busy = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Import $eligible Reviewed Residents") }
        } else {
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = onClose, enabled = !busy) { Text("Close") }
    }
}
