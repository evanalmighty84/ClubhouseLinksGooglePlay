package com.clubhouselinks.android

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val DeepPurple=Color(0xFF260B47)
private val GradientPurple=Color(0xFF7927AB)
private val OverlayColor=Color(0xFF10102F)

private fun JSONObject.nonNullString(field:String):String =
    optString(field).trim().takeIf { it.isNotBlank() && it != "null" } ?: ""

private data class CustomerProjects(
    val key:String,
    val name:String,
    val phone:String,
    val address:String,
    val area:String,
    val projects:List<JSONObject>
)

private fun groupCustomerProjects(projects:List<JSONObject>):List<CustomerProjects> {
    val groups=linkedMapOf<String,MutableList<JSONObject>>()
    projects.forEach { p ->
        val id=p.optInt("resident_id")
        val key=if(id>0)"resident:$id" else "project:${p.optInt("id")}"
        groups.getOrPut(key){mutableListOf()}.add(p)
    }
    return groups.map { (key,entries) ->
        val first=entries.first()
        val name=listOf(first.nonNullString("resident_first_name"),
            first.nonNullString("resident_last_name"))
            .filter(String::isNotBlank).joinToString(" ").ifBlank{"Customer"}
        CustomerProjects(
            key=key,name=name,phone=first.nonNullString("resident_phone"),
            address=first.nonNullString("resident_address"),
            area=first.nonNullString("resident_display_area_name"),projects=entries
        )
    }
}

@Composable
fun JobsSubmittedScreen(session: AccountSession, navigate: (String) -> Unit) {
    if (!session.isVendor) {
        SubmitJobScreen(session, navigate)
        return
    }
    var projects by remember(session.vendorId){ mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember(session.vendorId){ mutableStateOf(false) }
    var error by remember(session.vendorId){ mutableStateOf("") }
    val scope=rememberCoroutineScope()
    fun refresh() {
        if(loading)return
        scope.launch {
            loading=true;error=""
            try {
                val response=ClubhouseApi.vendorCompletedProjects(session.vendorId)
                val arr=response.optJSONArray("projects") ?: JSONArray()
                projects=(0 until arr.length()).mapNotNull{arr.optJSONObject(it)}
            }catch(e:Exception){error=e.message ?: "Unable to load completed projects"}
            finally{loading=false}
        }
    }
    LaunchedEffect(session.vendorId){refresh()}
    val customers=remember(projects){groupCustomerProjects(projects)}
    LazyColumn(
        modifier=Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF002B34),ClubhouseNavy,DeepPurple))),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=20.dp,bottom=120.dp),
        verticalArrangement=Arrangement.spacedBy(28.dp)
    ){
        item {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Completed Projects",style=MaterialTheme.typography.headlineLarge,
                    color=Color.White,fontWeight=FontWeight.Bold)
                Text(session.displayName.uppercase(),color=ClubhouseCyan,fontWeight=FontWeight.Bold)
                Text("Homes and completed projects shared by customers who used your company.",
                    color=Color.White.copy(alpha=0.75f))
                OutlinedButton(onClick={refresh()},enabled=!loading){Text("Refresh projects")}
                if(loading)CircularProgressIndicator(color=ClubhouseCyan)
                if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                if(!loading && error.isBlank() && customers.isEmpty())
                    Text("No customer projects yet.",color=Color.White)
            }
        }
        items(customers,key={it.key}){customer ->
            CustomerProjectsSection(customer)
        }
    }
}

@Composable
private fun CustomerProjectsSection(customer:CustomerProjects) {
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        CustomerHomeCard(customer)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically) {
            Text("${customer.projects.size} Completed Project${if(customer.projects.size==1)"" else "s"}",
                style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=Color.White)
            if(customer.projects.size>1)Text("Swipe  ↔",color=ClubhouseCyan,fontWeight=FontWeight.Bold)
        }
        val pagerState=rememberPagerState(pageCount={customer.projects.size})
        HorizontalPager(state=pagerState,modifier=Modifier.fillMaxWidth()) { index ->
            CompletedProjectPhotoCard(customer.projects[index])
        }
        if(customer.projects.size>1){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,
                verticalAlignment=Alignment.CenterVertically) {
                repeat(customer.projects.size) { index ->
                    Box(Modifier.padding(horizontal=4.dp).size(if(index==pagerState.currentPage)9.dp else 7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if(index==pagerState.currentPage)Color.White else Color.White.copy(alpha=0.35f)))
                }
            }
        }
    }
}

@Composable
private fun CustomerHomeCard(customer:CustomerProjects){
    val context=LocalContext.current
    val placeholder=remember {
        context.resources.getIdentifier("neighborhood_projects_placeholder","drawable",context.packageName)
    }
    Box(Modifier.fillMaxWidth().height(245.dp).clip(RoundedCornerShape(25.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF073A51),DeepPurple)))){
        if(placeholder!=0){
            androidx.compose.foundation.Image(
                painter=painterResource(placeholder),
                contentDescription="Generic community home artwork, not the customer's address",
                modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent,Color.Black.copy(alpha=0.25f),Color.Black.copy(alpha=0.94f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp),
            verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Home,contentDescription=null,tint=ClubhouseCyan)
                Spacer(Modifier.width(8.dp))
                Text("Customer Home",color=ClubhouseCyan,fontWeight=FontWeight.Bold)
            }
            Text(customer.name,style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold,color=Color.White)
            if(customer.phone.isNotBlank()){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.Phone,contentDescription=null,tint=Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(customer.phone,color=Color.White)
                }
            }
            val location=customer.address.ifBlank{customer.area}
            if(location.isNotBlank()){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.LocationOn,contentDescription=null,tint=Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(location,color=Color.White)
                }
            }
        }
    }
}

@Composable
private fun CompletedProjectPhotoCard(project:JSONObject) {
    Card(modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(28.dp),
        border=BorderStroke(1.dp,ClubhouseCyan.copy(alpha=0.46f)),
        colors=CardDefaults.cardColors(containerColor=DeepPurple)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
            Color(0xFF06263A),DeepPurple,GradientPurple.copy(alpha=0.56f))))
            .padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().height(255.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(OverlayColor),contentAlignment=Alignment.Center) {
                val imageUrl=project.nonNullString("image_url")
                if(imageUrl.startsWith("https://") || imageUrl.startsWith("http://")) {
                    val context=LocalContext.current
                    val request=remember(imageUrl) {
                        ImageRequest.Builder(context).data(imageUrl).crossfade(true).build()
                    }
                    var phase by remember(imageUrl){mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)}
                    AsyncImage(
                        model=request,
                        contentDescription=project.nonNullString("service").ifBlank{"Completed project photograph"},
                        modifier=Modifier.fillMaxSize().padding(5.dp),
                        contentScale=ContentScale.Fit,
                        onState={phase=it}
                    )
                    when(phase){
                        is AsyncImagePainter.State.Loading,AsyncImagePainter.State.Empty ->
                            CircularProgressIndicator(color=ClubhouseCyan)
                        is AsyncImagePainter.State.Error ->
                            PhotoUnavailable("Image could not be loaded")
                        else -> Unit
                    }
                } else PhotoUnavailable("No project photo")
            }
            Text(project.nonNullString("service").ifBlank{"Completed Project"},
                style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,
                color=Color.White)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.Verified,contentDescription=null,tint=ClubhouseCyan)
                    Spacer(Modifier.width(7.dp))
                    Text(project.nonNullString("approval_status").ifBlank{
                        project.nonNullString("moderation_status").ifBlank{"Submitted"}
                    }.replaceFirstChar{it.uppercase()},color=ClubhouseCyan,
                        fontWeight=FontWeight.Bold)
                }
                val date=project.nonNullString("photo_submitted_at")
                if(date.isNotBlank())Text(displayProjectDate(date),color=Color.White.copy(alpha=0.7f),
                    style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PhotoUnavailable(label:String) {
    Column(horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(8.dp)){
        Icon(Icons.Default.Image,contentDescription=null,tint=ClubhouseCyan,
            modifier=Modifier.size(46.dp))
        Text(label,color=Color.White.copy(alpha=0.7f),textAlign=TextAlign.Center)
    }
}

private fun displayProjectDate(value:String):String {
    val formatters=listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX","yyyy-MM-dd HH:mm:ss.SSSSSS",
        "yyyy-MM-dd HH:mm:ss")
    for(pattern in formatters){
        try{
            val parser=SimpleDateFormat(pattern,Locale.US)
            parser.timeZone=TimeZone.getTimeZone("UTC")
            val parsed=parser.parse(value) ?: continue
            return SimpleDateFormat("MMM d, yyyy",Locale.US).format(parsed)
        }catch(_:Exception){}
    }
    return value.take(10)
}

/** iOS resident submission with category, vendor and completed-photo upload. */
@Composable
fun SubmitJobScreen(session: AccountSession, navigate: (String) -> Unit,
                    readOnly: Boolean = false) {
    ResidentProjectSubmit(session, navigate, readOnly=readOnly)
}
