package com.clubhouselinks.android

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.StreetViewSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

private fun JSONObject.optionalNumber(name: String): Double? =
    opt(name)?.takeIf { it != JSONObject.NULL }?.toString()?.toDoubleOrNull()

@Suppress("DEPRECATION")
private suspend fun geocodeResidence(context: Context, address: String): LatLng? =
    withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val matches = Geocoder(context, Locale.US).getFromLocationName(address, 1)
            matches?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        } catch (_: Exception) {
            null
        }
    }

/**
 * Street-level Google panorama analogous to the iOS Apple Look Around card.
 * Only created when a nonempty residential address and an app-restricted Maps
 * SDK key exist. The API may return the closest road panorama, not the house.
 * No photo is downloaded, stored, or used as the user's personal avatar.
 */
@Composable
fun ResidentStreetViewCard(profile: JSONObject) {
    val address = profile.optString("address", "").trim().takeUnless { it == "null" }.orEmpty()
    if (address.isBlank()) {
        Text("Add a street address to enable a home street view.",
            style=MaterialTheme.typography.bodyMedium)
        return
    }

    Text("Home Street View", style=MaterialTheme.typography.titleLarge,
        fontWeight=FontWeight.Bold)
    Text(address, style=MaterialTheme.typography.bodySmall)

    if (!BuildConfig.HAS_GOOGLE_MAPS_KEY) {
        Surface(shape=RoundedCornerShape(20.dp),
            color=MaterialTheme.colorScheme.surfaceVariant,
            modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.Home, contentDescription=null)
                Spacer(Modifier.width(12.dp))
                Text("Home imagery will appear after Google Maps is configured for Android. Your address is saved.")
            }
        }
        return
    }

    val context=LocalContext.current
    val lat=profile.optionalNumber("latitude")
    val lng=profile.optionalNumber("longitude")
    var position by remember(address,lat,lng) {
        mutableStateOf(if (lat!=null && lng!=null &&
            lat in -90.0..90.0 && lng in -180.0..180.0) LatLng(lat,lng) else null)
    }
    var locating by remember(address,lat,lng){mutableStateOf(position==null)}
    LaunchedEffect(address,lat,lng) {
        if(position==null){
            locating=true
            position=geocodeResidence(context,address)
            locating=false
        }
    }

    when {
        locating -> CircularProgressIndicator()
        position==null ->
            Text("Street view location could not be determined for this address.",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            val coord=position!!
            var panoramaUnavailable by remember(address,coord){mutableStateOf(false)}
            val view=remember(context,address,coord) {
                StreetViewPanoramaView(context).apply {
                    onCreate(null)
                    getStreetViewPanoramaAsync { panorama ->
                        panorama.isUserNavigationEnabled=false
                        panorama.setOnStreetViewPanoramaChangeListener { location ->
                            panoramaUnavailable=(location==null)
                        }
                        panorama.setPosition(coord, 50, StreetViewSource.OUTDOOR)
                    }
                }
            }
            val owner=LocalLifecycleOwner.current
            DisposableEffect(view,owner) {
                val observer=LifecycleEventObserver { _, event ->
                    when(event){
                        Lifecycle.Event.ON_START -> view.onStart()
                        Lifecycle.Event.ON_RESUME -> view.onResume()
                        Lifecycle.Event.ON_PAUSE -> view.onPause()
                        Lifecycle.Event.ON_STOP -> view.onStop()
                        else -> Unit
                    }
                }
                owner.lifecycle.addObserver(observer)
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))view.onStart()
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))view.onResume()
                onDispose {
                    owner.lifecycle.removeObserver(observer)
                    view.onPause()
                    view.onStop()
                    view.onDestroy()
                }
            }
            if(panoramaUnavailable){
                Text("Google Street View imagery isn't available at this address.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }else{
                AndroidView(factory={view},
                    modifier=Modifier.fillMaxWidth().height(230.dp))
            }
            Text("Google Street View • Nearest available street panorama; the home may not be visible.",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
