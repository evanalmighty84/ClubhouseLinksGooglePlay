package com.clubhouselinks.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun VendorInbox(session: AccountSession) {
    var requests by remember(session.vendorId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selected by remember(session.vendorId) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(session.vendorId) { mutableStateOf(false) }
    var updating by remember(session.vendorId) { mutableStateOf(false) }
    var error by remember(session.vendorId) { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    val scope=rememberCoroutineScope()
    fun refresh(){
        if(!session.isVendor || loading)return
        scope.launch {
            loading=true;error=""
            try { requests=ClubhouseApi.vendorRequestsAll(session.vendorId) }
            catch(e:Exception){ error=e.message?:"Couldn't load requests" }
            finally {loading=false}
        }
    }
    LaunchedEffect(session.vendorId){refresh()}
    val visible=requests.filter {
        val s=it.optString("status","new").lowercase()
        when(filter){"New"->s=="new";"In progress"->s=="viewed"||s=="accepted";"Completed"->s=="completed";else->true}
    }
    ContentColumn{
        Text("Vendor service requests",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text(session.displayName)
        OutlinedButton(onClick={ { refresh() } },enabled=!loading){Text(if(loading)"Refreshing…" else "Refresh requests")}
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("All","New","In progress","Completed").forEach {
                FilterChip(selected=filter==it,onClick={filter=it},label={Text(it)})
            }
        }
        Text("${visible.size} displayed · ${requests.size} total loaded",style=MaterialTheme.typography.labelSmall)
        visible.forEach { req ->
            InfoCard(req.optString("service","Service request"),
                listOf(req.optString("resident_first_name"),req.optString("resident_display_area_name"),
                    req.optString("message"),"Status: ${req.optString("status","new")}")
                    .filter(String::isNotBlank).joinToString(" · "),
                "View request") {
                    selected=req
                    if(req.optString("status","new").lowercase()=="new"){
                        scope.launch {
                            try {ClubhouseApi.markVendorRequestViewed(session.vendorId,req.optString("id"));refresh()}
                            catch (_:Exception) { /* Do not block the detail view; status update is retryable */ }
                        }
                    }
                }
        }
        selected?.let { req ->
            HorizontalDivider()
            Text("Request details",style=MaterialTheme.typography.titleLarge)
            Text(req.optString("service","Service"))
            Text(req.optString("message","No description provided"))
            Text("Resident: "+listOf(req.optString("resident_first_name"),req.optString("resident_last_name")).filter(String::isNotBlank).joinToString(" "))
            Text("Location: ${req.optString("resident_address",req.optString("resident_display_area_name",""))}")
            Text("Status: ${req.optString("status")}")
            fun update(status:String) {
                scope.launch {
                    updating=true;error=""
                    try {
                        ClubhouseApi.updateVendorRequestStatus(session.vendorId,req.optString("id"),status)
                        selected=null;refresh()
                    }catch(e:Exception){error=e.message?:"Could not update request"}
                    finally{updating=false}
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={selected=null}){Text("Close")}
                val status=req.optString("status","new").lowercase()
                if(status=="new"||status=="viewed") {
                    Button(onClick={update("accepted")},enabled=!updating){Text("Accept")}
                    OutlinedButton(onClick={update("declined")},enabled=!updating){Text("Decline")}
                } else if(status=="accepted") {
                    Button(onClick={update("completed")},enabled=!updating){Text("Complete")}
                }
            }
        }
        Text("Production launch requires server-issued authenticated sessions. Vendor IDs alone must never authorize private request access.",style=MaterialTheme.typography.labelSmall)
    }
}
