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
                val onLogin: (AccountSession) -> Unit = { new -> store.save(new); session = new; page = "Home" }
                val signOut: () -> Unit = { store.clear(); session = AccountSession(); page = "Home" }
                ClubhouseNavigation(
                    session = session,
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
    page: String,
    onPage: (String) -> Unit,
    onLogin: (AccountSession) -> Unit,
    onSignOut: () -> Unit
) {
    val tabs = listOf("Home","Events","Services","Requests")
    Scaffold(
        topBar = {
            Surface(color = ClubhouseNavy) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CLUBHOUSE LINKS", color = ClubhouseCyan, fontWeight = FontWeight.Black)
                        Text(session.neighborhoodName.ifBlank { if (session.isVendor) session.displayName else "Your neighborhood, connected" }, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { onPage("Account") }) { Text(if(session.isResident || session.isVendor) "Account" else "Sign in") }
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
                                "Services" -> Icons.Default.Handyman
                                else -> Icons.Default.Inbox
                            }, contentDescription = tab)
                        },
                        label = { Text(tab) }
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when(page) {
                "Home" -> HomeScreen(session, onPage)
                "Account" -> AccountScreen(session, onLogin, onSignOut)
                "Events" -> EventsScreen(session, onPage)
                "Services" -> ServicesScreen(session, onPage)
                "Requests" -> RequestsScreen(session, onPage)
                else -> HomeScreen(session, onPage)
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
        InfoCard("Community events", "See neighborhood gatherings, HOA events and upcoming activities.", "View events") { navigate("Events") }
        InfoCard("Find a local provider", "Browse neighborhood vendors and request a quote or appointment.", "Browse services") { navigate("Services") }
        InfoCard(if(session.isVendor) "Service requests" else "My requests", "Check requests and their progress.", "Open inbox") { navigate("Requests") }
        Text("Android • Clubhouse Links", style = MaterialTheme.typography.labelSmall)
    }
}
