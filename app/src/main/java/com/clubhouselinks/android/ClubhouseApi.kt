package com.clubhouselinks.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Endpoint paths match the native iOS repository, not guessed API names.
 * Calls are made only by explicit user actions and use the same live backend.
 * SECURITY: production must use server-issued authenticated sessions for resident/vendor IDs.
 */
object ClubhouseApi {
    private const val root = "https://crm-function-app-5d4de511071d.herokuapp.com/server/resident_function/api"

    suspend fun sendVerification(phone: String) = post("residents/send-verification", JSONObject().put("phone", phone))
    suspend fun checkVerification(phone: String, code: String) =
        post("residents/check-verification", JSONObject().put("phone", phone).put("code", code))
    suspend fun signUp(first: String, last: String, phone: String, address: String, invite: String): JSONObject =
        post("residents/signup", JSONObject().put("first_name", first).put("last_name", last)
            .put("phone", phone).put("address", address).put("invite_code", invite))

    suspend fun vendorLookup(phone: String): JSONObject? = try {
        post("vendors/login", JSONObject().put("phone", phone))
    } catch (e: ApiException) { if (e.status == 404) null else throw e }

    suspend fun vendorRequests(vendorId: Int, limit: Int = 100, offset: Int = 0) =
        get("vendors/$vendorId/service-requests?limit=$limit&offset=$offset")
    suspend fun vendorRequestsAll(vendorId: Int): List<JSONObject> {
        val all = mutableListOf<JSONObject>()
        var offset = 0
        repeat(100) {
            val response = vendorRequests(vendorId, 100, offset)
            val page = response.optJSONArray("requests") ?: JSONArray()
            for (i in 0 until page.length()) page.optJSONObject(i)?.let(all::add)
            offset += page.length()
            val total = response.optInt("total_count", Int.MAX_VALUE)
            if (page.length() < 100 || offset >= total) return all
        }
        return all
    }
    suspend fun markVendorRequestViewed(vendorId: Int, requestId: String): JSONObject =
        patch("vendors/$vendorId/service-requests/${encode(requestId)}/viewed")
    suspend fun updateVendorRequestStatus(vendorId: Int, requestId: String, status: String): JSONObject =
        patch("vendors/$vendorId/service-requests/${encode(requestId)}/status", JSONObject().put("status", status))
    suspend fun residentVendors(residentId: Int) = get("residents/vendors/$residentId")
    suspend fun residentEvents(residentId: Int, boardMember: Boolean = false): JSONObject =
        get(if (boardMember) "hoaBoardMembers/$residentId/events" else "residents/events/$residentId")
    suspend fun residentProfile(residentId: Int) = get("residents/profile/$residentId")
    suspend fun vendorProfile(vendorId: Int) = get("vendors/$vendorId/profile")
    suspend fun residentRequests(residentId: Int) = get("residents/$residentId/service-requests")
    suspend fun residentCompletedProjects(residentId: Int) = get("residents/completed-projects/$residentId")

    // Uses same submission contract and endpoint as iOS VendorAPI.submitResidentRequest.
    suspend fun sendResidentRequest(residentId: Int, payload: JSONObject): JSONObject =
        post("residents/$residentId/service-requests", payload)

    private fun encode(id: String) = java.net.URLEncoder.encode(id, "UTF-8")
    private suspend fun get(path: String) = request("GET", path, null)
    private suspend fun post(path: String, body: JSONObject) = request("POST", path, body)
    private suspend fun patch(path: String, body: JSONObject? = null) = request("PATCH", path, body)
    private suspend fun request(method: String, path: String, payload: JSONObject?) = withContext(Dispatchers.IO) {
        val conn = (URL("$root/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (payload != null) conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val data = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val body = try { JSONObject(data) } catch (_: Exception) {
                try { JSONObject().put("events", JSONArray(data)) }
                catch (_: Exception) { JSONObject().put("error", "Unexpected server response") }
            }
            if (status !in 200..299) throw ApiException(status, body.optString("error", body.optString("message", "Request failed: $status")))
            if (body.optBoolean("success", true) == false) throw ApiException(status, body.optString("error", "Request unsuccessful"))
            body
        } finally { conn.disconnect() }
    }
}

class ApiException(val status: Int, override val message: String) : Exception(message)
