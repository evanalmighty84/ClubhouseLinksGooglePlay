package com.clubhouselinks.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// QA-only local screen samples. NO API calls, persistence, or production-account switching.
private val PreviewPurple = Color(0xFFC22BE0)
private val PreviewTeal = Color(0xFF13B7DC)
private val PreviewOrange = Color(0xFFFF8735)
private val PreviewViolet = Color(0xFF6726B4)

@Composable
fun QaPreviewScreen(onClose: () -> Unit) {
    var mode by remember { mutableStateOf("Vendor") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(Brush.verticalGradient(listOf(Color(0xFF002D37), ClubhouseNavy, Color(0xFF21083B))))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("QA SCREEN PREVIEW", color = PreviewTeal, fontWeight = FontWeight.Bold)
                Text("Sample data · no live account changes", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onClose) { Text("Exit preview") }
        }
        Text("View Clubhouse Links as", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Vendor", "Resident").forEach { item ->
                FilterChip(selected=mode==item, onClick={mode=item}, label={Text(item)})
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("HOA Board", "New Signup").forEach { item ->
                FilterChip(selected=mode==item, onClick={mode=item}, label={Text(item)})
            }
        }
        when (mode) {
            "Vendor" -> VendorModeSample()
            "Resident" -> ResidentModeSample()
            "HOA Board" -> BoardModeSample()
            else -> SignupModeSample()
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        Text(
            "This is a visual-only QA preview. Resident and HOA profiles below are examples, not database accounts. " +
                "To compare real device layouts, choose another Android model or screen size in Codemagic App Preview.",
            style=MaterialTheme.typography.bodySmall,
            color=Color.White.copy(alpha=0.75f)
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SampleActionCard(
    title: String, subtitle: String, start: Color, end: Color,
    icon: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(
            Brush.horizontalGradient(listOf(start,end)),
            RoundedCornerShape(20.dp)
        ).padding(horizontal=18.dp,vertical=21.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(15.dp)
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight=FontWeight.Bold, color=Color.White, style=MaterialTheme.typography.titleMedium)
            Text(subtitle, color=Color.White.copy(alpha=0.89f), style=MaterialTheme.typography.bodyMedium)
        }
        Icon(Icons.Default.ChevronRight,contentDescription=null,tint=Color.White)
    }
}

@Composable
private fun VendorModeSample() {
    Text("Account Settings",color=PreviewTeal, textAlign=TextAlign.End, modifier=Modifier.fillMaxWidth())
    Text("SAMPLE LANDSCAPING", fontWeight=FontWeight.Black,
        style=MaterialTheme.typography.headlineLarge, textAlign=TextAlign.Center,
        modifier=Modifier.fillMaxWidth(),color=Color.White)
    Text("Landscaping",style=MaterialTheme.typography.titleLarge,color=PreviewTeal,
        textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    SampleActionCard("Switch Resident","Troubleshoot a resident account",PreviewOrange,PreviewPurple) {
        Icon(Icons.Default.People,contentDescription=null,tint=Color.White)
    }
    SampleActionCard("Test New Signup","Test registration & SMS verification",PreviewTeal,PreviewPurple) {
        Icon(Icons.Default.PersonAdd,contentDescription=null,tint=Color.White)
    }
    SampleActionCard("HOA Demo Account","Cottonwood Creek Board Member",PreviewPurple,PreviewTeal) {
        Icon(Icons.Default.House,contentDescription=null,tint=Color.White)
    }
    PreviewPanel("Business profile", "Sample landscaping provider", "Home · Events · Jobs Submitted · Service Requests")
    PreviewPanel("Vendor requests", "Sample customer request", "Status: New · Visual preview only")
}

@Composable
private fun ResidentModeSample() {
    Text("YOUR NEIGHBORHOOD",color=PreviewTeal,fontWeight=FontWeight.Bold)
    Text("Country Place",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black)
    PreviewPanel("Welcome, Sample Resident", "Country Place · Plano, TX", "Resident account preview")
    PreviewPanel("Neighborhood Street Fair", "Food trucks, neighborhood events and participating local businesses", "Explore Street Fair")
    PreviewPanel("Community events", "Upcoming neighborhood gatherings and HOA events", "View events")
    PreviewPanel("Find a local provider", "Explore trusted service providers", "Browse services")
    PreviewPanel("My requests", "Sample service request status", "View requests")
}

@Composable
private fun BoardModeSample() {
    Text("HOA BOARD PREVIEW",color=PreviewTeal,fontWeight=FontWeight.Bold)
    Text("Cottonwood Creek",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black)
    Text("Board member view · Sample data only",color=Color.White.copy(alpha=0.8f))
    PreviewPanel("Neighborhood events", "Plan community gatherings and street fairs", "Sample: Neighborhood Engagement Event")
    PreviewPanel("Vendor directory", "Review providers participating in neighborhood activities", "Local businesses")
    PreviewPanel("Resident experience", "Preview what residents will see during an HOA event", "Country Place / Cottonwood Creek layouts")
}

@Composable
private fun SignupModeSample() {
    Text("Join Clubhouse Links",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
    Text("Preview the appearance of the registration flow without sending SMS or creating an account.")
    var role by remember { mutableStateOf("Resident") }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=role=="Resident",onClick={role="Resident"},label={Text("Resident")})
        FilterChip(selected=role=="Vendor",onClick={role="Vendor"},label={Text("Vendor")})
    }
    PreviewPanel("Step 1 — Mobile number", "(214) 555-0100", "Send verification code (preview)")
    PreviewPanel("Step 2 — Verification", "6-digit SMS code", "Verify phone (preview)")
    PreviewPanel("Step 3 — Profile", if(role=="Resident") "First and last name · Address · Invite code"
        else "Existing vendor profile", "No live request is submitted")
}

@Composable
private fun PreviewPanel(title: String, subtitle: String, detail: String) {
    Card(
        modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(containerColor=Color(0xFF222034)),
        border=BorderStroke(1.dp,PreviewViolet.copy(alpha=0.45f))
    ) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,color=Color.White)
            Text(subtitle,color=Color.White.copy(alpha=0.86f))
            Text(detail,color=PreviewTeal,style=MaterialTheme.typography.bodySmall)
        }
    }
}
