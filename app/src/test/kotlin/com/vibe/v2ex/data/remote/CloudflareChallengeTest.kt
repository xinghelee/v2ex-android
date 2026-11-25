package com.vibe.v2ex.data.remote

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareChallengeTest {

    private fun response(code: Int, cfMitigated: String? = null): Response =
        Response.Builder()
            .request(Request.Builder().url("https://www.v2ex.com/settings").build())
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message("")
            .apply { cfMitigated?.let { header("cf-mitigated", it) } }
            .build()

    @Test
    fun `challenge header marks a cloudflare challenge`() {
        assertTrue(response(403, "challenge").isCloudflareChallenge())
        assertTrue(response(403, " Challenge ").isCloudflareChallenge())
    }

    @Test
    fun `plain 403 or ok responses are not challenges`() {
        assertFalse(response(403).isCloudflareChallenge())
        assertFalse(response(200).isCloudflareChallenge())
        assertFalse(response(403, "block").isCloudflareChallenge())
    }
}
