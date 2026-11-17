package com.clubhouselinks.android

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Debug-only device enrollment identifier. It is NOT an APNs or FCM push token,
 * nor a credential by itself. The backend requires an explicit active, support-
 * enabled vendor device record for this exact identifier.
 * Never commit this token or place it in screenshots/logs.
 */
internal object SupportDeviceIdentity {
    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences("clubhouse_qa_support_device", Context.MODE_PRIVATE)
        prefs.getString("token", null)?.let { if (it.length == 64) return it }
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        prefs.edit().putString("token", token).apply()
        return token
    }
}

/** Android native translation of iOS SupportResidentPickerView.
 * Search results and selected resident come exclusively from authorized backend
 * endpoints. This never modifies the vendor's persisted login.
 */
@Composable
fun SupportResidentScreen(
    vendor: AccountSession,
    onClose: () -> Unit,
    onResidentSelected: (AccountSession) -> Unit
) {
    val context = LocalContext.current
    val token = remember { SupportDeviceIdentity.getOrCreate(context) }
    val scope = rememberCoroutineScope()
    var verifiedDevice by remember(vendor.vendorId) { mutableStateOf(false) }
    var authPending by remember(vendor.vendorId) { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var residents by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var switchingId by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }

    fun checkAuthorization() {
        scope.launch {
            authPending = true
            verifiedDevice = false
            try {
                // Same empty search the iOS app uses to check device authorization.
                ClubhouseApi.searchSupportResidents(vendor.vendorId, token, "")
                verifiedDevice = true
                error = ""
            } catch(e: Exception) {
                error = "Support access has not been authorized for this Android device. " +
                    "An administrator must enroll it for your support-enabled vendor. " +
                    (e.message ?: "")
            } finally { authPending = false }
        }
    }

    LaunchedEffect(vendor.vendorId) { if (vendor.isVendor && BuildConfig.DEBUG) checkAuthorization() }
    ContentColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Support Mode", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold)
            TextButton(onClick=onClose) { Text("Cancel") }
        }
        Text("Search for the resident account you want to troubleshoot.")
        if (!vendor.isVendor || !BuildConfig.DEBUG) {
            Text("This screen is available only to authorized vendor support devices in debug builds.")
        } else {
            if (authPending) CircularProgressIndicator()
            if (error.isNotBlank()) Text(error, color=MaterialTheme.colorScheme.error)
            if (!verifiedDevice) {
                Text("Authorization required", fontWeight=FontWeight.Bold)
                Text("This debug-device identifier must be registered as an active support device for the signed-in vendor. It is not an Android push notification token.")
                Text("Full enrollment identifier (64 characters)",
                    color=ClubhouseCyan, fontWeight=FontWeight.Bold)
                SelectionContainer {
                    Surface(
                        color=MaterialTheme.colorScheme.surfaceVariant,
                        shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier=Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            token.chunked(16).forEach { chunk ->
                                Text(chunk, fontFamily=FontFamily.Monospace,
                                    style=MaterialTheme.typography.bodyMedium,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text("Four rows of 16 characters. The emulator clipboard may not sync to your Mac. Keep the identifier private.",
                    style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Clubhouse Links QA support device", token))
                }) { Text("Copy enrollment identifier") }
                OutlinedButton(onClick={checkAuthorization()}, enabled=!authPending) {
                    Text("Check authorization again")
                }
            } else {
                OutlinedTextField(
                    value=query, onValueChange={query=it},
                    label={Text("Name, phone, address or ID")},
                    modifier=Modifier.fillMaxWidth(),
                    singleLine=true
                )
                Button(onClick={
                    scope.launch {
                        if (query.trim().length < 2) {
                            error="Enter at least 2 characters."
                            residents=emptyList()
                            return@launch
                        }
                        busy=true;error=""
                        try {
                            val response=ClubhouseApi.searchSupportResidents(vendor.vendorId,token,query.trim())
                            val arr=response.optJSONArray("residents") ?: JSONArray()
                            residents=(0 until arr.length()).mapNotNull{arr.optJSONObject(it)}
                            if(residents.isEmpty())error="No residents matched that search."
                        }catch(e:Exception){
                            residents=emptyList()
                            error=e.message ?: "Unable to search residents."
                        }finally{busy=false}
                    }
                },enabled=!busy) { Text(if(busy) "Searching…" else "Search residents") }
                if(busy) CircularProgressIndicator()
                residents.forEach { person ->
                    val id=person.optInt("id")
                    val name=listOf(person.optString("first_name"),person.optString("last_name"))
                        .filter(String::isNotBlank).joinToString(" ").ifBlank{"Resident #$id"}
                    val description=listOf(
                        person.optString("address"),
                        person.optString("phone"),
                        "Resident ID: $id"
                    ).filter(String::isNotBlank).joinToString("\n")
                    InfoCard(name,description,if(switchingId==id) "Opening…" else "View resident") {
                        if(switchingId!=0)return@InfoCard
                        scope.launch {
                            switchingId=id;error=""
                            try{
                                val response=ClubhouseApi.switchSupportResident(vendor.vendorId,token,id)
                                val resident=response.optJSONObject("resident")
                                    ?: throw IllegalStateException("Support server did not return a resident.")
                                val residentId=resident.optInt("id")
                                if(residentId<=0)throw IllegalStateException("Invalid resident profile.")
                                val residentName=listOf(resident.optString("first_name"),resident.optString("last_name"))
                                    .filter(String::isNotBlank).joinToString(" ")
                                onResidentSelected(AccountSession(
                                    accountType="resident", residentId=residentId,
                                    displayName=residentName,
                                    neighborhoodName=resident.optString("neighborhood_name")
                                ))
                            }catch(e:Exception){
                                error=e.message ?: "Unable to open selected resident."
                            }finally{switchingId=0}
                        }
                    }
                }
            }
        }
    }
}
