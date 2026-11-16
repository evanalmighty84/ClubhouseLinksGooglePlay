package com.clubhouselinks.android

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val RequestPurple=Color(0xFF571A76)
private val RequestNavy=Color(0xFF092C3A)
private val RequestGradient=Brush.linearGradient(listOf(RequestNavy,Color(0xFF251630),RequestPurple))

private fun JSONObject.requestText(key:String):String =
    optString(key).trim().takeUnless { it.isEmpty() || it == "null" } ?: ""

private fun requestDate(raw:String):String{
    if(raw.isBlank())return ""
    val patterns=listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX","yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX","yyyy-MM-dd HH:mm:ss.SSSSSS",
        "yyyy-MM-dd HH:mm:ss")
    for(pattern in patterns)try{
        val f=SimpleDateFormat(pattern,Locale.US)
        f.timeZone=TimeZone.getTimeZone("UTC")
        val parsed=f.parse(raw) ?: continue
        return SimpleDateFormat("MMM d, yyyy",Locale.US).format(parsed)
    }catch(_:Exception){}
    return raw.take(10)
}

private fun JSONObject.requestLocation():String =
    requestText("resident_display_area_name").ifBlank{
        requestText("resident_address")
    }

private fun JSONObject.requestPostLabel():String {
    val post=requestText("original_post_at")
    if(post.isNotBlank())return "Posted ${requestDate(post)}"
    val posted=requestText("posted_at_text")
    if(posted.isNotBlank())return "Posted $posted"
    val created=requestText("created_at")
    return if(created.isNotBlank())"Received ${requestDate(created)}" else ""
}

private fun JSONObject.customerName():String =
    listOf(requestText("resident_first_name"),requestText("resident_last_name"))
        .filter(String::isNotBlank).joinToString(" ").ifBlank{"Homeowner"}

@Composable
fun VendorInbox(session:AccountSession) {
    var requests by remember(session.vendorId){mutableStateOf<List<JSONObject>>(emptyList())}
    var selectedId by remember(session.vendorId){mutableStateOf("")}
    var loading by remember(session.vendorId){mutableStateOf(false)}
    var updating by remember(session.vendorId){mutableStateOf(false)}
    var error by remember(session.vendorId){mutableStateOf("")}
    var detailError by remember(session.vendorId){mutableStateOf("")}
    var filter by remember{mutableStateOf("All")}
    val scope=rememberCoroutineScope()

    fun refresh() {
        if(!session.isVendor || loading)return
        scope.launch{
            loading=true;error=""
            try{requests=ClubhouseApi.vendorRequestsAll(session.vendorId)}
            catch(e:Exception){error=e.message ?: "Unable to load service requests"}
            finally{loading=false}
        }
    }
    LaunchedEffect(session.vendorId){refresh()}
    val selected=requests.firstOrNull{it.requestText("id")==selectedId}
    val visible=requests.filter{
        when(filter){
            "New"->it.requestText("status")=="new"
            "In progress"->it.requestText("status") in listOf("viewed","accepted")
            "Completed"->it.requestText("status")=="completed"
            "Declined"->it.requestText("status")=="declined"
            else->true
        }
    }

    fun markViewed(request:JSONObject) {
        if(request.requestText("status")!="new")return
        scope.launch {
            try{
                ClubhouseApi.markVendorRequestViewed(session.vendorId,request.requestText("id"))
                refresh()
            }catch(e:Exception){
                detailError=e.message ?: "Unable to mark request viewed"
            }
        }
    }
    fun changeStatus(request:JSONObject,status:String) {
        if(updating)return
        scope.launch{
            updating=true;detailError=""
            try{
                ClubhouseApi.updateVendorRequestStatus(
                    session.vendorId,request.requestText("id"),status)
                selectedId=""
                refresh()
            }catch(e:Exception){
                detailError=e.message ?: "Unable to update request"
            }finally{updating=false}
        }
    }

    if(selected!=null){
        VendorRequestDetail(
            request=selected,
            updating=updating,
            error=detailError,
            onClose={selectedId="";detailError=""},
            onChangeStatus={status->changeStatus(selected,status)}
        )
    }else{
        LazyColumn(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color(0xFF002C34),ClubhouseNavy,Color(0xFF200633)))),
            contentPadding=PaddingValues(17.dp),
            verticalArrangement=Arrangement.spacedBy(15.dp)
        ){
            item{
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("Service Requests",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text(session.displayName.uppercase(),color=ClubhouseCyan,fontWeight=FontWeight.Bold)
                    OutlinedButton(onClick={refresh()},enabled=!loading){
                        Text(if(loading)"Refreshing…" else "Refresh requests")
                    }
                    if(loading)CircularProgressIndicator(color=ClubhouseCyan)
                    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                    // Scrollable chips prevent clipped labels on smaller phones.
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        items(listOf("All","New","In progress","Completed","Declined")){value->
                            FilterChip(selected=filter==value,onClick={filter=value},
                                label={Text(value)})
                        }
                    }
                    Text("${visible.size} displayed · ${requests.size} total",
                        style=MaterialTheme.typography.labelSmall)
                }
            }
            items(visible,key={it.requestText("id")}){request->
                RequestListCard(request){
                    selectedId=request.requestText("id")
                    detailError=""
                    markViewed(request)
                }
            }
            if(!loading && visible.isEmpty()){
                item{Text("No requests in this category.")}
            }
        }
    }
}

@Composable
private fun RequestListCard(request:JSONObject,onClick:()->Unit){
    val status=request.requestText("status").ifBlank{"new"}
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),
        colors=CardDefaults.cardColors(containerColor=Color(0xFF202038))){
        Column(Modifier.fillMaxWidth().background(RequestGradient).padding(19.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(request.requestText("service").ifBlank{"Service Request"},
                        style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(request.customerName(),color=ClubhouseCyan,fontWeight=FontWeight.Bold)
                }
                StatusPill(status)
            }
            Text(request.requestText("message"),maxLines=3,overflow=TextOverflow.Ellipsis,
                color=Color.White.copy(alpha=0.84f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text(request.requestLocation(),color=Color.White.copy(alpha=0.78f),
                    modifier=Modifier.weight(1f),maxLines=2)
                Spacer(Modifier.width(8.dp))
                Text(request.requestPostLabel(),color=Color.White.copy(alpha=0.7f),
                    style=MaterialTheme.typography.labelSmall)
            }
            val keyword=request.requestText("sub_service")
            if(keyword.isNotBlank())Text("Keyword: $keyword",color=ClubhouseCyan,
                style=MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StatusPill(status:String){
    Surface(shape=RoundedCornerShape(50),
        color=if(status=="new")ClubhouseCyan else Color(0xFF0CB0D0)){
        Text(status.replaceFirstChar{it.uppercase()},color=Color.Black,
            fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=14.dp,vertical=7.dp),
            style=MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DetailPanel(title:String,body:@Composable ColumnScope.()->Unit){
    Card(
        modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(26.dp),
        colors=CardDefaults.cardColors(containerColor=Color(0xFF14273C)),
        border=androidx.compose.foundation.BorderStroke(1.dp,ClubhouseCyan.copy(alpha=0.44f))
    ){
        Column(Modifier.fillMaxWidth().background(RequestGradient).padding(19.dp),
            verticalArrangement=Arrangement.spacedBy(13.dp)){
            Text(title,color=ClubhouseCyan,style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.Bold)
            body()
        }
    }
}

@Composable
private fun DetailValue(label:String,value:String){
    if(value.isNotBlank()){
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(label,color=Color.White.copy(alpha=0.6f),fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.labelMedium)
            Text(value,color=Color.White,style=MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun VendorRequestDetail(
    request:JSONObject,
    updating:Boolean,
    error:String,
    onClose:()->Unit,
    onChangeStatus:(String)->Unit
){
    val context=LocalContext.current
    val status=request.requestText("status").ifBlank{"new"}
    val phone=request.requestText("resident_phone")
    val location=request.requestLocation()
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF002B34),ClubhouseNavy,Color(0xFF290743))))){
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp),
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically){
            Text("Request #${request.requestText("id")}",
                style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            TextButton(onClick=onClose,enabled=!updating){Text("Done")}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal=17.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(18.dp)){
            Text(request.requestText("service").ifBlank{"Service Request"},
                style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
            StatusPill(status)
            DetailPanel("Resident"){
                Text(request.customerName(),style=MaterialTheme.typography.titleLarge,
                    fontWeight=FontWeight.Bold,color=Color.White)
                if(phone.isNotBlank()){
                    TextButton(onClick={
                        val digits=phone.filter(Char::isDigit)
                        if(digits.isNotEmpty()){
                            context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$digits")))
                        }
                    }){
                        Icon(Icons.Default.Phone,contentDescription=null)
                        Spacer(Modifier.width(8.dp))
                        Text(phone)
                    }
                }
                if(location.isNotBlank())Text(location,color=Color.White.copy(alpha=0.82f))
            }
            DetailPanel("Request"){
                DetailValue("Service / Keyword",request.requestText("service"))
                DetailValue("Sub-service / Keyword",request.requestText("sub_service"))
                DetailValue("Location",location)
                DetailValue("Original post date",request.requestPostLabel())
                DetailValue("Message",request.requestText("message"))
                DetailValue("Source",request.requestText("source"))
                DetailValue("Source lead ID",request.requestText("source_lead_id"))
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            if(updating)CircularProgressIndicator(color=ClubhouseCyan)
            when(status){
                "new","viewed"->{
                    Button(onClick={onChangeStatus("accepted")},enabled=!updating,
                        modifier=Modifier.fillMaxWidth().height(60.dp)){
                        Icon(Icons.Default.CheckCircle,contentDescription=null)
                        Spacer(Modifier.width(10.dp))
                        Text("Accept Request",fontWeight=FontWeight.Bold)
                    }
                    OutlinedButton(onClick={onChangeStatus("declined")},enabled=!updating,
                        modifier=Modifier.fillMaxWidth().height(60.dp)){
                        Icon(Icons.Default.Cancel,contentDescription=null)
                        Spacer(Modifier.width(10.dp))
                        Text("Decline Request",fontWeight=FontWeight.Bold)
                    }
                }
                "accepted"->{
                    Button(onClick={onChangeStatus("completed")},enabled=!updating,
                        modifier=Modifier.fillMaxWidth().height(56.dp)){Text("Mark Completed")}
                }
                else->Text("No further action is required.",
                    color=Color.White.copy(alpha=0.75f))
            }
            Spacer(Modifier.height(90.dp))
        }
    }
}
