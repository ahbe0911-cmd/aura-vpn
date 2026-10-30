package com.auravpn.profile

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyUriParserTest {
    @Test fun parsesVless() {
        val result = ProxyUriParser.parse("vless://uuid@example.com:443?security=tls&type=ws#Edge")
        assertTrue(result is ProfileParseResult.Success)
        val profile = (result as ProfileParseResult.Success).profile
        assertEquals(ProxyProtocol.VLESS, profile.protocol)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)
        assertEquals("tls", profile.query["security"])
    }

    @Test fun parsesTrojan() {
        val result = ProxyUriParser.parse("trojan://secret@example.com:443?sni=example.org#T1") as ProfileParseResult.Success
        assertEquals(ProxyProtocol.TROJAN, result.profile.protocol)
        assertEquals("secret", result.profile.userInfo)
    }

    @Test fun parsesShadowsocksSip002() {
        val result = ProxyUriParser.parse("ss://YWVzLTI1Ni1nY206cGFzcw@example.com:8388#SS") as ProfileParseResult.Success
        assertEquals(ProxyProtocol.SHADOWSOCKS, result.profile.protocol)
        assertEquals(8388, result.profile.port)
    }

    @Test fun parsesHysteria2() {
        val result = ProxyUriParser.parse("hysteria2://pass@example.com:443?sni=example.org#H2") as ProfileParseResult.Success
        assertEquals(ProxyProtocol.HYSTERIA2, result.profile.protocol)
    }

    @Test fun parsesTuic() {
        val result = ProxyUriParser.parse("tuic://uuid:password@example.com:443?congestion_control=bbr#TUIC") as ProfileParseResult.Success
        assertEquals(ProxyProtocol.TUIC, result.profile.protocol)
    }

    @Test fun parsesWireguard() {
        val result = ProxyUriParser.parse("wireguard://privatekey@example.com:51820?publickey=pub#WG") as ProfileParseResult.Success
        assertEquals(ProxyProtocol.WIREGUARD, result.profile.protocol)
    }

    @Test fun parsesSocks() {
        val result = ProxyUriParser.parse("socks://user:pass@example.com:1080#SOCKS") as ProfileParseResult.Success
        assertEquals(ProxyProtocol.SOCKS, result.profile.protocol)
    }

    @Test fun parsesVmessBase64Json() {
        val json = """{"add":"node.example","port":"443","id":"uuid","ps":"Node"}"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())
        val result = ProxyUriParser.parse("vmess://$encoded") as ProfileParseResult.Success
        assertEquals(ProxyProtocol.VMESS, result.profile.protocol)
        assertTrue(result.profile.raw.contains("node.example"))
    }

    @Test fun rejectsMalformedInput() {
        assertTrue(ProxyUriParser.parse("vless://not a valid link") is ProfileParseResult.Failure)
        assertTrue(ProxyUriParser.parse("unknown://host:443") is ProfileParseResult.Failure)
        assertTrue(ProxyUriParser.parse("") is ProfileParseResult.Failure)
    }
}
