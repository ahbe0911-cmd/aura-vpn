package com.auravpn.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SavedSubscription(val url: String, val name: String, val lastUpdated: Long)

class SubscriptionRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("subscription_sources", Context.MODE_PRIVATE)

    @Synchronized fun list(): List<SavedSubscription> = try {
        val array = JSONArray(prefs.getString("sources", "[]"))
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            SavedSubscription(obj.getString("url"), obj.optString("name", "اشتراک"), obj.optLong("updated"))
        }
    } catch (_: Exception) { emptyList() }

    @Synchronized fun add(url: String, name: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        if (list().any { it.url == url }) return false
        save(list() + SavedSubscription(url, name.trim().ifBlank { "اشتراک" }, 0L))
        return true
    }

    @Synchronized fun remove(url: String) = save(list().filterNot { it.url == url })

    @Synchronized fun markUpdated(url: String) = save(list().map {
        if (it.url == url) it.copy(lastUpdated = System.currentTimeMillis()) else it
    })

    private fun save(sources: List<SavedSubscription>) {
        val arr = JSONArray()
        sources.forEach { arr.put(JSONObject().put("url", it.url).put("name", it.name).put("updated", it.lastUpdated)) }
        check(prefs.edit().putString("sources", arr.toString()).commit())
    }

    /** Network operations must be called from a background dispatcher. */
    fun fetch(url: String): String {
        require(url.startsWith("https://", ignoreCase = true)) { "Only HTTPS subscriptions are supported" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.instanceFollowRedirects = false
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            val declaredLength = connection.contentLengthLong
            require(declaredLength < 0 || declaredLength <= 1024 * 1024) { "Subscription too large" }
            return connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= 1024 * 1024) { "Subscription too large" }
                    out.write(buffer, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }
}
