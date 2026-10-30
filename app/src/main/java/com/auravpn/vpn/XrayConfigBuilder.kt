package com.auravpn.vpn

import com.auravpn.profile.ParsedProfile
import com.auravpn.profile.ProfileParseResult
import com.auravpn.profile.ProxyProtocol
import com.auravpn.profile.ProxyUriParser
import org.json.JSONArray
import org.json.JSONObject

sealed class EngineConfigResult {
    data class Ready(val json: String) : EngineConfigResult()
    data class Unsupported(val reason: String) : EngineConfigResult()
}

/**
 * Generates a strictly limited Xray outbound configuration.
 * Does not start a VPN, native core, local proxy or TUN bridge.
 * Unsupported transports are rejected rather than silently ignored.
 */
object XrayConfigBuilder {
    fun fromShareLink(link: String): EngineConfigResult {
        val parsed = ProxyUriParser.parse(link)
        if (parsed !is ProfileParseResult.Success) {
            return EngineConfigResult.Unsupported((parsed as ProfileParseResult.Failure).reason)
        }
        return fromProfile(parsed.profile)
    }

    private fun fromVmess(profile: ParsedProfile): EngineConfigResult {
        return try {
            val vmess = JSONObject(profile.raw)
            val host = profile.host ?: return EngineConfigResult.Unsupported("VMess server address is missing")
            val port = profile.port?.takeIf { it in 1..65535 }
                ?: return EngineConfigResult.Unsupported("VMess server port is invalid")
            val id = profile.userInfo?.takeIf { it.isNotBlank() }
                ?: return EngineConfigResult.Unsupported("VMess UUID is missing")
            try { java.util.UUID.fromString(id) } catch (_: Exception) {
                return EngineConfigResult.Unsupported("Invalid VMess UUID")
            }
            val alterId = vmess.optString("aid", "0").toIntOrNull()?.takeIf { it >= 0 }
                ?: return EngineConfigResult.Unsupported("Invalid VMess alterId")
            val cipher = vmess.optString("scy", "auto").ifBlank { "auto" }
            if (cipher !in setOf("auto", "none", "zero", "aes-128-gcm", "chacha20-poly1305")) {
                return EngineConfigResult.Unsupported("Unsupported VMess cipher: $cipher")
            }
            val network = profile.query["type"] ?: "tcp"
            if (network !in setOf("tcp", "ws")) {
                return EngineConfigResult.Unsupported("VMess transport $network is not supported yet")
            }
            val security = profile.query["security"] ?: "none"
            if (security !in setOf("none", "tls")) {
                return EngineConfigResult.Unsupported("VMess security $security is not supported yet")
            }
            if (!profile.query["fp"].isNullOrBlank() || !profile.query["alpn"].isNullOrBlank()) {
                return EngineConfigResult.Unsupported("VMess fingerprint/custom ALPN needs a separate adapter")
            }
            val user = JSONObject().put("id", id).put("alterId", alterId).put("security", cipher)
            val server = JSONObject().put("address", host).put("port", port)
                .put("users", JSONArray().put(user))
            val settings = JSONObject().put("vnext", JSONArray().put(server))
            val stream = JSONObject().put("network", network).put("security", security)
            if (network == "ws") {
                val ws = JSONObject().put("path", profile.query["path"] ?: "/")
                profile.query["host"]?.takeIf { it.isNotBlank() }?.let {
                    ws.put("headers", JSONObject().put("Host", it))
                }
                stream.put("wsSettings", ws)
            }
            if (security == "tls") {
                stream.put("tlsSettings", JSONObject().put("serverName", profile.query["sni"] ?: host))
            }
            val outbound = JSONObject().put("tag", "proxy").put("protocol", "vmess")
                .put("settings", settings).put("streamSettings", stream)
            EngineConfigResult.Ready(JSONObject().put("outbounds", JSONArray().put(outbound)).toString())
        } catch (e: Exception) {
            EngineConfigResult.Unsupported("Malformed VMess configuration: ${e.message ?: "invalid JSON"}")
        }
    }

    private fun fromShadowsocks(profile: ParsedProfile): EngineConfigResult {
        val host = profile.host?.takeIf { it.isNotBlank() }
            ?: return EngineConfigResult.Unsupported("Shadowsocks server address is missing")
        val port = profile.port?.takeIf { it in 1..65535 }
            ?: return EngineConfigResult.Unsupported("Shadowsocks server port is invalid")
        val credentials = profile.userInfo?.split(':', limit = 2)
            ?: return EngineConfigResult.Unsupported("Shadowsocks credentials are missing")
        if (credentials.size != 2 || credentials[0].isBlank() || credentials[1].isBlank()) {
            return EngineConfigResult.Unsupported("Shadowsocks credentials must be method:password")
        }
        val method = credentials[0].lowercase()
        val supportedMethods = setOf(
            "aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305",
            "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm"
        )
        if (method !in supportedMethods) {
            return EngineConfigResult.Unsupported("Unsupported Shadowsocks cipher: $method")
        }
        if (profile.query.keys.any { it != "plugin" }) {
            return EngineConfigResult.Unsupported("Unknown Shadowsocks options")
        }
        if (!profile.query["plugin"].isNullOrBlank()) {
            return EngineConfigResult.Unsupported("Shadowsocks SIP003 plugins are not supported yet")
        }
        val server = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("method", method)
            .put("password", credentials[1])
        val outbound = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
        return EngineConfigResult.Ready(JSONObject().put("outbounds", JSONArray().put(outbound)).toString())
    }

    fun fromProfile(profile: ParsedProfile): EngineConfigResult {
        if (profile.protocol == ProxyProtocol.VMESS) return fromVmess(profile)
        if (profile.protocol == ProxyProtocol.SHADOWSOCKS) return fromShadowsocks(profile)
        if (profile.protocol !in setOf(ProxyProtocol.VLESS, ProxyProtocol.TROJAN)) {
            return EngineConfigResult.Unsupported("This protocol needs a separate adapter")
        }
        val host = profile.host ?: return EngineConfigResult.Unsupported("Missing server host")
        val port = profile.port?.takeIf { it in 1..65535 }
            ?: return EngineConfigResult.Unsupported("Invalid server port")
        val secret = profile.userInfo?.takeIf { it.isNotBlank() }
            ?: return EngineConfigResult.Unsupported("Missing credentials")
        val transport = profile.query["type"] ?: "tcp"
        if (transport !in setOf("tcp", "ws")) return EngineConfigResult.Unsupported("Transport $transport is not supported yet")
        val security = profile.query["security"] ?: "none"
        if (security !in setOf("none", "tls")) {
            return EngineConfigResult.Unsupported("Security $security is not supported yet")
        }
        val supportedParams = setOf("type", "security", "sni", "peer", "allowInsecure", "host", "path", "encryption", "flow", "alpn", "fp")
        val unknownParams = profile.query.keys - supportedParams
        if (unknownParams.isNotEmpty()) return EngineConfigResult.Unsupported("Unrecognized options: ${unknownParams.joinToString()}")
        if (!profile.query["fp"].isNullOrBlank()) return EngineConfigResult.Unsupported("TLS fingerprint requires a separate adapter")
        if (!profile.query["alpn"].isNullOrBlank()) return EngineConfigResult.Unsupported("Custom ALPN requires a separate adapter")
        if (profile.protocol == ProxyProtocol.VLESS && profile.query["encryption"] !in setOf(null, "none")) {
            return EngineConfigResult.Unsupported("Unsupported VLESS encryption")
        }
        if (transport == "tcp" && (profile.query["path"] != null || profile.query["host"] != null)) {
            return EngineConfigResult.Unsupported("WebSocket options require type=ws")
        }
        val server = JSONObject().put("address", host).put("port", port)
        val settings = JSONObject()
        when (profile.protocol) {
            ProxyProtocol.VLESS -> {
                if (!Regex("^[0-9a-fA-F-]{36}$").matches(secret)) {
                    return EngineConfigResult.Unsupported("Invalid VLESS user ID")
                }
                val user = JSONObject().put("id", secret).put("encryption", "none")
                val flow = profile.query["flow"]
                if (!flow.isNullOrEmpty()) return EngineConfigResult.Unsupported("VLESS flow $flow needs a separate adapter")
                server.put("users", JSONArray().put(user))
                settings.put("vnext", JSONArray().put(server))
            }
            ProxyProtocol.TROJAN -> {
                server.put("password", secret)
                settings.put("servers", JSONArray().put(server))
            }
            else -> return EngineConfigResult.Unsupported("Unsupported protocol")
        }
        val stream = JSONObject().put("network", transport).put("security", security)
        if (transport == "ws") {
            val ws = JSONObject().put("path", profile.query["path"] ?: "/")
            val wsHost = profile.query["host"]
            if (!wsHost.isNullOrBlank()) ws.put("headers", JSONObject().put("Host", wsHost))
            stream.put("wsSettings", ws)
        }
        if (security == "tls") {
            val tls = JSONObject()
            val sni = profile.query["sni"] ?: profile.query["peer"] ?: host
            tls.put("serverName", sni)
            if (profile.query["allowInsecure"] == "1") {
                return EngineConfigResult.Unsupported("Insecure TLS is not supported")
            }
            stream.put("tlsSettings", tls)
        }
        val outbound = JSONObject()
            .put("tag", "proxy")
            .put("protocol", profile.protocol.name.lowercase())
            .put("settings", settings)
            .put("streamSettings", stream)
        return EngineConfigResult.Ready(JSONObject().put("outbounds", JSONArray().put(outbound)).toString())
    }
}
