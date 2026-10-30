package com.auravpn.profile

import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class SubscriptionFormat { SHARE_LINKS, CLASH_YAML, SING_BOX_JSON, XRAY_JSON }

data class SubscriptionMetadata(
    val title: String? = null,
    val updateIntervalHours: Int? = null,
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expireEpochSeconds: Long? = null
)

data class ImportedNode(
    val name: String,
    val protocol: String,
    val server: String? = null,
    val port: Int? = null,
    val source: String
)

data class SubscriptionResult(
    val format: SubscriptionFormat,
    val nodes: List<ImportedNode>,
    val metadata: SubscriptionMetadata = SubscriptionMetadata(),
    val warnings: List<String> = emptyList()
)

sealed class SubscriptionParseResult {
    data class Success(val value: SubscriptionResult) : SubscriptionParseResult()
    data class Failure(val reason: String) : SubscriptionParseResult()
}

/**
 * Imports common subscription envelopes. This stage extracts nodes and metadata;
 * engine-specific conversion and runtime validation are deliberately separate.
 */
object SubscriptionParser {
    fun parse(input: String, headers: Map<String, String> = emptyMap()): SubscriptionParseResult {
        val original = input.trim().removePrefix("\uFEFF")
        if (original.isEmpty()) return SubscriptionParseResult.Failure("Empty subscription")
        val metadata = parseMetadata(headers)
        val content = decodeIfBase64(original)
        return try {
            when {
                content.startsWith("{") || content.startsWith("[") -> parseJson(content, metadata)
                looksLikeYaml(content) -> parseClashYaml(content, metadata)
                else -> parseLinks(content, metadata)
            }
        } catch (e: Exception) {
            SubscriptionParseResult.Failure("Unable to parse subscription: ${e.message ?: "invalid format"}")
        }
    }

    private fun parseJson(text: String, metadata: SubscriptionMetadata): SubscriptionParseResult {
        val rootArray = text.trimStart().startsWith("[")
        val root = if (rootArray) JSONArray(text) else JSONObject(text)
        val nodes = mutableListOf<ImportedNode>()
        var format: SubscriptionFormat? = null

        fun consumeXray(obj: JSONObject) {
            format = SubscriptionFormat.XRAY_JSON
            val outbounds = obj.optJSONArray("outbounds") ?: JSONArray()
            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(i) ?: continue
                val protocol = outbound.optString("protocol").takeIf { it.isNotBlank() } ?: continue
                val settings = outbound.optJSONObject("settings") ?: JSONObject()
                val server = firstServer(settings)
                nodes += ImportedNode(outbound.optString("tag", protocol), protocol, server?.first, server?.second, outbound.toString())
            }
        }

        fun consumeSingBox(obj: JSONObject) {
            format = SubscriptionFormat.SING_BOX_JSON
            val outbounds = obj.optJSONArray("outbounds") ?: JSONArray()
            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(i) ?: continue
                val type = outbound.optString("type").takeIf { it.isNotBlank() } ?: continue
                if (type in setOf("direct", "block", "dns", "selector", "urltest")) continue
                nodes += ImportedNode(
                    outbound.optString("tag", type), type,
                    outbound.optString("server").takeIf { it.isNotBlank() },
                    outbound.optInt("server_port").takeIf { it in 1..65535 },
                    outbound.toString()
                )
            }
        }

        fun consumeArray(array: JSONArray) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                when {
                    item.has("protocol") && item.has("settings") -> consumeXray(JSONObject().put("outbounds", JSONArray().put(item)))
                    item.has("type") && item.has("server") -> consumeSingBox(JSONObject().put("outbounds", JSONArray().put(item)))
                    else -> Unit
                }
            }
        }

        when (root) {
            is JSONObject -> when {
                root.has("outbounds") && root.optJSONArray("outbounds")?.optJSONObject(0)?.has("type") == true -> consumeSingBox(root)
                root.has("outbounds") -> consumeXray(root)
                root.has("servers") -> return SubscriptionParseResult.Failure("Unsupported JSON server schema")
                else -> return SubscriptionParseResult.Failure("Unrecognized JSON configuration")
            }
            is JSONArray -> consumeArray(root)
        }
        val detected = format ?: SubscriptionFormat.XRAY_JSON
        if (nodes.isEmpty()) return SubscriptionParseResult.Failure("No supported outbound nodes found")
        return SubscriptionParseResult.Success(SubscriptionResult(detected, nodes, metadata))
    }

    private fun firstServer(settings: JSONObject): Pair<String?, Int?>? {
        val list = settings.optJSONArray("vnext") ?: settings.optJSONArray("servers") ?: settings.optJSONArray("addresses")
        val item = list?.optJSONObject(0) ?: return null
        val host = item.optString("address").takeIf { it.isNotBlank() }
            ?: item.optString("server").takeIf { it.isNotBlank() }
        val port = item.optInt("port").takeIf { it in 1..65535 }
        return host to port
    }

    private fun parseClashYaml(text: String, metadata: SubscriptionMetadata): SubscriptionParseResult {
        val options = LoaderOptions().apply { isAllowDuplicateKeys = false; maxAliasesForCollections = 20 }
        val root = Yaml(SafeConstructor(options)).load<Any?>(text) as? Map<*, *>
            ?: return SubscriptionParseResult.Failure("Clash YAML root must be a mapping")
        val proxies = root["proxies"] as? List<*> ?: return SubscriptionParseResult.Failure("No Clash proxies list")
        val nodes = proxies.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val type = map["type"]?.toString() ?: return@mapNotNull null
            val name = map["name"]?.toString() ?: type
            val server = map["server"]?.toString()
            val port = map["port"]?.toString()?.toIntOrNull()?.takeIf { it in 1..65535 }
            ImportedNode(name, type, server, port, map.toString())
        }
        if (nodes.isEmpty()) return SubscriptionParseResult.Failure("No valid Clash proxy entries")
        return SubscriptionParseResult.Success(SubscriptionResult(SubscriptionFormat.CLASH_YAML, nodes, metadata))
    }

    private fun parseLinks(text: String, metadata: SubscriptionMetadata): SubscriptionParseResult {
        val links = text.lineSequence().map { it.trim() }
            .filter { it.contains("://") && !it.startsWith("#") }
            .toList()
        if (links.isEmpty()) return SubscriptionParseResult.Failure("No proxy share links found")
        val nodes = mutableListOf<ImportedNode>()
        val warnings = mutableListOf<String>()
        links.forEach { link ->
            when (val parsed = ProxyUriParser.parse(link)) {
                is ProfileParseResult.Success -> {
                    val p = parsed.profile
                    nodes += ImportedNode(p.name, p.protocol.name.lowercase(), p.host, p.port, link)
                }
                is ProfileParseResult.Failure -> warnings += parsed.reason
            }
        }
        if (nodes.isEmpty()) return SubscriptionParseResult.Failure("No valid proxy links found")
        return SubscriptionParseResult.Success(SubscriptionResult(SubscriptionFormat.SHARE_LINKS, nodes, metadata, warnings))
    }

    private fun looksLikeYaml(text: String): Boolean =
        text.lineSequence().take(12).any { it.trim().startsWith("proxies:") || it.trim().startsWith("proxy-groups:") }

    private fun decodeIfBase64(input: String): String {
        if (input.contains("://") || input.startsWith("{") || input.startsWith("[") || looksLikeYaml(input)) return input
        val compact = input.filterNot { it.isWhitespace() }
        if (compact.length < 8 || !compact.matches(Regex("[A-Za-z0-9_+/=-]+"))) return input
        return try {
            val normalized = compact.replace('-', '+').replace('_', '/')
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            val decoded = String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8)
            if (decoded.any { it == '\uFFFD' || (it.code < 0x09) }) input else decoded.trim()
        } catch (_: Exception) { input }
    }

    private fun parseMetadata(headers: Map<String, String>): SubscriptionMetadata {
        val normalized = headers.entries.associate { it.key.lowercase() to it.value.trim() }
        val info = normalized["subscription-userinfo"].orEmpty()
            .split(';').mapNotNull { part ->
                val pieces = part.trim().split('=', limit = 2)
                if (pieces.size == 2) pieces[0].trim().lowercase() to pieces[1].trim().toLongOrNull() else null
            }.toMap()
        return SubscriptionMetadata(
            title = normalized["profile-title"]?.takeIf { it.isNotBlank() },
            updateIntervalHours = normalized["profile-update-interval"]?.toIntOrNull()?.takeIf { it > 0 },
            uploadBytes = info["upload"],
            downloadBytes = info["download"],
            totalBytes = info["total"],
            expireEpochSeconds = info["expire"]
        )
    }
}
