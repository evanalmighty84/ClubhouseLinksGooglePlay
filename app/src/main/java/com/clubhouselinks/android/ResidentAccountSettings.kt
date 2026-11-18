package com.clubhouselinks.android

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject

private val settingsCyan = Color(0xFF16C6E0)
private val settingsPurple = Color(0xFFB52BD3)
private val settingsBackdrop = Brush.verticalGradient(
    listOf(Color(0xFF002A33), Color(0xFF080B21), Color(0xFF33083B))
)
private val settingsPanel = Brush.linearGradient(
    listOf(Color(0xFF143D46), Color(0xFF1D1B35), Color(0xFF371642))
)

private fun JSONObject.safeText(field: String): String =
    optString(field, "").trim().takeUnless { it.isBlank() || it.equals("null", true) }.orEmpty()

@Composable
private fun SettingsPanel(
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .background(settingsPanel, RoundedCornerShape(24.dp))
            .then(Modifier.background(Color.Transparent))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun SettingsInfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top) {
        Text(title, color = settingsCyan, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.47f))
        Text(value, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.End,
            modifier = Modifier.weight(0.53f))
    }
}

@Composable
fun ResidentAccountSettings(
    session: AccountSession,
    readOnly: Boolean,
    onClose: () -> Unit,
    onManageResidents: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember(session.residentId) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(session.residentId) { mutableStateOf(true) }
    var error by remember(session.residentId) { mutableStateOf("") }
    var firstName by remember(session.residentId) { mutableStateOf("") }
    var lastName by remember(session.residentId) { mutableStateOf("") }
    var phone by remember(session.residentId) { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            loading = true
            error = ""
            try {
                val response = ClubhouseApi.residentProfile(session.residentId)
                val resident = response.optJSONObject("resident") ?: response
                profile = resident
                val prefs = context.getSharedPreferences(
                    "clubhouse_resident_profile_local_${session.residentId}", Context.MODE_PRIVATE
                )
                firstName = if (readOnly) resident.safeText("first_name")
                    else prefs.getString("first_name", null) ?: resident.safeText("first_name")
                lastName = if (readOnly) resident.safeText("last_name")
                    else prefs.getString("last_name", null) ?: resident.safeText("last_name")
                phone = if (readOnly) resident.safeText("phone")
                    else prefs.getString("phone", null) ?: resident.safeText("phone")
            } catch (e: Exception) {
                error = e.message ?: "Unable to load resident profile."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(session.residentId) { reload() }

    // A newly created resident may open this screen before Codemagic finishes.
    // Refresh only house-image fields; never overwrite locally edited form data.
    // The coroutine is cancelled when this screen closes or the resident changes.
    LaunchedEffect(session.residentId, profile?.safeText("address")) {
        val initial = profile ?: return@LaunchedEffect
        if (initial.safeText("address").isBlank() ||
            initial.safeText("house_image_url").isNotBlank()) return@LaunchedEffect

        // 36 checks at 5 seconds = 3 minutes, covering cold Codemagic starts,
        // while avoiding unlimited background polling and API cost.
        repeat(36) {
            delay(5_000)
            val current = profile ?: return@LaunchedEffect
            if (current.safeText("house_image_url").isNotBlank() ||
                current.safeText("address") != initial.safeText("address")) return@LaunchedEffect
            try {
                val response = ClubhouseApi.residentProfile(session.residentId)
                val fresh = response.optJSONObject("resident") ?: response
                if (fresh.safeText("address") != initial.safeText("address"))
                    return@LaunchedEffect
                if (fresh.safeText("house_image_url").isNotBlank()) {
                    profile = fresh // Compose recomposes Street View with the new Cloudinary URL
                    return@LaunchedEffect
                }
            } catch (_: Exception) {
                // Temporary network failures do not interrupt signup or edits.
            }
        }
    }
    Column(Modifier.fillMaxSize().background(settingsBackdrop)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Account Settings", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Account Settings",
                    tint = Color.White)
            }
        }
        Text("Manage your resident profile information for this device.",
            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.73f))
        if (readOnly) {
            Text("VIEWING AS RESIDENT · READ ONLY",
                color = settingsCyan, fontWeight = FontWeight.Bold)
        }
        if (loading) CircularProgressIndicator(color = settingsCyan)
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFFFA39A))
            OutlinedButton(onClick = ::reload) { Text("Retry loading") }
        }

        profile?.let { resident ->
            val isBoard = resident.optBoolean("board_of_directors", false)
            val role = if (isBoard) "HOA Administrator" else "Resident"
            val area = resident.safeText("neighborhood_name")
                .ifBlank { resident.safeText("display_area_name") }
                .ifBlank { session.neighborhoodName.takeUnless { it.equals("null", true) }.orEmpty() }
                .ifBlank { "Area not set" }
            val address = resident.safeText("address")
            val signupProvider = resident.safeText("signup_provider")
            val signupMethod = if (signupProvider.equals("apple", true)) "Apple" else "Email / Manual"

            Card(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, settingsCyan.copy(alpha = 0.65f)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18283A))
            ) {
                Column(Modifier.fillMaxWidth().background(settingsPanel).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Profile", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    OutlinedTextField(value = firstName, onValueChange = { firstName = it },
                        readOnly = readOnly, label = { Text("First Name") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = lastName, onValueChange = { lastName = it },
                        readOnly = readOnly, label = { Text("Last Name") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it },
                        readOnly = readOnly, label = { Text("Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Address is used for local area matching and nearby vendor activity. " +
                        "To protect location accuracy, address changes require creating a new profile or contacting support.",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, settingsPurple.copy(alpha = 0.7f)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF291A3E))) {
                Column(Modifier.fillMaxWidth().background(settingsPanel).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("Account Info", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    SettingsInfoRow("Account Type", role)
                    SettingsInfoRow("Resident ID", session.residentId.toString())
                    SettingsInfoRow("Area", area)
                    SettingsInfoRow("Signup Method", signupMethod)
                    SettingsInfoRow("Address", address.ifBlank { "Not set" })
                }
            }

            if (isBoard) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, settingsCyan.copy(alpha = 0.6f)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF18283A))
                ) {
                    Column(
                        Modifier.fillMaxWidth().background(settingsPanel).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("HOA Resident Management",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Manage the resident directory for $area. Add residents individually or upload a directory for review and import.",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium)
                        if (readOnly) {
                            Text("Support Mode does not grant board privileges. The board member's own phone must be verified before any directory access or import.",
                                color = settingsCyan, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = onManageResidents,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = settingsPurple)
                        ) { Text("Manage HOA Residents", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (address.isNotBlank()) {
                Card(shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, settingsCyan.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF18283A))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Home Street View", style = MaterialTheme.typography.titleLarge,
                            color = Color.White, fontWeight = FontWeight.Bold)
                        if (resident.safeText("house_image_url").isBlank() &&
                            !resident.isNull("latitude") && !resident.isNull("longitude")) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(17.dp),
                                    color = settingsCyan, strokeWidth = 2.dp)
                                Text("Preparing your home image… It will appear automatically.",
                                    color = settingsCyan,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        ResidentStreetViewCard(resident)
                        Text("Saved property imagery, when available. Synthetic testing images are labeled.",
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (!readOnly) {
                Button(
                    onClick = {
                        context.getSharedPreferences(
                            "clubhouse_resident_profile_local_${session.residentId}", Context.MODE_PRIVATE
                        ).edit()
                            .putString("first_name", firstName.trim())
                            .putString("last_name", lastName.trim())
                            .putString("phone", phone.trim())
                            .apply()
                        saveMessage = "Saved on this Android device only. Your server profile was not changed."
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = settingsPurple)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
                if (saveMessage.isNotBlank()) {
                    Text(saveMessage, color = settingsCyan,
                        style = MaterialTheme.typography.bodySmall)
                }
                Card(shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.6f)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF37112C))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Delete Account", style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFFF5555), fontWeight = FontWeight.Bold)
                        Text("Permanently deleting your account will remove resident information and linked records. " +
                            "Account deletion is not yet available in this Android screen; contact support to request deletion.",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
