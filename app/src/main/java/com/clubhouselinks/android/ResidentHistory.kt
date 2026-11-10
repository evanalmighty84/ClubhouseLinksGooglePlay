package com.clubhouselinks.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ResidentRequestHistory(session: AccountSession) {
    var busy by remember(session.residentId) { mutableStateOf(false) }
    var error by remember(session.residentId) { mutableStateOf("") }
    var items by remember(session.residentId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun reload() {
        if(!session.isResident || busy)return
        scope.launch {
            busy=true;error=""
            try {
                val result=ClubhouseApi.residentRequests(session.residentId)
                val arr=result.optJSONArray("requests")?:JSONArray()
                items=(0 until arr.length()).mapNotNull{arr.optJSONObject(it)}
            } catch(e:Exception) { error=e.message ?: "Unable to load request history" }
            finally { busy=false }
        }
    }
    LaunchedEffect(session.residentId) { if(session.isResident)reload() }
    Text("My service requests",style=MaterialTheme.typography.titleLarge)
    OutlinedButton(onClick={reload},enabled=!busy) { Text(if(busy)"Refreshing…" else "Refresh history") }
    if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
    if(!busy && error.isBlank() && items.isEmpty()) Text("No requests on file.")
    items.forEach { request ->
        InfoCard(request.optString("service","Service request"),
            listOf(
                request.optString("vendor_company_name"),
                request.optString("message"),
                "Status: ${request.optString("status","new")}",
                request.optString("created_at")
            ).filter(String::isNotBlank).joinToString("\n")
        )
    }
}
