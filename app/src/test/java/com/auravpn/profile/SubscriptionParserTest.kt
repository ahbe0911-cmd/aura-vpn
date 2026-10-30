package com.auravpn.profile

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SubscriptionParserTest {
    @Test fun parsesPlainLinks() {
        val result = SubscriptionParser.parse("vless://id@example.com:443#A\ntrojan://pw@example.org:443#B")
        assertTrue(result is SubscriptionParseResult.Success)
        assertEquals(2, (result as SubscriptionParseResult.Success).value.nodes.size)
    }

    @Test fun decodesBase64Subscription() {
        val plain = "vless://id@example.com:443#Node"
        val encoded = Base64.getEncoder().encodeToString(plain.toByteArray())
        val result = SubscriptionParser.parse(encoded)
        assertTrue(result is SubscriptionParseResult.Success)
    }

    @Test fun importsVmessSubscriptionNodeWithDecodedEndpoint() {
        val payload = """{"v":"2","ps":"VMess Node","add":"vmess.example.com","port":"443","id":"b831381d-6324-4d53-ad4f-8cda48b30811","aid":"0","net":"ws","path":"/ray","tls":"tls"}"""
        val link = "vmess://" + Base64.getEncoder().encodeToString(payload.toByteArray())
        val result = SubscriptionParser.parse(link) as SubscriptionParseResult.Success
        assertEquals(1, result.value.nodes.size)
        assertEquals("VMess Node", result.value.nodes.first().name)
        assertEquals("vmess.example.com", result.value.nodes.first().server)
        assertEquals(443, result.value.nodes.first().port)
    }

    @Test fun readsSubscriptionHeaders() {
        val result = SubscriptionParser.parse(
            "vless://id@example.com:443",
            mapOf("Subscription-Userinfo" to "upload=10; download=20; total=100; expire=1900000000",
                "profile-title" to "Test", "profile-update-interval" to "12")
        ) as SubscriptionParseResult.Success
        assertEquals("Test", result.value.metadata.title)
        assertEquals(12, result.value.metadata.updateIntervalHours)
        assertEquals(100L, result.value.metadata.totalBytes)
    }

    @Test fun parsesClashYaml() {
        val yaml = """
            proxies:
              - name: edge
                type: trojan
                server: edge.example
                port: 443
        """.trimIndent()
        val result = SubscriptionParser.parse(yaml) as SubscriptionParseResult.Success
        assertEquals(SubscriptionFormat.CLASH_YAML, result.value.format)
        assertEquals("edge.example", result.value.nodes.first().server)
    }

    @Test fun parsesSingBoxJson() {
        val json = """{"outbounds":[{"type":"vless","tag":"edge","server":"edge.example","server_port":443},{"type":"direct","tag":"direct"}]}"""
        val result = SubscriptionParser.parse(json) as SubscriptionParseResult.Success
        assertEquals(SubscriptionFormat.SING_BOX_JSON, result.value.format)
        assertEquals(1, result.value.nodes.size)
    }

    @Test fun parsesXrayJson() {
        val json = """{"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"edge.example","port":443}]}}]}"""
        val result = SubscriptionParser.parse(json) as SubscriptionParseResult.Success
        assertEquals(SubscriptionFormat.XRAY_JSON, result.value.format)
        assertEquals("edge.example", result.value.nodes.first().server)
    }

    @Test fun rejectsMalformedContent() {
        assertTrue(SubscriptionParser.parse("not a subscription").let { it is SubscriptionParseResult.Failure })
        assertTrue(SubscriptionParser.parse("{broken json").let { it is SubscriptionParseResult.Failure })
    }
}
