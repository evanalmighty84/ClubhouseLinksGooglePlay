package com.clubhouselinks.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

// Native Android counterparts to iOS HomeView's role-specific destinations.
// Profile data is loaded from the existing Heroku backend, not from QA mock data.
// Server-enforced session authorization is still required before production.
@Composable
fun NativeHome(session: AccountSession, navigate: (String) -> Unit) {
    when {
        session.isVendor -> NativeVendorHome(session, navigate)
        session.isResident -> NativeResidentHome(session, navigate)
        else -> NativeGuestHome(navigate)
    }
}

@Composable
private fun NativeHomeColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF002B34), ClubhouseNavy, Color(0xFF210A3D))))
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp), content = content
    )
}

@Composable
private fun PortedArtwork(name: String, label: String) {
    val context = LocalContext.current
    val id = remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
    if (id != 0) {
        Image(painter = painterResource(id), contentDescription = label,
            modifier = Modifier.fillMaxWidth().height(195.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun PortedAction(title: String, summary: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor=Color(0xFF231C37))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
            Text(summary)
            Text("Open",color=ClubhouseCyan,style=MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NativeGuestHome(navigate: (String) -> Unit) {
    NativeHomeColumn {
        PortedArtwork("clubhouse_app_icon", "Clubhouse Links mascot")
        Text("Clubhouse Links", style=MaterialTheme.typography.headlineLarge,
            fontWeight=FontWeight.Bold, textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        Text("Home Services", color=ClubhouseCyan, style=MaterialTheme.typography.titleLarge,
            textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        Text("Your Local Home Service Referral Network",textAlign=TextAlign.Center,
            modifier=Modifier.fillMaxWidth())
        Button(onClick={navigate("Account")},modifier=Modifier.fillMaxWidth()){Text("Create Account")}
        PortedAction("See Completed Projects By Neighbors",
            "Choose a home project or repair specialist by seeing who your neighbors have used.",
            {navigate("Account")})
        PortedAction("Submit Vendor Requests",
            "Send maintenance requests or contact reputable local vendors.",
            {navigate("Account")})
        PortedAction("View Vendors","Browse local home-service providers.",{navigate("Services")})
        PortedAction("Upcoming Events","See neighborhood events, gatherings and meetings.",{navigate("Events")})
    }
}

@Composable
private fun NativeVendorHome(session: AccountSession, navigate: (String) -> Unit) {
    var vendor by remember(session.vendorId) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(session.vendorId) { mutableStateOf(true) }
    var error by remember(session.vendorId) { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    fun refresh(){
        scope.launch {
            loading=true; error=""
            try {
                val response=ClubhouseApi.vendorProfile(session.vendorId)
                vendor=response.optJSONObject("vendor") ?: response
            }catch(e:Exception){error=e.message ?: "Unable to load vendor profile"}
            finally{loading=false}
        }
    }
    LaunchedEffect(session.vendorId){refresh()}
    NativeHomeColumn {
        TextButton(onClick={navigate("Account")},modifier=Modifier.align(Alignment.End)){Text("Account Settings")}
        val business=vendor?.optString("company_name").orEmpty().ifBlank{session.displayName}
        Text(business.ifBlank{"Vendor Account"}.uppercase(),
            style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black,
            textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        val category=vendor?.optString("category").orEmpty()
        if(category.isNotBlank())Text(category,color=ClubhouseCyan,
            style=MaterialTheme.typography.titleLarge,textAlign=TextAlign.Center,
            modifier=Modifier.fillMaxWidth())
        if(loading)CircularProgressIndicator()
        if(error.isNotBlank()){
            Text(error,color=MaterialTheme.colorScheme.error)
            OutlinedButton(onClick={refresh()}){Text("Retry")}
        }
        if(BuildConfig.DEBUG) {
            PortedAction("Switch Resident",
                "Search for a real resident by name, phone, address or ID. Requires an authorized support device.",
                {navigate("Support Search")})
            PortedAction("QA Screen Preview",
                "Sample role views only — never switches real accounts.",{navigate("QA Preview")})
        }
        PortedArtwork("clubhouse_app_icon","Clubhouse Links")
        PortedAction("Jobs Submitted","View vendor project submissions.",{navigate("Jobs Submitted")})
        PortedAction("Service Requests","Open requests assigned to this vendor.",{navigate("Service Requests")})
        PortedAction("Neighborhood Street Fair","View neighborhood activities.",{navigate("Street Fair")})
    }
}

@Composable
private fun NativeResidentHome(session: AccountSession, navigate: (String) -> Unit) {
    var resident by remember(session.residentId) { mutableStateOf<JSONObject?>(null) }
    var mode by remember(session.residentId) { mutableStateOf("standard") }
    var loading by remember(session.residentId) { mutableStateOf(true) }
    var error by remember(session.residentId) { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    fun refresh(){
        scope.launch{
            loading=true;error=""
            try{
                val profile=ClubhouseApi.residentProfile(session.residentId)
                resident=profile.optJSONObject("resident") ?: profile
                val home=ClubhouseApi.residentHomeMode(session.residentId)
                mode=home.optString("resident_home_mode","standard")
            }catch(e:Exception){error=e.message ?: "Unable to load resident profile"}
            finally{loading=false}
        }
    }
    LaunchedEffect(session.residentId){refresh()}
    NativeHomeColumn {
        if(loading)CircularProgressIndicator()
        if(error.isNotBlank()){
            Text(error,color=MaterialTheme.colorScheme.error)
            OutlinedButton(onClick={refresh()}){Text("Retry")}
        }
        val community=resident?.optString("neighborhood_name").orEmpty().ifBlank{session.neighborhoodName}
        Text(community.ifBlank{"Your neighborhood"},style=MaterialTheme.typography.headlineLarge,
            fontWeight=FontWeight.Bold)
        val first=resident?.optString("first_name").orEmpty()
        Text(if(first.isBlank())"Welcome" else "Welcome, "+first,
            style=MaterialTheme.typography.titleLarge,color=ClubhouseCyan)
        if(mode=="street_fair"){
            PortedArtwork("foodtruck","Neighborhood food truck")
            PortedAction("Neighborhood Street Fair",
                "See events, food trucks and vendors in your neighborhood.",{navigate("Street Fair")})
        }else{
            PortedArtwork("clubhouse_links_home_fallback","Community")
            PortedAction("Completed Projects By Neighbors",
                "See home improvement projects completed nearby.",{navigate("Account")})
            PortedAction("Find a Local Provider",
                "Browse businesses serving your neighborhood.",{navigate("Services")})
            PortedAction("Submit Job","Share a completed home project.",{navigate("Submit Job")})
        }
        PortedAction("Upcoming Events","See neighborhood events.",{navigate("Events")})
        PortedAction("Request Service","View or submit a service request.",{navigate("Request Service")})
    }
}
