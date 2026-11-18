package com.clubhouselinks.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val eventCyan=Color(0xFF08C4E4)
private val eventNight=Color(0xFF0C1230)
private val eventPurple=Color(0xFF5F167D)
private val eventBrush=Brush.linearGradient(listOf(Color(0xFF06384D),Color(0xFF251A40),eventPurple))

private fun JSONObject.eText(k:String)=optString(k).trim().takeUnless{it.isEmpty()||it=="null"} ?: ""
private fun JSONArray.eObjects()=(0 until length()).mapNotNull{optJSONObject(it)}
private fun eventTime(raw:String):ZonedDateTime?=try{Instant.parse(raw).atZone(ZoneId.systemDefault())}catch(_:Exception){
    try{OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault())}catch(_:Exception){
        try{LocalDateTime.parse(raw.take(19)).atZone(ZoneId.systemDefault())}catch(_:Exception){null}
    }
}
private fun JSONObject.eventInstant():ZonedDateTime?=eventTime(eText("starts_at"))

// Read the selected photo on a background dispatcher and downsample it before
// sending it to the existing HOA Board API (image_base64). Avoid uploading an
// unbounded full-resolution camera image through Express JSON.
private suspend fun eventPhotoBase64(context:android.content.Context,uri:Uri):String =
    withContext(Dispatchers.IO) {
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
            ?: throw IllegalArgumentException("Unable to open selected photo.")
        require(bounds.outWidth>0 && bounds.outHeight>0){"Invalid event photo."}
        var sample=1
        while(bounds.outWidth/sample>1600 || bounds.outHeight/sample>1600) sample*=2
        val options=BitmapFactory.Options().apply{inSampleSize=sample}
        val decoded=context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it,null,options)
        } ?: throw IllegalArgumentException("Unable to decode selected photo.")
        val maxSide=maxOf(decoded.width,decoded.height)
        val scaled=if(maxSide>1200) {
            val ratio=1200f/maxSide
            Bitmap.createScaledBitmap(decoded,(decoded.width*ratio).toInt().coerceAtLeast(1),
                (decoded.height*ratio).toInt().coerceAtLeast(1),true)
        }else decoded
        val bytes=ByteArrayOutputStream().use { out ->
            check(scaled.compress(Bitmap.CompressFormat.JPEG,78,out)){"Photo encoding failed."}
            out.toByteArray()
        }
        if(scaled!==decoded)scaled.recycle()
        decoded.recycle()
        require(bytes.size<=3_000_000){"Event photo is too large. Choose a smaller image."}
        Base64.encodeToString(bytes,Base64.NO_WRAP)
    }

@Composable
private fun EventPhoto(event:JSONObject) {
    val url=event.eText("image_url")
    if(url.startsWith("https://")) {
        AsyncImage(model=url,contentDescription="Photo for ${event.eText("title")}",
            modifier=Modifier.fillMaxWidth().height(205.dp)
                .clip(RoundedCornerShape(16.dp)),contentScale=ContentScale.Crop)
    }
}


@Composable
fun ResidentParityEvents(session:AccountSession,readOnly:Boolean=false){
    val scope=rememberCoroutineScope()
    var profile by remember(session.residentId){mutableStateOf<JSONObject?>(null)}
    var events by remember(session.residentId){mutableStateOf<List<JSONObject>>(emptyList())}
    var busy by remember(session.residentId){mutableStateOf(true)}
    var error by remember(session.residentId){mutableStateOf("")}
    var boardView by remember(session.residentId){mutableStateOf(true)}
    var showCreate by remember{mutableStateOf(false)}
    var month by remember{mutableStateOf(YearMonth.now())}
    var selectedDay by remember{mutableStateOf<LocalDate?>(null)}
    val isBoard=profile?.optBoolean("board_of_directors",false)==true
    val showBoardView=isBoard && boardView
    val canManage=showBoardView && !readOnly
    fun reload(){
        if(!session.isResident)return
        scope.launch{
            busy=true;error=""
            try{
                val p=ClubhouseApi.residentProfile(session.residentId)
                profile=p.optJSONObject("resident") ?: p
                val board=profile?.optBoolean("board_of_directors",false)==true
                val result=ClubhouseApi.residentEvents(session.residentId,board && boardView)
                events=(result.optJSONArray("events") ?: JSONArray()).eObjects()
            }catch(e:Exception){error=e.message ?: "Unable to load events"}
            finally{busy=false}
        }
    }
    LaunchedEffect(session.residentId,boardView,readOnly){reload()}
    val neighborhood=profile?.eText("neighborhood_name").orEmpty()
        .ifBlank{session.neighborhoodName}.ifBlank{"Your Neighborhood"}
    val today=LocalDate.now()
    val upcoming=events.filter{(it.eventInstant()?.toLocalDate() ?: today)>=today}
        .sortedBy{it.eventInstant()}
    val past=events.filter{(it.eventInstant()?.toLocalDate() ?: today)<today}
        .sortedByDescending{it.eventInstant()}
    if(showCreate && canManage){
        HoaEventPublishForm(
            residentId=session.residentId,
            onClose={showCreate=false},
            onPublished={showCreate=false;reload()}
        )
        return
    }
    LazyColumn(
        modifier=Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0xFF002D37),eventNight,Color(0xFF2F073C)))),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=20.dp,bottom=105.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)
    ){
        item{
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(if(showBoardView)"HOA Events" else "Community Events",
                    style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                Text(neighborhood,color=eventCyan,style=MaterialTheme.typography.titleLarge)
                if(readOnly) Text("Resident support preview · event publishing disabled",
                    color=eventCyan,style=MaterialTheme.typography.labelMedium)
                if(isBoard){
                    Button(onClick={boardView=!boardView},modifier=Modifier.fillMaxWidth()){
                        Text(if(boardView)"View as Resident" else "Return to HOA Board View")
                    }
                }
                if(showBoardView){
                    EventPanel {
                        Text("Neighborhood Announcements",
                            style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        Text("Publishing an event adds it to your neighborhood calendar and sends a push notification to registered residents in your community.")
                        Text("You can include a photo, description, date, time, and location.",
                            color=eventCyan,fontWeight=FontWeight.Bold)
                    }
                    Button(onClick={showCreate=true},enabled=canManage,
                        modifier=Modifier.fillMaxWidth().height(58.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=eventCyan,contentColor=Color.Black)){
                        Icon(Icons.Default.Event,contentDescription=null)
                        Spacer(Modifier.width(10.dp))
                        Text(if(readOnly)"Create New Event (read-only preview)" else "Create New Event",fontWeight=FontWeight.Bold)
                    }
                }
                if(busy)CircularProgressIndicator(color=eventCyan)
                if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
                OutlinedButton(onClick={reload()},enabled=!busy) {Text("Refresh events")}
            }
        }
        item{
            EventPanel {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.CalendarMonth,contentDescription=null,tint=Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Event Calendar",style=MaterialTheme.typography.titleLarge,
                        fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick={month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,contentDescription="Previous month")}
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.US)),
                        color=eventCyan,fontWeight=FontWeight.Bold,
                        style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={month=month.plusMonths(1)}){Icon(Icons.Default.ChevronRight,contentDescription="Next month")}
                }
                val weekdays=listOf("S","M","T","W","T","F","S")
                Row(Modifier.fillMaxWidth()){
                    weekdays.forEach{d->Text(d,Modifier.weight(1f),
                        textAlign=TextAlign.Center,color=Color.LightGray)}
                }
                val offset=month.atDay(1).dayOfWeek.value%7
                val total=month.lengthOfMonth()
                val dates=(0 until offset).map{0}+(1..total).toList()
                dates.chunked(7).forEach{week->
                    Row(Modifier.fillMaxWidth()){
                        (0..6).forEach{col->
                            val dateNum=week.getOrNull(col) ?: 0
                            if(dateNum==0){Spacer(Modifier.weight(1f).height(47.dp))}
                            else{
                                val date=month.atDay(dateNum)
                                val scheduled=events.any{it.eventInstant()?.toLocalDate()==date}
                                val current=selectedDay==date || (selectedDay==null && date==today)
                                Box(Modifier.weight(1f).height(47.dp),contentAlignment=Alignment.Center){
                                    OutlinedButton(
                                        onClick={selectedDay=if(selectedDay==date)null else date},
                                        contentPadding=PaddingValues(0.dp),
                                        modifier=Modifier.size(40.dp),
                                        border=if(current)BorderStroke(2.dp,eventCyan)else null
                                    ){
                                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                                            Text(dateNum.toString(),
                                                color=if(current)Color.White else MaterialTheme.colorScheme.onSurface)
                                            if(scheduled)Box(Modifier.size(4.dp).clip(RoundedCornerShape(3.dp))
                                                .background(Color.Yellow))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color.Yellow))
                    Spacer(Modifier.width(10.dp))
                    Text("Scheduled event",color=Color.LightGray)
                }
            }
        }
        item{Text(if(selectedDay!=null)"Events on ${selectedDay}" else "Current & Upcoming Events",
            style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        val shown=if(selectedDay!=null)events.filter{it.eventInstant()?.toLocalDate()==selectedDay}
            else upcoming
        if(shown.isEmpty())item{EventPanel{Text("There are no events scheduled for this selection.",
            color=Color.White.copy(alpha=0.8f))}}
        items(shown,key={"${it.eText("event_source")}-${it.optInt("id")}"}){event->
            EventPanel{
                EventPhoto(event)
                Text(event.eText("title").ifBlank{"Community Event"},
                    style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                event.eventInstant()?.let{Text(it.format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")),
                    color=eventCyan,fontWeight=FontWeight.Bold)}
                Text(event.eText("address"),color=Color.White.copy(alpha=0.8f))
                Text(event.eText("description"),color=Color.White.copy(alpha=0.9f))
            }
        }
        if(selectedDay==null && past.isNotEmpty()){
            item{Text("Past Events",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
            items(past,key={"${it.eText("event_source")}-${it.optInt("id")}-past"}){event->
                EventPanel{
                    EventPhoto(event)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(event.eText("title").ifBlank{"Community Event"},
                            fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge,
                            modifier=Modifier.weight(1f))
                        Text("Completed",color=Color.LightGray)
                    }
                    Text(event.eText("address"),color=Color.White.copy(alpha=0.8f))
                    Text(event.eventInstant()?.format(
                        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · h:mm a")).orEmpty(),
                        color=eventCyan)
                    if(event.eText("description").isNotBlank())
                        Text(event.eText("description"),color=Color.White.copy(alpha=0.85f))
                }
            }
        }
    }
}

@Composable
private fun EventPanel(content:@Composable ColumnScope.()->Unit){
    Card(modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(26.dp),
        border=BorderStroke(1.dp,eventCyan.copy(alpha=0.5f)),
        colors=CardDefaults.cardColors(containerColor=Color(0xFF201433))){
        Column(Modifier.fillMaxWidth().background(eventBrush).padding(19.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

@Composable
private fun HoaEventPublishForm(
    residentId:Int,onClose:()->Unit,onPublished:()->Unit
){
    var title by remember{mutableStateOf("")}
    var description by remember{mutableStateOf("")}
    var address by remember{mutableStateOf("")}
    var date by remember{mutableStateOf(LocalDate.now().plusDays(1).toString())}
    var startTime by remember{mutableStateOf("10:00")}
    var endTime by remember{mutableStateOf("12:00")}
    var saving by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var selectedPhoto by remember { mutableStateOf<Uri?>(null) }
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedPhoto=uri
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .background(Brush.verticalGradient(listOf(Color(0xFF003341),Color(0xFF281039))))
        .padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Create New Event",modifier=Modifier.weight(1f),
                style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            TextButton(onClick=onClose){Text("Cancel")}
        }
        Text("HOA board only · Publishes to your neighborhood calendar",color=eventCyan)
        OutlinedTextField(title,{title=it},label={Text("Event title")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(description,{description=it},label={Text("Description")},minLines=3,
            modifier=Modifier.fillMaxWidth())
        OutlinedTextField(address,{address=it},label={Text("Event location / address")},
            modifier=Modifier.fillMaxWidth())
        OutlinedTextField(date,{date=it},label={Text("Date YYYY-MM-DD")},
            modifier=Modifier.fillMaxWidth())
        OutlinedTextField(startTime,{startTime=it},label={Text("Start 24-hour HH:MM")},
            modifier=Modifier.fillMaxWidth())
        OutlinedTextField(endTime,{endTime=it},label={Text("End 24-hour HH:MM")},
            modifier=Modifier.fillMaxWidth())
        Text("Event photo (optional)",style=MaterialTheme.typography.titleMedium,
            color=Color.White,fontWeight=FontWeight.Bold)
        selectedPhoto?.let { uri ->
            AsyncImage(model=uri,contentDescription="Selected event photo",
                modifier=Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(16.dp)),
                contentScale=ContentScale.Crop)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            OutlinedButton(onClick={photoPicker.launch("image/*")},enabled=!saving) {
                Icon(Icons.Default.AddPhotoAlternate,contentDescription=null)
                Spacer(Modifier.width(6.dp))
                Text(if(selectedPhoto==null)"Choose Event Photo" else "Change Photo")
            }
            if(selectedPhoto!=null)TextButton(onClick={selectedPhoto=null},enabled=!saving) {
                Text("Remove")
            }
        }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        Button(
            modifier=Modifier.fillMaxWidth(),enabled=!saving && title.isNotBlank(),
            onClick={
                scope.launch {
                    saving=true;error=""
                    try {
                        val day=LocalDate.parse(date)
                        val start=LocalTime.parse(startTime)
                        val end=LocalTime.parse(endTime)
                        val zone=ZoneId.systemDefault()
                        val starts=ZonedDateTime.of(day,start,zone)
                        val ends=ZonedDateTime.of(day,end,zone)
                        require(ends.isAfter(starts)){"End time must be after start time."}
                        val body=JSONObject()
                            .put("title",title.trim())
                            .put("description",description.trim())
                            .put("address",address.trim())
                            .put("starts_at",starts.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                            .put("ends_at",ends.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                            .put("publish",true)
                        selectedPhoto?.let { body.put("image_base64",eventPhotoBase64(context,it)) }
                        ClubhouseApi.createBoardEvent(residentId,body)
                        onPublished()
                    }catch(e:Exception){error=e.message ?: "Unable to publish event"}
                    finally{saving=false}
                }
            }
        ){Text(if(saving)"Publishing…" else "Publish Event")}
    }
}
