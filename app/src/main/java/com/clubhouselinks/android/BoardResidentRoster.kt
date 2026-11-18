package com.clubhouselinks.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var communityName by remember(boardId) { mutableStateOf("") }
    var residentInviteCode by remember(boardId) { mutableStateOf("") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var showAddResident by remember { mutableStateOf(false) }
    var showImportDirectory by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }

    fun load() {
        if (token.isBlank() || busy) return
        scope.launch {
            busy = true; error = ""
            try {
                val response = ClubhouseApi.boardRosterList(boardId, token)
                val array = response.optJSONArray("residents") ?: JSONArray()
                residents = (0 until array.length()).mapNotNull(array::optJSONObject)
                communityName = response.optJSONObject("community")?.optString("neighborhood_name").orEmpty()
                residentInviteCode = response.optJSONObject("community")?.optString("invite_code").orEmpty()
            } catch (ex: Exception) {
                if (ex is ApiException && (ex.status == 401 || ex.status == 403)) {
                    token = ""; sent = false; residents = emptyList()
                }
                error = ex.message ?: "Unable to load HOA residents"
            } finally {busy = false}
        }
    }
    if (showImportDirectory && token.isNotBlank()) {
        BoardResidentDirectoryImport(
            boardId = boardId,
            token = token,
            community = communityName,
            inviteCode = residentInviteCode,
            onClose = { showImportDirectory = false },
            onImported = {
                showImportDirectory = false
                load()
            }
        )
        return
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
                                communityName=list.optJSONObject("community")?.optString("neighborhood_name").orEmpty()
                                residentInviteCode=list.optJSONObject("community")?.optString("invite_code").orEmpty()
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
            Text(if (communityName.isBlank()) "Community resident invite code" else "$communityName resident invite code",
                style = MaterialTheme.typography.titleSmall, color = Color.White)
            OutlinedTextField(
                value = residentInviteCode.ifBlank { "No active resident invite code configured" },
                onValueChange = {},
                enabled = false,
                label = { Text("Resident Invite Code (read-only)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = { showAddResident = true; error = ""; successMessage = "" },
                enabled = !busy && residentInviteCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add Resident") }
            OutlinedButton(
                onClick = { showImportDirectory = true; error = ""; successMessage = "" },
                enabled = !busy && residentInviteCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Upload Resident Directory") }
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
        if(successMessage.isNotBlank()) Text(successMessage,color=Color(0xFF9CE2B4))
    }
    if (showAddResident && token.isNotBlank()) {
        var firstName by remember { mutableStateOf("") }
        var lastName by remember { mutableStateOf("") }
        var mobile by remember { mutableStateOf("") }
        var homeAddress by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!busy) showAddResident = false },
            title = { Text("Add Community Resident") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = residentInviteCode,
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Community Invite Code") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it.take(80) },
                        label = { Text("First Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it.take(80) },
                        label = { Text("Last Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it.filter(Char::isDigit).take(10) },
                        label = { Text("Mobile Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = homeAddress,
                        onValueChange = { homeAddress = it.take(500) },
                        label = { Text("Home Address, City, State, ZIP") },
                        minLines = 2
                    )
                    Text("Community membership is approved immediately. The resident will verify their own phone when signing in.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && residentInviteCode.isNotBlank() &&
                        firstName.isNotBlank() && lastName.isNotBlank() &&
                        mobile.length == 10 && homeAddress.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            successMessage = ""
                            try {
                                ClubhouseApi.boardRosterCreate(
                                    boardId, token,
                                    JSONObject()
                                        .put("first_name", firstName.trim())
                                        .put("last_name", lastName.trim())
                                        .put("phone", mobile)
                                        .put("address", homeAddress.trim())
                                )
                                showAddResident = false
                                successMessage = "Resident added and approved. Phone verification remains required."
                                val list = ClubhouseApi.boardRosterList(boardId, token)
                                val arr = list.optJSONArray("residents") ?: JSONArray()
                                residents = (0 until arr.length()).mapNotNull(arr::optJSONObject)
                                communityName = list.optJSONObject("community")?.optString("neighborhood_name").orEmpty()
                                residentInviteCode = list.optJSONObject("community")?.optString("invite_code").orEmpty()
                            } catch (ex: Exception) {
                                if (ex is ApiException && (ex.status == 401 || ex.status == 403)) {
                                    token = ""; sent = false; showAddResident = false
                                }
                                error = ex.message ?: "Unable to add resident"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Add & Approve") }
            },
            dismissButton = {
                TextButton(onClick = { showAddResident = false }, enabled = !busy) { Text("Cancel") }
            }
        )
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
                                communityName=list.optJSONObject("community")?.optString("neighborhood_name").orEmpty()
                                residentInviteCode=list.optJSONObject("community")?.optString("invite_code").orEmpty()
                            }catch(ex:Exception){error=ex.message ?: "Unable to save changes"}
                            finally{busy=false}
                        }
                    }) {Text("Save Changes")}
            },
            dismissButton={TextButton(onClick={selected=null},enabled=!busy){Text("Cancel")}}
        )
    }
}
