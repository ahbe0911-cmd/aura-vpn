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
    fun fetch(url: String): String = fetchResult(url).content

    fun fetchResult(url: String): SubscriptionFetchResult {
        require(url.startsWith("https://", ignoreCase = true)) { "Only HTTPS subscriptions are supported" }
        var current = URL(url)
        repeat(4) { redirectCount ->
            require(current.protocol.equals("https", ignoreCase = true)) { "Insecure subscription redirect rejected" }
            val connection = current.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "AURA-VPN/0.1")
            connection.setRequestProperty("Accept", "text/plain, application/json, application/yaml, */*")
            try {
                val code = connection.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalArgumentException("Subscription redirect has no destination")
                    require(redirectCount < 3) { "Too many subscription redirects" }
                    val next = URL(current, location)
                    require(next.protocol.equals("https", ignoreCase = true)) { "Insecure subscription redirect rejected" }
                    current = next
                } else {
                    require(code == HttpURLConnection.HTTP_OK) { "HTTP $code" }
                    val declaredLength = connection.contentLengthLong
                    require(declaredLength < 0 || declaredLength <= 1024 * 1024) { "Subscription too large" }
                    val body = connection.inputStream.use { stream ->
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
                    val headers = connection.headerFields.entries
                        .filter { it.key != null && !it.value.isNullOrEmpty() }
                        .associate { it.key to it.value.first() }
                    return SubscriptionFetchResult(body, headers, current.toString())
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalArgumentException("Too many subscription redirects")
    }
}

data class SubscriptionFetchResult(
    val content: String,
    val headers: Map<String, String>,
    val finalUrl: String
)
