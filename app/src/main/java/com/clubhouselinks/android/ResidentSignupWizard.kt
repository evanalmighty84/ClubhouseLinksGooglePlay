package com.clubhouselinks.android

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Same waving hard-hat mascot asset used by iOS SignupView, not the
 * announcement video (which has houses and a megaphone).
 */
@Composable
private fun SignupWavingBird(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "signup-waving-bird")
    val tilt by transition.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bird-wave"
    )
    val floatY by transition.animateFloat(
        initialValue = -7f, targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bird-float"
    )
    Image(
        painter = painterResource(R.drawable.clubhouse_bird_wave_sprite_512),
        contentDescription = "Clubhouse Links hard-hat bird waving",
        modifier = modifier.graphicsLayer {
            rotationZ = tilt
            translationY = floatY.dp.toPx()
        },
        contentScale = ContentScale.Fit
    )
}

private val signupActionGradient = Brush.horizontalGradient(
    listOf(Color(0xFF20C6ED), Color(0xFF8A8FF0), Color(0xFFD02CDC))
)

/**
 * Android counterpart to iOS SignupView's welcome → first/last/phone/verify/
 * address/invite stepper. Existing residents are signed in after SMS verification.
 * Debug support signup retains the vendor account; it does not persist a new
 * resident login to device storage.
 */
@Composable
fun ResidentSignupWizard(
    supportTestMode: Boolean,
    onResidentCreated: (AccountSession) -> Unit,
    onCancel: () -> Unit,
    onVendorLogin: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var verifiedPhone by remember { mutableStateOf("") }
    var boardAccessToken by remember { mutableStateOf("") }

    fun action(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            message = ""
            try { block() }
            catch (e: Exception) { message = e.message ?: "The request could not be completed." }
            finally { busy = false }
        }
    }

    val background = Brush.verticalGradient(
        listOf(Color(0xFF002C37), Color(0xFF0B0E29), Color(0xFF32083D)))
    Column(
        Modifier.fillMaxSize().background(background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Sign Up", modifier = Modifier.fillMaxWidth(),
            color = Color.White, style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center)
        if (supportTestMode) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B111E)),
                border = BorderStroke(1.dp, Color(0xFFE87C18)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SUPPORT TEST MODE", color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("Testing New Resident Signup", color = Color.White.copy(alpha = 0.8f))
                        }
                        Text("●", color = Color(0xFF34C759))
                    }
                    Button(onClick = onCancel, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE87C18))) {
                        Text("↩  Cancel & Return to Aspen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // Use the original iOS signup mascot on *every* wizard step.
        SignupWavingBird(Modifier.fillMaxWidth().height(220.dp))
        Text("Create Your Account", style = MaterialTheme.typography.headlineLarge,
            color = Color.White, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(if (step == 0) "Join Clubhouse Links and discover nearby communities and trusted local services."
            else "Answer one quick question at a time.",
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (step > 0) Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center) {
            repeat(6) { index ->
                Box(Modifier.padding(horizontal = 5.dp).size(10.dp)
                    .background(
                        if (index < step) ClubhouseCyan else Color(0xFF40485E),
                        RoundedCornerShape(50)))
            }
        }
        Card(
            shape = RoundedCornerShape(25.dp),
            border = BorderStroke(1.dp, ClubhouseCyan.copy(alpha = 0.7f)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF211A38)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF132B3C), Color(0xFF451951))))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (step > 0) {
                    TextButton(onClick = { if (!busy) { step--; message = "" } },
                        modifier = Modifier.align(Alignment.Start)) {
                        Text("‹  Back", color = ClubhouseCyan, fontWeight = FontWeight.Bold)
                    }
                }
                val question = when (step) {
                    0 -> "Let’s get you set up"
                    1 -> "What’s your first name?"
                    2 -> "What’s your last name?"
                    3 -> "What’s your mobile number?"
                    4 -> "Verify your mobile number"
                    5 -> "What’s your home address?"
                    else -> "What’s your invite code?"
                }
                Text(question, color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center)
                val subtitle = when (step) {
                    0 -> "Create your account to discover nearby communities, events, and trusted local services."
                    1 -> "This helps personalize your Clubhouse Links experience."
                    2 -> "We’ll use this to create your account."
                    3 -> "We’ll text you a one-time verification code to secure your account."
                    4 -> "Enter the six-digit code we just texted you."
                    5 -> "Your address helps us find neighborhood services and projects near you."
                    else -> "Enter the code from your community, contractor, or local service provider."
                }
                Text(subtitle, color = Color.White.copy(alpha = 0.73f),
                    textAlign = TextAlign.Center)
                if (step in 1..6) {
                    val value = when(step) {
                        1 -> first; 2 -> last; 3 -> phone; 4 -> code
                        5 -> address; else -> invite
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { new ->
                            when (step) {
                                1 -> first = new
                                2 -> last = new
                                3 -> { phone = new.filter(Char::isDigit).take(10); verifiedPhone = "" }
                                4 -> code = new.filter(Char::isDigit).take(6)
                                5 -> address = new
                                6 -> invite = new.uppercase()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(when (step) {
                            1 -> "Enter First Name"
                            2 -> "Enter Last Name"
                            3 -> "Enter Mobile Number"
                            4 -> "Verification Code"
                            5 -> "Street address, city, state, ZIP"
                            else -> "Invite Code"
                        }) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (step == 3) KeyboardType.Phone
                                else if (step == 4) KeyboardType.Number else KeyboardType.Text),
                        singleLine = step != 5,
                        shape = RoundedCornerShape(18.dp)
                    )
                }
                if (message.isNotBlank()) Text(message,
                    color = Color(0xFFFFB0A5), style = MaterialTheme.typography.bodyMedium)
                val canContinue = when (step) {
                    0 -> true
                    1 -> first.isNotBlank()
                    2 -> last.isNotBlank()
                    3 -> phone.length == 10
                    4 -> code.length == 6
                    5 -> true // iOS / backend allow address to be optional.
                    else -> invite.isNotBlank()
                }
                Button(
                    enabled = canContinue && !busy,
                    onClick = {
                        when (step) {
                            0, 1, 2, 5 -> { step++; message = "" }
                            3 -> action {
                                if (verifiedPhone == phone) step = 5
                                else {
                                    ClubhouseApi.sendVerification(phone)
                                    code = ""
                                    step = 4
                                }
                            }
                            4 -> action {
                                val result = ClubhouseApi.checkVerification(phone, code)
                                if (!result.optBoolean("success", false) ||
                                    !result.optBoolean("verified", false))
                                    throw IllegalStateException("Verification code was not accepted.")
                                verifiedPhone = phone
                                boardAccessToken = result.optString("board_access_token", "")
                                val existing = ClubhouseApi.residentLookupAfterVerification(phone)
                                val resident = existing?.optJSONObject("resident")
                                if ((resident?.optInt("id", 0) ?: 0) > 0) {
                                    onResidentCreated(AccountSession(
                                        accountType = "resident",
                                        residentId = resident!!.optInt("id"),
                                        displayName = listOf(
                                            resident.optString("first_name"),
                                            resident.optString("last_name")).joinToString(" ").trim(),
                                        neighborhoodName = resident.optString("neighborhood_name", "")
                                            .takeUnless { it == "null" }.orEmpty()
                                    ))
                                } else if (existing == null) {
                                    step = 5
                                } else {
                                    message = "The verified account could not be loaded. Try signing in again."
                                }
                            }
                            6 -> action {
                                if (verifiedPhone != phone)
                                    throw IllegalStateException("Verify your phone before creating an account.")
                                val result = ClubhouseApi.signUp(
                                    first.trim(), last.trim(), phone, address.trim(), invite.trim())
                                val resident = result.optJSONObject("resident")
                                val id = resident?.optInt("id") ?: result.optInt("resident_id")
                                if (id <= 0) throw IllegalStateException("Signup did not return a resident ID.")
                                onResidentCreated(AccountSession(
                                    accountType = "resident", residentId = id,
                                    displayName = "${first.trim()} ${last.trim()}",
                                    neighborhoodName = resident?.optString("neighborhood_name", "")
                                        ?.takeUnless { it == "null" }.orEmpty(),
                                    boardAccessToken = boardAccessToken
                                ))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(55.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(signupActionGradient),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    Text(if (busy) "Working…" else when (step) {
                        0 -> "Start Step-by-Step Signup"
                        3 -> "Send Verification Code"
                        4 -> "Verify & Continue"
                        6 -> "Create Account"
                        else -> "Next"
                    }, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (step == 0 && onVendorLogin != null) {
            TextButton(onClick = onVendorLogin) {
                Text("Already a service partner? Vendor sign in", color = ClubhouseCyan)
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}
