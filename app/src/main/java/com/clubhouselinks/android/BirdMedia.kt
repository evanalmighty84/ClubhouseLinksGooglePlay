package com.clubhouselinks.android

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment

/**
 * iOS video parity:
 *   iOS Resources/clubhouse_bird_announcements.mp4
 *     -> Android res/raw/clubhouse_bird_announcements.mp4
 *   iOS Resources/clubhouse-bird-newproject.mp4
 *     -> Android res/raw/clubhouse_bird_newproject.mp4
 *
 * Android falls back to existing bird sprite artwork if videos are not present.
 */
@Composable
fun ClubhouseBirdMedia(rawName:String,modifier:Modifier=Modifier){
    val context=LocalContext.current
    val id=remember(rawName) {
        context.resources.getIdentifier(rawName,"raw",context.packageName)
    }
    if(id!=0){
        AndroidView(
            modifier=modifier,
            factory={ctx->
                VideoView(ctx).apply{
                    setVideoURI(Uri.parse("android.resource://${ctx.packageName}/$id"))
                    setOnPreparedListener{ player->
                        player.isLooping=true
                        player.setVolume(0f,0f)
                        start()
                    }
                }
            },
            update={view->if(!view.isPlaying)view.start()}
        )
    }else{
        val sprite=remember{
            context.resources.getIdentifier("clubhouse_bird_wave_sprite_512",
                "drawable",context.packageName)
        }
        if(sprite!=0){
            Box(modifier.background(Color(0xFF0A3044)),contentAlignment=Alignment.Center){
                Image(painterResource(sprite),contentDescription="Clubhouse Links bird",
                    modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            }
        }else{
            Box(modifier,contentAlignment=Alignment.Center){Text("Clubhouse Links")}
        }
    }
}
