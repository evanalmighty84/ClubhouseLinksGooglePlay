package com.clubhouselinks.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val ClubhouseNavy = Color(0xFF0C1230)
val ClubhouseCyan = Color(0xFF16C6E0)
val ClubhouseOrange = Color(0xFFFF9800)
private val scheme = darkColorScheme(primary = ClubhouseCyan, secondary = ClubhouseOrange, background = ClubhouseNavy, surface = Color(0xFF151E44))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(this)
        setContent {
            MaterialTheme(colorScheme = scheme) {
                var session by remember { mutableStateOf(store.read()) }
                var page by remember { mutableStateOf("Home") }
                // Support mode is intentionally temporary: the vendor remains the
                // persisted account, exactly as in iOS SupportResidentPickerView.
                var supportResident by remember { mutableStateOf<AccountSession?>(null) }
                var supportError by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()
                val onLogin: (AccountSession) -> Unit = { new ->
                    store.save(new); session = new; supportResident = null; page = "Home"
                }
                val signOut: () -> Unit = {
                    store.clear(); session = AccountSession(); supportResident = null; page = "Home"
                }
                val exitSupport: () -> Unit = {
                    if (supportResident != null && session.isVendor) {
                        scope.launch {
                            try {
                                ClubhouseApi.clearSupportResident(
                                    session.vendorId, SupportDeviceIdentity.getOrCreate(this@MainActivity)
                                )
                                supportResident = null
                                supportError = ""
                                page = "Home"
                            } catch (e: Exception) {
                                supportError = e.message ?: "Unable to end support mode."
                            }
                        }
                    }
                }
                ClubhouseNavigation(
                    session = supportResident ?: session,
                    vendorSession = session,
                    supportModeActive = supportResident != null,
                    supportError = supportError,
                    onExitSupport = exitSupport,
                    onSupportSelected = { selected ->
                        supportResident = selected
                        supportError = ""
                        page = "Home"
                    },
                    page = page,
                    onPage = { page = it },
                    onLogin = onLogin,
                    onSignOut = signOut
                )
            }
        }
    }
}

@Composable
private fun ClubhouseNavigation(
    session: AccountSession,
    vendorSession: AccountSession,
    supportModeActive: Boolean,
    supportError: String,
    onExitSupport: () -> Unit,
    onSupportSelected: (AccountSession) -> Unit,
    page: String,
    onPage: (String) -> Unit,
    onLogin: (AccountSession) -> Unit,
    onSignOut: () -> Unit
) {
    val tabs = if (session.isVendor) listOf("Home","Events","Jobs Submitted","Service Requests")
    else listOf("Home","Events","Submit Job","Request Service")
    Scaffold(
        topBar = {
            Surface(color = ClubhouseNavy, modifier = Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CLUBHOUSE LINKS", color = ClubhouseCyan, fontWeight = FontWeight.Black)
                        Text(session.neighborhoodName.ifBlank { if (session.isVendor) session.displayName else "Your neighborhood, connected" }, style = MaterialTheme.typography.labelSmall)
                    }
                    if (supportModeActive) {
                        TextButton(onClick = onExitSupport) { Text("Exit Support") }
                    } else {
                        if (BuildConfig.DEBUG) {
                            TextButton(onClick = { onPage("QA Preview") }) { Text("Preview") }
                        }
                        TextButton(onClick = { onPage("Account") }) {
                            Text(if(session.isResident || session.isVendor) "Account" else "Sign in")
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = ClubhouseNavy) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = page == tab,
                        onClick = { onPage(tab) },
                        icon = {
                            Icon(when(tab) {
                                "Home" -> Icons.Default.Home
                                "Events" -> Icons.Default.CalendarMonth
                                "Submit Job", "Jobs Submitted" -> Icons.Default.Send
                                else -> Icons.Default.Call
                            }, contentDescription = tab)
                        },
                        label = { Text(tab) }
                    )
                }
            }
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (supportError.isNotBlank()) Text(supportError, color = MaterialTheme.colorScheme.error)
            if (supportModeActive) {
                Surface(color = Color(0xFF203851), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("VIEWING AS RESIDENT · READ ONLY",
                                color = ClubhouseCyan,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge)
                            Text(session.displayName.ifBlank { "Resident" } +
                                " · " + session.neighborhoodName,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = onExitSupport) { Text("Return to Aspen") }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
            when(page) {
                "Home" -> if(session.isResident) ResidentParityHome(session, onPage, readOnly=supportModeActive) else NativeHome(session, onPage)
                "Account" -> if (supportModeActive) NativeHome(session, onPage)
                    else AccountScreen(session, onLogin, onSignOut)
                "QA Preview" -> if (BuildConfig.DEBUG) {
                    QaPreviewScreen(
                        onClose = { onPage("Home") },
                        onRealResidentSearch = if (vendorSession.isVendor && !supportModeActive) {
                            { onPage("Support Search") }
                        } else null
                    )
                } else NativeHome(session, onPage)
                "Events" -> EventsScreen(session, onPage)
                "Submit Job" -> SubmitJobScreen(session, onPage)
                "Jobs Submitted" -> JobsSubmittedScreen(session, onPage)
                "Request Service" -> RequestsScreen(session, onPage, readOnly = supportModeActive)
                "Service Requests" -> RequestsScreen(session, onPage)
                "Services" -> ServicesScreen(session, onPage, readOnly = supportModeActive)
                "Requests" -> RequestsScreen(session, onPage, readOnly = supportModeActive)
                "Street Fair" -> StreetFairScreen(session, onPage)
                "Support Search" -> if (BuildConfig.DEBUG && vendorSession.isVendor && !supportModeActive) {
                    SupportResidentScreen(
                        vendor=vendorSession,
                        onClose={onPage("Home")},
                        onResidentSelected=onSupportSelected
                    )
                } else NativeHome(session,onPage)
                else -> NativeHome(session, onPage)
            }
            }
        }
    }
}

@Composable
fun ContentColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
fun InfoCard(title: String, description: String, action: String? = null, onClick: (() -> Unit)? = null) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if(description.isNotBlank()) Text(description)
            if(action != null && onClick != null) TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
fun HomeScreen(session: AccountSession, navigate: (String) -> Unit) {
    ContentColumn {
        Text("Your neighborhood, connected.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Find local businesses, request service, and join community activities.")
        if (!session.isVendor && !session.isResident) {
            InfoCard("Get started", "Verify your phone and join your HOA community. Existing vendors can access their service requests.", "Sign in or join") { navigate("Account") }
        } else {
            InfoCard("Hello, ${session.displayName.ifBlank { "neighbor" }}", if(session.isVendor) "Vendor dashboard" else session.neighborhoodName.ifBlank { "Resident account" })
        }
        InfoCard("Neighborhood Street Fair", "See community experiences, food trucks, and participating local businesses.", "Explore Street Fair") { navigate("Street Fair") }
        InfoCard("Community events", "See neighborhood gatherings, HOA events and upcoming activities.", "View events") { navigate("Events") }
        InfoCard("Find a local provider", "Browse neighborhood vendors and request a quote or appointment.", "Browse services") { navigate("Services") }
        InfoCard(if(session.isVendor) "Service requests" else "My requests", "Check requests and their progress.", "Open inbox") { navigate("Requests") }
        Text("Android • Clubhouse Links", style = MaterialTheme.typography.labelSmall)
    }
}
