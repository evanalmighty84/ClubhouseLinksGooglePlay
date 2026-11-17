package com.clubhouselinks.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val homeGlow = Color(0xFF13C2E0)
private val homeNight = Color(0xFF081B2A)
private val homePurple = Color(0xFF501B67)
private val homeBrush = Brush.linearGradient(listOf(Color(0xFF083D4D), Color(0xFF1D1E40), homePurple))

private fun JSONObject.field(name:String):String =
    optString(name).trim().takeUnless {it.isBlank() || it=="null"} ?: ""

private fun JSONArray.objects():List<JSONObject> =
    (0 until length()).mapNotNull{optJSONObject(it)}

private data class NeighborWork(
    val key:String, val vendor:String, val service:String,
    val picture:String, val resident:String
)

private fun vendorProjects(vendors:List<JSONObject>):List<NeighborWork> =
    vendors.flatMap { vendor ->
        val people=vendor.optJSONArray("signed_up_people") ?: JSONArray()
        people.objects().mapNotNull { person ->
            val url=person.field("finished_photo_url")
            val approval=person.field("photo_approval_status")
            if(url.isBlank() || (approval.isNotBlank() && approval!="approved")) null
            else NeighborWork(
                key="${vendor.optInt("id")}-${person.optInt("id")}",
                vendor=vendor.field("company_name"),
                service=vendor.field("category").ifBlank{"Home Service"},
                picture=url, resident=person.field("first_name")
            )
        }
    }

@Composable
private fun OutlinedNeonPanel(
    modifier:Modifier=Modifier,
    content:@Composable ColumnScope.()->Unit
) {
    Card(
        modifier=modifier.fillMaxWidth(),
        shape=RoundedCornerShape(25.dp),
        colors=CardDefaults.cardColors(containerColor=Color(0xFF211A42)),
        border=BorderStroke(1.dp,homeGlow.copy(alpha=0.65f))
    ){
        Column(Modifier.background(homeBrush).padding(18.dp),
            verticalArrangement=Arrangement.spacedBy(15.dp),content=content)
    }
}

@Composable
private fun BirdArtwork() {
    val context=LocalContext.current
    val frames=remember {
        (0..7).mapNotNull { n ->
            val key="clubhouse_bird_flying_${n.toString().padStart(2,'0')}"
            context.resources.getIdentifier(key,"drawable",context.packageName).takeIf{it!=0}
        }
    }
    var frame by remember{mutableIntStateOf(0)}
    LaunchedEffect(frames.size) {
        if(frames.size>1)while(true){
            delay(190)
            frame=(frame+1)%frames.size
        }
    }
    if(frames.isNotEmpty()){
        Image(
            painter=painterResource(frames[frame.coerceIn(frames.indices)]),
            contentDescription="Clubhouse Links bird flying",
            modifier=Modifier.fillMaxWidth().height(215.dp),
            contentScale=ContentScale.Fit
        )
    }else {
        Icon(Icons.Default.Home,contentDescription="Neighborhood",
            tint=homeGlow,modifier=Modifier.fillMaxWidth().height(110.dp))
    }
}

@Composable
fun ResidentParityHome(
    session:AccountSession,
    navigate:(String)->Unit,
    readOnly:Boolean=false
) {
    var profile by remember(session.residentId){mutableStateOf<JSONObject?>(null)}
    var events by remember(session.residentId){mutableStateOf<List<JSONObject>>(emptyList())}
    var announcements by remember(session.residentId){mutableStateOf<List<JSONObject>>(emptyList())}
    var vendors by remember(session.residentId){mutableStateOf<List<JSONObject>>(emptyList())}
    var busy by remember(session.residentId){mutableStateOf(true)}
    var error by remember(session.residentId){mutableStateOf("")}
    val scope=rememberCoroutineScope()
    fun load(){
        if(!session.isResident)return
        scope.launch {
            busy=true; error=""
            try {
                val p=ClubhouseApi.residentProfile(session.residentId)
                profile=p.optJSONObject("resident") ?: p
                // Identical APIs as iOS's NeighborhoodUpdatesCarousel and project filters.
                val eventResponse=runCatching{ClubhouseApi.residentEvents(session.residentId)}.getOrNull()
                events=(eventResponse?.optJSONArray("events") ?: JSONArray()).objects()
                val announcementResponse=runCatching{ClubhouseApi.residentAnnouncements(session.residentId)}.getOrNull()
                announcements=(announcementResponse?.optJSONArray("announcements") ?: JSONArray()).objects()
                val directory=ClubhouseApi.residentVendors(session.residentId)
                vendors=(directory.optJSONArray("vendors") ?: JSONArray()).objects()
            }catch(e:Exception){error=e.message ?: "Unable to load neighborhood data"}
            finally{busy=false}
        }
    }
    LaunchedEffect(session.residentId){load()}
    val community=profile?.field("neighborhood_name").orEmpty().ifBlank{session.neighborhoodName}
        .ifBlank{profile?.field("display_area_name").orEmpty()}.ifBlank{"Your Neighborhood"}
    val name=listOf(profile?.field("first_name").orEmpty(),
        profile?.field("last_name").orEmpty()).filter(String::isNotBlank).joinToString(" ")
        .ifBlank{session.displayName}
    val works=remember(vendors){vendorProjects(vendors)}
    var service by remember(session.residentId){mutableStateOf("All Services")}
    var vendor by remember(session.residentId){mutableStateOf("All Vendors")}
    val serviceOptions=remember(works){listOf("All Services")+works.map{it.service}.distinct().sorted()}
    val vendorOptions=remember(works){listOf("All Vendors")+works.map{it.vendor}.distinct().sorted()}
    val filtered=works.filter{
        (service=="All Services"||it.service==service) && (vendor=="All Vendors"||it.vendor==vendor)
    }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0xFF002D37),homeNight,Color(0xFF30083E))))
            .verticalScroll(rememberScrollState()).padding(17.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)
    ){
        Text(name.ifBlank{"Your Home"},style=MaterialTheme.typography.headlineLarge,
            fontWeight=FontWeight.Black,color=Color.White,modifier=Modifier.fillMaxWidth(),
            textAlign=TextAlign.Center)
        if(busy)CircularProgressIndicator(color=homeGlow)
        if(error.isNotBlank()) {
            Text(error,color=MaterialTheme.colorScheme.error)
            OutlinedButton(onClick={load}) {Text("Retry loading")}
        }
        OutlinedNeonPanel {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){
                    Text("📣  NEIGHBORHOOD UPDATES",color=homeGlow,
                        fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelLarge)
                    Text(community,style=MaterialTheme.typography.titleLarge,
                        fontWeight=FontWeight.Bold,color=Color.White)
                }
                if(!readOnly){
                    FilledTonalButton(onClick={navigate("Account")}) {Text("Account")}
                }
            }
            val updateItems=remember(events,announcements){
                announcements.map{Triple(it.field("title"),it.field("body"),it.field("image_url"))}+
                    events.map{Triple(it.field("title"),it.field("description"),it.field("image_url"))}
            }
            val pages=1+updateItems.size
            val pager=rememberPagerState(pageCount={pages})
            HorizontalPager(state=pager,modifier=Modifier.fillMaxWidth()) { index ->
                if(index==0) {
                    Box(Modifier.fillMaxWidth().height(225.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF003F58),Color(0xFF221238)))),
                        contentAlignment=Alignment.Center){BirdArtwork()}
                }else{
                    val item=updateItems[index-1]
                    Column(Modifier.fillMaxWidth().height(225.dp),
                        verticalArrangement=Arrangement.spacedBy(10.dp)){
                        if(item.third.startsWith("https://")){
                            AsyncImage(model=item.third,contentDescription=item.first,
                                modifier=Modifier.fillMaxWidth().height(120.dp)
                                    .clip(RoundedCornerShape(14.dp)),contentScale=ContentScale.Crop)
                        }
                        Text(item.first,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text(item.second,style=MaterialTheme.typography.bodySmall,maxLines=4)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){
                repeat(pages.coerceAtMost(12)) {i ->
                    Box(Modifier.padding(4.dp).size(if(i==pager.currentPage)9.dp else 7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if(i==pager.currentPage)Color.White else Color.LightGray.copy(alpha=0.6f)))
                }
            }
            if(updateItems.isEmpty()) {
                Text("Events and HOA announcements will appear here.",
                    color=Color.White.copy(alpha=0.8f))
            }
        }

        Text("Clubhouse Links is your portal to everyday home service contractors who have been used and trusted by your neighbors.",
            modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,fontWeight=FontWeight.SemiBold,
            color=Color.White.copy(alpha=0.88f))

        OutlinedNeonPanel {
            Text("$community Projects",style=MaterialTheme.typography.headlineMedium,
                fontWeight=FontWeight.Bold)
            Text("See trusted home service contractors used by neighbors near you in $community.",
                color=Color.White.copy(alpha=0.78f))
            Text("Service",color=homeGlow,fontWeight=FontWeight.Bold)
            ParitySelector(service,serviceOptions){service=it}
            Text("Vendor",color=homeGlow,fontWeight=FontWeight.Bold)
            ParitySelector(vendor,vendorOptions){vendor=it}
            if(filtered.isEmpty()){
                val context=LocalContext.current
                val image=remember{
                    context.resources.getIdentifier(
                        "neighborhood_projects_placeholder","drawable",context.packageName)
                }
                if(image!=0) Image(painterResource(image),
                    contentDescription="Neighborhood project placeholder",
                    modifier=Modifier.fillMaxWidth().height(185.dp)
                        .clip(RoundedCornerShape(17.dp)),contentScale=ContentScale.Crop)
                Text("This is where you will see your neighbors' projects when they have work done.",
                    modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,
                    color=Color.White.copy(alpha=0.8f))
            }else{
                val pager=rememberPagerState(pageCount={filtered.size})
                HorizontalPager(state=pager,modifier=Modifier.fillMaxWidth()) {i->
                    val work=filtered[i]
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                        AsyncImage(model=work.picture,contentDescription=work.service,
                            modifier=Modifier.fillMaxWidth().height(180.dp)
                                .clip(RoundedCornerShape(17.dp)),contentScale=ContentScale.Crop)
                        Text(work.service,style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.Bold)
                        Text(work.vendor,color=homeGlow)
                    }
                }
                Text("${pager.currentPage+1} / ${filtered.size}",
                    modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
            }
        }
        OutlinedNeonPanel {
            Text("Start a Project",style=MaterialTheme.typography.headlineMedium,
                fontWeight=FontWeight.Bold)
            BirdArtwork()
            Text("Share your completed project with neighbors or find someone to help with your next one.")
            Button(onClick={navigate("Submit Job")},enabled=!readOnly,
                modifier=Modifier.fillMaxWidth()){
                Text(if(readOnly)"Read-only support view" else "Start a Project")
            }
        }
        Button(onClick={navigate("Services")},modifier=Modifier.fillMaxWidth()){
            Icon(Icons.Default.Store,contentDescription=null)
            Spacer(Modifier.width(8.dp))
            Text("View Vendor Directory")
        }
        OutlinedNeonPanel {
            Text("Submit Vendor Request",style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.Bold)
            Text("Request a contractor, service provider, or local business recommendation.")
            OutlinedButton(onClick={navigate("Services")},modifier=Modifier.fillMaxWidth()){
                Text(if(readOnly)"Browse vendors (read only)" else "Request a Service")
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun ParitySelector(value:String,options:List<String>,onSelect:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Box {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){
            Text(value,modifier=Modifier.weight(1f),textAlign=TextAlign.Start)
            Icon(Icons.Default.ExpandMore,contentDescription=null)
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
            options.forEach{option->
                DropdownMenuItem(text={Text(option)},onClick={
                    expanded=false
                    onSelect(option)
                })
            }
        }
    }
}
