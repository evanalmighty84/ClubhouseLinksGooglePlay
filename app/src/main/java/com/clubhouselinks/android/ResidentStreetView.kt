package com.clubhouselinks.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import org.json.JSONObject

private fun cleanProfileField(profile: JSONObject, key: String): String =
    profile.optString(key, "").trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()

private fun comparableAddress(value: String): String =
    value.trim().replace(Regex("\\s+"), " ").lowercase()

/**
 * The shared house photo shown in Account Settings is an independently
 * licensed/resident-uploaded Cloudinary image saved in hoa_residents.
 *
 * Apple Look Around remains a live native MapKit view in the iOS app;
 * Google's Street View and Apple's Look Around imagery are not copied into
 * our Cloudinary image library.
 */
@Composable
fun ResidentStreetViewCard(profile: JSONObject) {
    val address = cleanProfileField(profile, "address")
    Text("Home", style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold)
    if (address.isNotBlank()) {
        Text(address, style = MaterialTheme.typography.bodySmall)
    }

    val imageUrl = cleanProfileField(profile, "house_image_url")
    val imageAddress = cleanProfileField(profile, "house_image_address")
    val allowedImage = imageUrl.takeIf {
        address.isNotBlank() &&
            imageAddress.isNotBlank() &&
            comparableAddress(address) == comparableAddress(imageAddress) &&
            (it.startsWith("https://res.cloudinary.com/"))
    }

    if (allowedImage != null) {
        SubcomposeAsyncImage(
            model = allowedImage,
            contentDescription = "Saved house photo for this residence",
            modifier = Modifier.fillMaxWidth().height(200.dp)
                .clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
            loading = { GenericHousePlaceholder("Loading house photo…") },
            error = { GenericHousePlaceholder("House photo unavailable") }
        )
    } else {
        GenericHousePlaceholder(
            if (address.isBlank()) "Add an address to show your home."
            else "No saved house photo yet."
        )
    }
}

@Composable
private fun GenericHousePlaceholder(message: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(200.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Home, contentDescription = null,
                modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(8.dp))
            Text("Clubhouse Links Home", fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
