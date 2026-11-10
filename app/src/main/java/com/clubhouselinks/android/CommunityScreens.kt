package com.clubhouselinks.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.displayName(): String =
    optString("company_name").ifBlank { optString("name","Local provider") }

@Composable
fun EventsScreen(session: AccountSession, navigate: (String)->Unit) {
    var loading by remember(session.residentId) { mutableStateOf(false) }
    var events by remember(session.residentId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember(session.residentId) { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    fun refresh(){
        if(!session.isResident || loading) return
        scope.launch {
            loading=true; error=""
            try {
                val response=ClubhouseApi.residentEvents(session.residentId)
                val arr=response.optJSONArray("events") ?: JSONArray()
                events=(0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            }catch(e:Exception){error=e.message ?: "Unable to load events"}finally{loading=false}
        }
    }
    LaunchedEffect(session.residentId){ if(session.isResident) refresh() }
    ContentColumn {
        Text("Neighborhood events",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Stay informed about events and activities near your community.")
        if(!session.isResident) InfoCard("Sign in to see events","Event listings are tied to your verified neighborhood account.","Sign in"){ navigate("Account") }
        else {
            Button(onClick={refresh},enabled=!loading){ Text(if(loading)"Refreshing…" else "Refresh events") }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            if(!loading && error.isBlank() && events.isEmpty()) Text("No community events are listed for this account.")
            events.forEach { item->
                InfoCard(
                    item.optString("title","Community event"),
                    listOf(item.optString("starts_at"),item.optString("neighborhood_name"),item.optString("address"),item.optString("description"))
                        .filter(String::isNotBlank).joinToString("\n")
                )
            }
        }
    }
}

@Composable
fun ServicesScreen(session: AccountSession, navigate: (String)->Unit) {
    var vendors by remember(session.residentId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember(session.residentId) { mutableStateOf("") }
    var busy by remember(session.residentId) { mutableStateOf(false) }
    var selectedVendor by remember { mutableStateOf<JSONObject?>(null) }
    var selectedService by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var submitError by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    fun refresh(){
        if(!session.isResident || busy)return
        scope.launch{
            busy=true;error=""
            try{
                val response=ClubhouseApi.residentVendors(session.residentId)
                val arr=response.optJSONArray("vendors") ?: JSONArray()
                vendors=(0 until arr.length()).mapNotNull{arr.optJSONObject(it)}
            }catch(e:Exception){error=e.message?:"Unable to load vendors"}
            finally{busy=false}
        }
    }
    LaunchedEffect(session.residentId){ if(session.isResident)refresh() }
    ContentColumn {
        Text("Local services",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Find participating vendors and request help in your community.")
        if(!session.isResident) InfoCard("Join your community","Verify your resident account to see neighborhood providers and submit service requests.","Sign in"){navigate("Account")}
        else {
            OutlinedButton(onClick={refresh},enabled=!busy){ Text(if(busy)"Loading…" else "Refresh directory") }
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            if(!busy && error.isBlank() && vendors.isEmpty()) Text("No providers found for your neighborhood.")
            vendors.forEach { vendor->
                val label=vendor.displayName()
                val category=vendor.optString("category",vendor.optString("description"))
                InfoCard(label,category,"Request service"){selectedVendor=vendor;selectedService=category;message="";submitError=""}
            }
        }
        selectedVendor?.let { vendor ->
            HorizontalDivider()
            Text("Request service from ${vendor.displayName()}",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(selectedService,{selectedService=it},Modifier.fillMaxWidth(),label={Text("Service")})
            OutlinedTextField(message,{message=it},Modifier.fillMaxWidth(),label={Text("Describe the work")},minLines=3)
            if(submitError.isNotBlank()) Text(submitError,color=MaterialTheme.colorScheme.error)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedButton(onClick={selectedVendor=null}){Text("Cancel")}
                Button(enabled=!submitting && message.isNotBlank() && selectedService.isNotBlank(),onClick={
                    scope.launch {
                        submitting=true;submitError=""
                        try {
                            val result=ClubhouseApi.sendResidentRequest(session.residentId,
                                JSONObject().put("vendor_id",vendor.optInt("id")).put("service",selectedService)
                                    .put("sub_service",JSONObject.NULL).put("message",message.trim()))
                            if(result.optBoolean("success",false)){
                                selectedVendor=null;message="";submitError="Request submitted. Check with the vendor for next steps."
                            }else submitError=result.optString("error","Request was not accepted")
                        }catch(e:Exception){submitError=e.message?:"Could not submit request"}
                        finally{submitting=false}
                    }
                }) { Text(if(submitting)"Submitting…" else "Submit request") }
            }
        }
    }
}

@Composable
fun RequestsScreen(session: AccountSession, navigate: (String)->Unit) {
    if(session.isVendor) {
        VendorInbox(session)
    } else {
        ContentColumn {
            Text("Service requests",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            if(session.isResident) {
                InfoCard("Need a contractor?","Browse your neighborhood vendor directory and describe your project.","Browse vendors"){navigate("Services")}
                ResidentRequestHistory(session)
            } else InfoCard("Sign in","Please verify your account to send service requests.","Sign in"){navigate("Account")}
        }
    }
}
