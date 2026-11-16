package com.clubhouselinks.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Native equivalent of iOS RequestView's vendor branch: VendorCompletedProjectsView. */
@Composable
fun JobsSubmittedScreen(session: AccountSession, navigate: (String) -> Unit) {
    if (!session.isVendor) {
        SubmitJobScreen(session, navigate)
        return
    }
    var projects by remember(session.vendorId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember(session.vendorId) { mutableStateOf(false) }
    var error by remember(session.vendorId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun refresh() {
        scope.launch {
            loading = true; error = ""
            try {
                val result = ClubhouseApi.vendorCompletedProjects(session.vendorId)
                val arr = result.optJSONArray("projects") ?: JSONArray()
                projects = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            } catch (e: Exception) { error = e.message ?: "Unable to load completed projects" }
            finally { loading = false }
        }
    }
    LaunchedEffect(session.vendorId) { refresh() }
    ContentColumn {
        Text("Jobs Submitted", style=MaterialTheme.typography.headlineLarge, fontWeight=FontWeight.Bold)
        Text("Completed projects submitted for your customers.")
        OutlinedButton(onClick={refresh()}, enabled=!loading) { Text("Refresh projects") }
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) Text(error, color=MaterialTheme.colorScheme.error)
        if (!loading && error.isBlank() && projects.isEmpty()) Text("No completed projects on file.")
        projects.forEach { project ->
            val name = listOf(project.optString("resident_first_name"),
                project.optString("resident_last_name")).filter(String::isNotBlank).joinToString(" ")
            InfoCard(
                name.ifBlank { "Customer project" },
                listOf(project.optString("service"), project.optString("resident_display_area_name"),
                    project.optString("approval_status"), project.optString("moderation_status"))
                    .filter(String::isNotBlank).joinToString(" · ")
            )
        }
    }
}

/** iOS RequestView's resident branch includes photo capture/upload and vendor selection.
 *  Those must be ported before enabling project submission on Android. */
@Composable
fun SubmitJobScreen(session: AccountSession, navigate: (String) -> Unit) {
    ContentColumn {
        Text("Submit New Project", style=MaterialTheme.typography.headlineLarge, fontWeight=FontWeight.Bold)
        Text("Share a finished project from a contractor you used so nearby neighbors can discover trusted home service providers.")
        if (!session.isResident) {
            InfoCard("Sign in to submit a project", "Your project must belong to a verified resident profile.",
                "Sign in") { navigate("Account") }
        } else {
            InfoCard("Completed projects", "Your submitted projects and account details are available below.")
            ProfileDetails(session)
            Text("Photo upload and vendor selection from the iOS project submission screen are not yet implemented in Android. No project will be created from this screen.",
                style=MaterialTheme.typography.bodySmall)
        }
    }
}
