package com.clubhouselinks.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun StreetFairScreen(session: AccountSession, navigate: (String)->Unit){
    ContentColumn {
        Text("Neighborhood Street Fair",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Discover participating neighborhood businesses and activities.")
        InfoCard("Find participating vendors","Browse businesses serving your community, including service providers and event vendors.",
            "View directory"){navigate("Services")}
        InfoCard("Community events","Browse upcoming local events and HOA activities.","View events"){navigate("Events")}
        InfoCard("Vendor participation","Event dates and food truck visits depend on each HOA's confirmed schedule. Please check the community events page for current listings.")
        if(!session.isResident) InfoCard("Join your neighborhood","Verify your phone and enter your community's invite code to see your neighborhood.","Sign in"){navigate("Account")}
    }
}
