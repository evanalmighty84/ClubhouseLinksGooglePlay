package com.clubhouselinks.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Signup / existing vendor access mirror the iOS SMS-verification endpoints.
 * The current iOS backend uses IDs without server-issued access tokens; this is NOT a
 * substitute for server authorization. Release is blocked until that is addressed.
 */
@Composable
fun AccountScreen(session: AccountSession, onLogin: (AccountSession) -> Unit, onSignOut: () -> Unit) {
    if(session.isVendor || session.isResident) {
        ContentColumn {
            Text("My account", style = MaterialTheme.typography.headlineMedium)
            InfoCard(session.displayName, if(session.isVendor) "Vendor account" else "Resident · ${session.neighborhoodName}")
            Text("Account data is provided by the existing Clubhouse Links backend.")
            OutlinedButton(onClick = onSignOut) { Text("Sign out on this device") }
        }
        return
    }
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var verifiedFor by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("resident") }
    val validPhone = phone.length == 10
    fun launchAction(action: suspend () -> Unit) {
        if(!busy) scope.launch { busy = true; message = ""; try { action() } catch(e: Exception){ message=e.message ?: "Something went wrong" } finally {busy=false} }
    }
    ContentColumn {
        Text("Join Clubhouse Links", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold)
        Text("Verify your mobile number before joining or accessing your vendor profile.")
        SingleChoiceSegment(account, onChange = { account=it }, first="resident", second="vendor")
        OutlinedTextField(
            value=phone, onValueChange={ new -> val digits=new.filter(Char::isDigit).take(10); if(digits!=phone){ verified=false; verifiedFor="" }; phone=digits },
            modifier=Modifier.fillMaxWidth(), label={Text("10-digit mobile phone")}, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone), singleLine=true
        )
        Button(enabled=validPhone && !busy, onClick={launchAction {
            ClubhouseApi.sendVerification(phone)
            verified=false
            message="A verification code has been sent."
        }}) { Text(if(busy) "Working…" else "Send verification code") }
        OutlinedTextField(value=code,onValueChange={code=it.filter(Char::isDigit).take(6)},label={Text("6-digit verification code")},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        Button(enabled=validPhone && code.length==6 && !busy, onClick={launchAction {
            val response=ClubhouseApi.checkVerification(phone, code)
            if(response.optBoolean("success",false) || response.optBoolean("verified",false)){
                verified=true; verifiedFor=phone; message="Phone verified."
            } else message=response.optString("message", "Verification was not accepted")
        }}) { Text("Verify phone") }
        if(verified && verifiedFor==phone && account=="vendor") {
            Button(enabled=!busy, onClick={launchAction {
                val response=ClubhouseApi.vendorLookup(phone)
                val vendor=response?.optJSONObject("vendor")
                if(vendor==null) message="No vendor account was found for that phone number."
                else onLogin(AccountSession("vendor", vendorId=vendor.optInt("id"), displayName=vendor.optString("company_name")))
            }}) { Text("Open vendor account") }
        }
        if(verified && verifiedFor==phone && account=="resident"){
            OutlinedTextField(firstName,{firstName=it},Modifier.fillMaxWidth(),label={Text("First name")},singleLine=true)
            OutlinedTextField(lastName,{lastName=it},Modifier.fillMaxWidth(),label={Text("Last name")},singleLine=true)
            OutlinedTextField(address,{address=it},Modifier.fillMaxWidth(),label={Text("Street address")})
            OutlinedTextField(inviteCode,{inviteCode=it},Modifier.fillMaxWidth(),label={Text("Neighborhood or contractor invite code")})
            Text("The existing iOS signup flow requires a valid invite code.", style=MaterialTheme.typography.bodySmall)
            Button(enabled=!busy && firstName.isNotBlank() && lastName.isNotBlank() && inviteCode.isNotBlank(), onClick={launchAction {
                val result=ClubhouseApi.signUp(firstName.trim(),lastName.trim(),phone,address.trim(),inviteCode.trim())
                val resident=result.optJSONObject("resident")
                val id=resident?.optInt("id") ?: result.optInt("resident_id")
                if(id>0) onLogin(AccountSession("resident",residentId=id,displayName="${firstName.trim()} ${lastName.trim()}",neighborhoodName=resident?.optString("neighborhood_name").orEmpty()))
                else message=result.optString("message","Signup response did not contain a resident account")
            }}) { Text("Create resident account") }
        }
        if(message.isNotBlank()) Text(message)
        InfoCard("Security before Play release", "The existing server needs authenticated resident/vendor sessions and per-account authorization. Phone verification alone cannot secure all current API routes.")
    }
}
@Composable
private fun SingleChoiceSegment(selected: String, onChange: (String)->Unit, first: String, second: String){
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        FilterChip(selected==first,onClick={onChange(first)},label={Text("Resident")})
        FilterChip(selected==second,onClick={onChange(second)},label={Text("Vendor")})
    }
}
