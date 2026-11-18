package com.clubhouselinks.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val rosterCyan = Color(0xFF1FC8E6)
private val rosterBg = Brush.verticalGradient(listOf(Color(0xFF00343D),Color(0xFF11122B),Color(0xFF3A1455)))

/**
 * A verified board member can maintain only their own HOA's residents.
 * Session JWT is kept in this composable's volatile memory and discarded on
 * navigation/dismissal; server-side authorization remains authoritative.
 */
@Composable
fun BoardResidentRoster(boardId: Int, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var token by remember(boardId) { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var residents by remember(boardId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }

    fun load() {
        if (token.isBlank() || busy) return
        scope.launch {
            busy = true; error = ""
            try {
                val response = ClubhouseApi.boardRosterList(boardId, token)
                val array = response.optJSONArray("residents") ?: JSONArray()
                residents = (0 until array.length()).mapNotNull(array::optJSONObject)
            } catch (ex: Exception) {
                if (ex is ApiException && (ex.status == 401 || ex.status == 403)) {
                    token = ""; sent = false; residents = emptyList()
                }
                error = ex.message ?: "Unable to load HOA residents"
            } finally {busy = false}
        }
    }
    Column(Modifier.fillMaxSize().background(rosterBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HOA Residents",style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,modifier = Modifier.weight(1f))
            TextButton(onClick = {token = ""; onClose()}) { Text("Close",color=rosterCyan) }
        }
        if (token.isBlank()) {
            Text("Verify your board member phone to manage residents in your community. Access expires after 30 minutes.",
                color = Color.White.copy(alpha=.8f))
            if (sent) {
                OutlinedTextField(value=code,onValueChange={code=it.filter(Char::isDigit).take(10)},
                    label={Text("SMS verification code")},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                Button(onClick={
                    scope.launch {
                        busy=true; error=""
                        try {
                            val response=ClubhouseApi.boardRosterVerify(boardId, code)
                            token=response.optString("access_token")
                            if(token.isBlank()) error="Verification did not return a session"
                            else {
                                val list=ClubhouseApi.boardRosterList(boardId,token)
                                val arr=list.optJSONArray("residents") ?: JSONArray()
                                residents=(0 until arr.length()).mapNotNull(arr::optJSONObject)
                            }
                        } catch(ex:Exception){error=ex.message ?: "Verification failed"}
                        finally {busy=false}
                    }
                },enabled=!busy && code.length>=4,modifier=Modifier.fillMaxWidth()) {
                    Text("Verify & Open Resident Directory")
                }
            }
            OutlinedButton(onClick={
                scope.launch {
                    busy=true;error=""
                    try{ClubhouseApi.boardRosterStart(boardId);sent=true}
                    catch(ex:Exception){error=ex.message ?: "Unable to send code"}
                    finally{busy=false}
                }
            },enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                Text(if(sent)"Resend verification code" else "Send Board Verification Code")
            }
        } else {
            OutlinedTextField(value=search,onValueChange={search=it},
                label={Text("Search residents by name or ID")},
                modifier=Modifier.fillMaxWidth(),singleLine=true)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("${residents.size} community residents", modifier=Modifier.weight(1f),
                    color=rosterCyan)
                TextButton(onClick={load()},enabled=!busy) {Text("Refresh")}
            }
            val filtered=residents.filter { resident ->
                val full="${resident.optString("first_name")} ${resident.optString("last_name")}"
                search.isBlank() || full.contains(search,ignoreCase=true) ||
                    resident.optString("id").contains(search)
            }
            LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),
                contentPadding=PaddingValues(bottom=110.dp),modifier=Modifier.weight(1f)) {
                items(filtered,key={it.optInt("id")}) { resident ->
                    Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF182C3D)),
                        border=BorderStroke(1.dp,Color(0xFF28576B)),
                        shape=RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${resident.optString("first_name")} ${resident.optString("last_name")}",
                                    fontWeight=FontWeight.SemiBold)
                                Text("ID ${resident.optInt("id")} · ${resident.optString("approval_status")}",
                                    color=rosterCyan)
                                if(resident.optBoolean("board_of_directors"))
                                    Text("Board member (protected)",color=Color.LightGray)
                            }
                            if(!resident.optBoolean("board_of_directors"))
                                TextButton(onClick={selected=resident}) { Text("Edit") }
                        }
                    }
                }
            }
        }
        if(busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        if(error.isNotBlank()) Text(error,color=Color(0xFFFFB6AC))
    }
    selected?.let { resident ->
        var first by remember(resident.optInt("id")) { mutableStateOf(resident.optString("first_name")) }
        var last by remember(resident.optInt("id")) { mutableStateOf(resident.optString("last_name")) }
        var status by remember(resident.optInt("id")) { mutableStateOf(resident.optString("approval_status")) }
        AlertDialog(
            onDismissRequest={if(!busy)selected=null},
            title={Text("Update Resident ${resident.optInt("id")}")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value=first,onValueChange={first=it},label={Text("First name")})
                    OutlinedTextField(value=last,onValueChange={last=it},label={Text("Last name")})
                    Text("Approval status")
                    listOf("pending","approved","rejected").forEach { option ->
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            RadioButton(selected=status==option,onClick={status=option})
                            Text(option.replaceFirstChar{it.uppercase()})
                        }
                    }
                    Text("Phone, address and board privileges cannot be changed here.",
                        style=MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton={
                TextButton(enabled=!busy && first.isNotBlank() && last.isNotBlank(),
                    onClick={
                        scope.launch {
                            busy=true;error=""
                            try {
                                ClubhouseApi.boardRosterUpdate(boardId,token,resident.optInt("id"),
                                    JSONObject().put("first_name",first.trim())
                                        .put("last_name",last.trim())
                                        .put("approval_status",status))
                                selected=null
                                val list=ClubhouseApi.boardRosterList(boardId,token)
                                val arr=list.optJSONArray("residents") ?: JSONArray()
                                residents=(0 until arr.length()).mapNotNull(arr::optJSONObject)
                            }catch(ex:Exception){error=ex.message ?: "Unable to save changes"}
                            finally{busy=false}
                        }
                    }) {Text("Save Changes")}
            },
            dismissButton={TextButton(onClick={selected=null},enabled=!busy){Text("Cancel")}}
        )
    }
}
