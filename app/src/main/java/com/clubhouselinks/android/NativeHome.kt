package com.clubhouselinks.android

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import kotlinx.coroutines.delay
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

/**
 * Faithful Android counterpart of iOS HomeView.homeContent and
 * HomeIntroImageView. Both assets are checked into drawable-nodpi.
 */
@Composable
private fun GuestHomeIntroImage() {
    var showClubhouse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_500)
        showClubhouse = true
    }
    Crossfade(
        targetState = showClubhouse,
        animationSpec = tween(800),
        label = "clubhouse-intro-image",
        modifier = Modifier.fillMaxWidth().height(250.dp)
    ) { showingClubhouse ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (showingClubhouse) {
                Image(
                    painter = painterResource(R.drawable.hoa),
                    contentDescription = "Neon Clubhouse Links community clubhouse",
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            BorderStroke(1.5.dp, Brush.linearGradient(
                                listOf(Color(0xFF00D5EA), Color(0xFFFF9A24), Color(0xFFD235DE))
                            )),
                            RoundedCornerShape(24.dp)
                        ),
                    contentScale = ContentScale.Fit
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.clubhouse_app_icon),
                    contentDescription = "Clubhouse Links logo",
                    modifier = Modifier.size(190.dp)
                        .shadow(18.dp, RoundedCornerShape(38.dp), ambientColor = ClubhouseCyan)
                        .clip(RoundedCornerShape(38.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun GuestNeonCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, ClubhouseCyan.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF12293C), Color(0xFF231C38), Color(0xFF32163D))
                )
            )
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = Color.White,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(description, color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NativeGuestHome(navigate: (String) -> Unit) {
    NativeHomeColumn {
        GuestHomeIntroImage()

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Clubhouse Links", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = Color.White)
            Text("Home Services", color = ClubhouseCyan,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Your Local Home Service Referral Network",
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium)
        }

        // iOS HomeView's Create Account button uses a purple → orange gradient.
        Box(
            modifier = Modifier.fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(18.dp),
                    ambientColor = ClubhouseCyan.copy(alpha = 0.50f))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFB82BD3), Color(0xFFFF8736))
                    )
                )
                .clickable { navigate("Account") }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Create Account", color = Color.White,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        GuestNeonCard(
            "See Completed Projects By Neighbors",
            "Choose your next home project or repair specialist by seeing who your neighbors have used"
        ) { navigate("Account") }
        GuestNeonCard(
            "Submit Vendor Requests",
            "Send maintenance requests, report issues, or contact reputable local vendors."
        ) { navigate("Account") }
        GuestNeonCard(
            "View Vendors",
            "Browse trusted local contractors, home service providers, and HOA or neighborhood-reviewed businesses."
        ) { navigate("Services") }
        GuestNeonCard(
            "Upcoming Events",
            "See social events, meetings, holiday celebrations, and other activities."
        ) { navigate("Events") }
        Spacer(Modifier.height(90.dp))
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
            PortedAction("Test New Resident Signup",
                "Open the iOS-style signup wizard without replacing the Aspen vendor login.",
                {navigate("Support Signup")})
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
