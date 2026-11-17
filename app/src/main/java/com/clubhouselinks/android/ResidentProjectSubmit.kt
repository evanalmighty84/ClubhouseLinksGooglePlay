package com.clubhouselinks.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

// The iOS RequestView submits a resized JPEG as a data URL to the
// /residents/completed-projects endpoint. Android follows that contract.
private suspend fun projectImageDataUri(context:android.content.Context,uri:Uri):String =
    withContext(Dispatchers.IO){
        val bitmap=context.contentResolver.openInputStream(uri)?.use{
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Could not open the selected image")
        try{
            val maxSize=1400
            val scale=minOf(1f,maxSize.toFloat()/maxOf(bitmap.width,bitmap.height))
            val resized=if(scale==1f)bitmap else Bitmap.createScaledBitmap(
                bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),
                (bitmap.height*scale).toInt().coerceAtLeast(1),true)
            try{
                val out=ByteArrayOutputStream()
                require(resized.compress(Bitmap.CompressFormat.JPEG,65,out)){
                    "Could not compress project photo"
                }
                "data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
            }finally{if(resized!==bitmap)resized.recycle()}
        }finally{bitmap.recycle()}
    }

@Composable
fun ResidentProjectSubmit(session:AccountSession,navigate:(String)->Unit,readOnly:Boolean=false){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var vendors by remember(session.residentId){mutableStateOf<List<JSONObject>>(emptyList())}
    var loading by remember(session.residentId){mutableStateOf(true)}
    var sending by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var success by remember{mutableStateOf("")}
    var service by remember{mutableStateOf("Painting")}
    var vendorId by remember{mutableIntStateOf(0)}
    var otherVendor by remember{mutableStateOf(false)}
    var vendorName by remember{mutableStateOf("")}
    var vendorPhone by remember{mutableStateOf("")}
    var photo by remember{mutableStateOf<Uri?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){photo=it}
    LaunchedEffect(session.residentId){
        loading=true
        try{
            val p=ClubhouseApi.residentVendors(session.residentId)
            val arr=p.optJSONArray("vendors") ?: JSONArray()
            vendors=(0 until arr.length()).mapNotNull{arr.optJSONObject(it)}
        }catch(e:Exception){error=e.message ?: "Vendor directory could not load"}
        finally{loading=false}
    }
    val choices=remember(vendors){
        (listOf("Painting","Pool Service","Roofing","Realtor","Plumbing","Electrical",
            "Landscaping","General Contractor")+
            vendors.map{it.optString("category")}.filter(String::isNotBlank))
            .distinct().sorted()
    }
    val matching=vendors.filter{it.optString("category").contains(service,ignoreCase=true)}
    val options=if(matching.isEmpty())vendors else matching
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF002E37),ClubhouseNavy,Color(0xFF310C43))))
        .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement=Arrangement.spacedBy(17.dp)){
        Text("Submit New Project",style=MaterialTheme.typography.headlineLarge,
            fontWeight=FontWeight.Bold)
        Text("Share a completed home project and contractor with your neighbors.",
            color=Color.White.copy(alpha=0.75f))
        if(readOnly){
            Text("Support mode is read-only. Return to Aspen without submitting anything.",
                color=ClubhouseCyan)
            OutlinedButton(onClick={navigate("Home")}){Text("Back to resident home")}
        }else if(!session.isResident){
            OutlinedButton(onClick={navigate("Account")}){Text("Sign in to submit")}
        }else{
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF29183F)),
                shape=RoundedCornerShape(22.dp)){
                Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
                    Text("Project Details",style=MaterialTheme.typography.titleLarge,
                        fontWeight=FontWeight.Bold)
                    var openService by remember{mutableStateOf(false)}
                    Text("Service",color=ClubhouseCyan,fontWeight=FontWeight.Bold)
                    Box {
                        OutlinedButton(onClick={openService=true},modifier=Modifier.fillMaxWidth()){
                            Text(service,modifier=Modifier.weight(1f))
                            Text("⌄")
                        }
                        DropdownMenu(expanded=openService,onDismissRequest={openService=false}){
                            choices.forEach{choice->
                                DropdownMenuItem(text={Text(choice)},onClick={
                                    service=choice;vendorId=0;openService=false
                                })
                            }
                        }
                    }
                    Text("Vendor",color=ClubhouseCyan,fontWeight=FontWeight.Bold)
                    if(loading)CircularProgressIndicator(color=ClubhouseCyan)
                    var openVendor by remember{mutableStateOf(false)}
                    Box{
                        val selected=vendors.firstOrNull{it.optInt("id")==vendorId}
                        OutlinedButton(onClick={openVendor=true},modifier=Modifier.fillMaxWidth()){
                            Text(if(otherVendor)"Vendor not listed" else selected?.optString("company_name")
                                ?: "Select vendor",modifier=Modifier.weight(1f))
                            Text("⌄")
                        }
                        DropdownMenu(expanded=openVendor,onDismissRequest={openVendor=false}){
                            options.forEach{vendor->
                                DropdownMenuItem(text={Text(vendor.optString("company_name"))},
                                    onClick={vendorId=vendor.optInt("id");otherVendor=false;openVendor=false})
                            }
                            DropdownMenuItem(text={Text("My vendor is not listed")},onClick={
                                otherVendor=true;vendorId=0;openVendor=false
                            })
                        }
                    }
                    if(otherVendor){
                        OutlinedTextField(vendorName,{vendorName=it},
                            label={Text("Vendor name")},modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(vendorPhone,{vendorPhone=it},
                            label={Text("Vendor phone")},modifier=Modifier.fillMaxWidth())
                    }
                    Text("Finished Project Photo",color=ClubhouseCyan,
                        fontWeight=FontWeight.Bold)
                    if(photo!=null){
                        AsyncImage(model=photo,contentDescription="Selected completed project",
                            modifier=Modifier.fillMaxWidth().height(190.dp),
                            contentScale=ContentScale.Fit)
                    }
                    OutlinedButton(onClick={picker.launch("image/*")},
                        modifier=Modifier.fillMaxWidth()){
                        Text(if(photo==null)"Choose Finished Photo" else "Change Photo")
                    }
                    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                    if(success.isNotBlank())Text(success,color=ClubhouseCyan)
                    Button(enabled=!sending && photo!=null && (vendorId>0 ||
                        (otherVendor && vendorName.isNotBlank() && vendorPhone.isNotBlank())),
                        onClick={
                            scope.launch{
                                sending=true;error="";success=""
                                try{
                                    val image=projectImageDataUri(context,photo
                                        ?: throw IllegalArgumentException("Choose a photo"))
                                    val payload=JSONObject()
                                        .put("resident_id",session.residentId)
                                        .put("category",service.trim().lowercase()
                                            .replace(' ','_'))
                                        .put("image_base64",image)
                                    if(otherVendor){
                                        payload.put("vendor_name",vendorName.trim())
                                            .put("vendor_phone",vendorPhone.trim())
                                    }else payload.put("vendor_id",vendorId)
                                    ClubhouseApi.submitCompletedProject(payload)
                                    success="Completed project submitted for review."
                                    photo=null;vendorId=0;vendorName="";vendorPhone=""
                                }catch(e:Exception){error=e.message ?: "Unable to submit project"}
                                finally{sending=false}
                            }
                        },modifier=Modifier.fillMaxWidth()){
                        Text(if(sending)"Uploading…" else "Submit Completed Work")
                    }
                }
            }
        }
    }
}
