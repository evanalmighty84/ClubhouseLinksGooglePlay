package com.clubhouselinks.android

import android.content.Context

data class AccountSession(
    val accountType: String = "",
    val residentId: Int = 0,
    val vendorId: Int = 0,
    val displayName: String = "",
    val neighborhoodName: String = ""
) {
    val isResident get() = accountType == "resident" && residentId > 0
    val isVendor get() = accountType == "vendor" && vendorId > 0
}

class SessionStore(private val context: Context) {
    private val prefs get() = context.getSharedPreferences("clubhouse_account", Context.MODE_PRIVATE)
    fun read(): AccountSession = AccountSession(
        prefs.getString("type", "") ?: "",
        prefs.getInt("residentId", 0),
        prefs.getInt("vendorId", 0),
        prefs.getString("name", "") ?: "",
        prefs.getString("neighborhood", "") ?: ""
    )
    fun save(session: AccountSession) {
        prefs.edit().putString("type", session.accountType)
            .putInt("residentId", session.residentId).putInt("vendorId", session.vendorId)
            .putString("name", session.displayName).putString("neighborhood", session.neighborhoodName).apply()
    }
    fun clear() = prefs.edit().clear().apply()
}
