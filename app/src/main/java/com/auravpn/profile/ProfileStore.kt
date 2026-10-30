package com.auravpn.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Private app storage, with Android Auto Backup disabled in the manifest. */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("profiles_private", Context.MODE_PRIVATE)

    @Synchronized
    fun list(): List<StoredProfile> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { item ->
                    StoredProfile(item.getString("id"), item.getString("name"), item.getString("protocol"), item.getString("source"))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized
    fun import(text: String): ImportSummary {
        val parsed = SubscriptionParser.parse(text)
        if (parsed !is SubscriptionParseResult.Success) {
            return ImportSummary(0, 0, (parsed as SubscriptionParseResult.Failure).reason)
        }
        val existing = list().associateBy { it.id }.toMutableMap()
        var added = 0
        var duplicates = 0
        parsed.value.nodes.forEach { node ->
            val id = fingerprint(node.source)
            if (existing.containsKey(id)) duplicates++
            else {
                existing[id] = StoredProfile(id, node.name, node.protocol, node.source)
                added++
            }
        }
        persist(existing.values.toList())
        return ImportSummary(added, duplicates, null)
    }

    @Synchronized
    fun delete(id: String) {
        persist(list().filterNot { it.id == id })
    }

    private fun persist(items: List<StoredProfile>) {
        val array = JSONArray()
        items.forEach { profile ->
            array.put(JSONObject().put("id", profile.id).put("name", profile.name)
                .put("protocol", profile.protocol).put("source", profile.source))
        }
        check(prefs.edit().putString("items", array.toString()).commit()) { "Profile storage failed" }
    }

    private fun fingerprint(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

data class StoredProfile(val id: String, val name: String, val protocol: String, val source: String)
data class ImportSummary(val added: Int, val duplicates: Int, val error: String?)
