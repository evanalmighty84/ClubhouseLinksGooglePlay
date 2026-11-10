package com.clubhouselinks.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ProfileDetails(session: AccountSession) {
    val scope = rememberCoroutineScope()
    var busy by remember(session.residentId,session.vendorId) { mutableStateOf(false) }
    var error by remember(session.residentId,session.vendorId) { mutableStateOf("") }
    var data by remember(session.residentId,session.vendorId) { mutableStateOf<JSONObject?>(null) }
    var projects by remember(session.residentId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    fun refresh(){
        if(busy)return
        scope.launch {
            busy=true;error=""
            try {
                if(session.isVendor){
                    val response=ClubhouseApi.vendorProfile(session.vendorId)
                    data=response.optJSONObject("vendor")?:response
                } else if(session.isResident){
                    val response=ClubhouseApi.residentProfile(session.residentId)
                    data=response.optJSONObject("resident")?:response
                    val projResponse=ClubhouseApi.residentCompletedProjects(session.residentId)
                    val arr=projResponse.optJSONArray("projects")?:JSONArray()
                    projects=(0 until arr.length()).mapNotNull{arr.optJSONObject(it)}
                }
            }catch(e:Exception){error=e.message?:"Unable to load profile"}
            finally {busy=false}
        }
    }
    LaunchedEffect(session.accountType,session.residentId,session.vendorId){refresh()}
    Text("Profile details",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    OutlinedButton(onClick={refresh},enabled=!busy){Text(if(busy)"Loading…" else "Refresh profile")}
    if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
    data?.let{ p ->
        val rows=if(session.isVendor) listOf("company_name","category","contact_name","email","website","description")
        else listOf("first_name","last_name","neighborhood_name","display_area_name","approval_status")
        rows.forEach { key->
            val value=p.optString(key)
            if(value.isNotBlank() && value!="null") Text("${key.replace('_',' ').replaceFirstChar { it.uppercase() }}: $value")
        }
    }
    if(session.isResident && projects.isNotEmpty()){
        HorizontalDivider()
        Text("Completed projects",style=MaterialTheme.typography.titleMedium)
        projects.forEach{ p ->
            InfoCard(p.optString("service","Completed project"),
                listOf(p.optString("description"),p.optString("created_at")).filter(String::isNotBlank).joinToString("\n"))
        }
    }
}
