package com.auravpn.profile

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class ProxyProtocol { VMESS, VLESS, TROJAN, SHADOWSOCKS, HYSTERIA2, TUIC, WIREGUARD, SOCKS }

data class ParsedProfile(
    val protocol: ProxyProtocol,
    val name: String,
    val host: String? = null,
    val port: Int? = null,
    val userInfo: String? = null,
    val query: Map<String, String> = emptyMap(),
    val raw: String
)

sealed class ProfileParseResult {
    data class Success(val profile: ParsedProfile) : ProfileParseResult()
    data class Failure(val reason: String) : ProfileParseResult()
}

/**
 * Safe first-stage share-link parser. It extracts endpoint metadata only; it does not
 * claim that the link is a valid runnable engine configuration.
 */
object ProxyUriParser {
    fun parse(input: String): ProfileParseResult {
        val raw = input.trim()
        if (raw.isEmpty()) return ProfileParseResult.Failure("Empty profile")
        if (raw.startsWith("vmess://", ignoreCase = true)) return parseVmess(raw)
        val scheme = raw.substringBefore("://", "").lowercase()
        val protocol = when (scheme) {
            "vless" -> ProxyProtocol.VLESS
            "trojan" -> ProxyProtocol.TROJAN
            "ss" -> ProxyProtocol.SHADOWSOCKS
            "hysteria2", "hy2" -> ProxyProtocol.HYSTERIA2
            "tuic" -> ProxyProtocol.TUIC
            "wireguard" -> ProxyProtocol.WIREGUARD
            "socks", "socks5" -> ProxyProtocol.SOCKS
            else -> return ProfileParseResult.Failure("Unsupported scheme: $scheme")
        }
        return try {
            val uri = URI(raw)
            val host = uri.host ?: return ProfileParseResult.Failure("Missing or invalid host")
            val port = uri.port.takeIf { it in 1..65535 }
                ?: return ProfileParseResult.Failure("Missing or invalid port")
            val query = parseQuery(uri.rawQuery)
            val userInfo = uri.rawUserInfo?.let(::decode)
            if (protocol == ProxyProtocol.SHADOWSOCKS) return parseShadowsocks(raw)
            val name = uri.rawFragment?.let(::decode)?.takeIf { it.isNotBlank() } ?: host
            ProfileParseResult.Success(ParsedProfile(protocol, name, host, port, userInfo, query, raw))
        } catch (_: Exception) {
            if (protocol == ProxyProtocol.SHADOWSOCKS) parseShadowsocks(raw)
            else ProfileParseResult.Failure("Malformed $scheme link")
        }
    }

    private fun parseVmess(raw: String): ProfileParseResult {
        return try {
            val encoded = raw.substringAfter("://").substringBefore('#').trim()
            val decoded = decodeBase64(encoded)
            if (!decoded.trim().startsWith("{") || !decoded.trim().endsWith("}")) {
                return ProfileParseResult.Failure("VMess payload must be a JSON object")
            }
            val json = org.json.JSONObject(decoded)
            val host = json.optString("add").takeIf { it.isNotBlank() }
                ?: return ProfileParseResult.Failure("VMess server address is missing")
            val port = json.optString("port").toIntOrNull()?.takeIf { it in 1..65535 }
                ?: return ProfileParseResult.Failure("VMess server port is invalid")
            val id = json.optString("id").takeIf { it.isNotBlank() }
                ?: return ProfileParseResult.Failure("VMess UUID is missing")
            val network = json.optString("net", "tcp").ifBlank { "tcp" }.lowercase()
            val tls = json.optString("tls").lowercase()
            val query = buildMap {
                put("type", network)
                put("security", if (tls == "tls") "tls" else "none")
                listOf("host", "path", "sni", "alpn", "fp").forEach { key ->
                    json.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
                }
            }
            val name = json.optString("ps").takeIf { it.isNotBlank() } ?: host
            ProfileParseResult.Success(
                ParsedProfile(ProxyProtocol.VMESS, name, host, port, id, query, decoded)
            )
        } catch (_: Exception) {
            ProfileParseResult.Failure("Invalid VMess Base64 payload")
        }
    }

    private fun parseShadowsocks(raw: String): ProfileParseResult {
        return try {
            val body = raw.substringAfter("://").substringBefore('#')
            val decoded = if (body.contains("@")) body else decodeBase64(body)
            val uri = URI("ss://$decoded")
            val host = uri.host ?: return ProfileParseResult.Failure("Invalid Shadowsocks host")
            val port = uri.port.takeIf { it in 1..65535 }
                ?: return ProfileParseResult.Failure("Invalid Shadowsocks port")
            val rawCredentials = uri.rawUserInfo
                ?: return ProfileParseResult.Failure("Missing Shadowsocks credentials")
            val decodedCredentials = decode(rawCredentials)
            val credentials = if (decodedCredentials.contains(':')) decodedCredentials else decodeBase64(decodedCredentials)
            val name = raw.substringAfter('#', "").takeIf { it.isNotBlank() }?.let(::decode) ?: host
            ProfileParseResult.Success(
                ParsedProfile(ProxyProtocol.SHADOWSOCKS, name, host, port, credentials, parseQuery(uri.rawQuery), raw)
            )
        } catch (_: Exception) {
            ProfileParseResult.Failure("Malformed Shadowsocks link")
        }
    }

    private fun parseQuery(query: String?): Map<String, String> =
        query.orEmpty().split('&').filter { it.isNotBlank() }.associate { item ->
            val key = item.substringBefore('=')
            val value = item.substringAfter('=', "")
            decode(key) to decode(value)
        }

    private fun decode(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())

    private fun decodeBase64(value: String): String {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8)
    }
}
