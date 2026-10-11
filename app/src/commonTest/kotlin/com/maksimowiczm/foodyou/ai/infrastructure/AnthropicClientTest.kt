package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import com.maksimowiczm.foodyou.ai.infrastructure.anthropic.AnthropicClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnthropicClientTest {

    @Test
    fun `sends messages request`() = runBlocking {
        var captured: HttpRequestData? = null
        val client =
            anthropicClientOf(MockEngine { request ->
                captured = request
                respond(content = MESSAGES_RESPONSE)
            })

        client.complete(
            system = SYSTEM_PROMPT,
            user = "a bowl of oatmeal",
            images = listOf(PreparedImage(jpeg = byteArrayOf(), base64 = "Zmlyc3Q=")),
            config = CONFIG,
        )

        val request = assertNotNull(captured)
        assertTrue(request.url.toString().contains("/v1/messages"))
        assertEquals("test-key", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
        val body = request.bodyText()
        assertTrue(body.contains("\"max_tokens\":1024"))
        val imageBlockIndex = body.indexOf("\"type\":\"base64\"")
        val textBlockIndex = body.indexOf("\"type\":\"text\"")
        assertTrue(imageBlockIndex in 0 until textBlockIndex)
    }

    @Test
    fun `returns first text block`() = runBlocking {
        val client = anthropicClientOf(MockEngine { respond(content = MESSAGES_RESPONSE) })

        val text = client.complete(SYSTEM_PROMPT, "a bowl of oatmeal", emptyList(), CONFIG)

        assertEquals("Oatmeal", text)
    }

    @Test
    fun `429 maps to RateLimited`() = runBlocking {
        val client = anthropicClientOf(MockEngine { respond(content = "error", status = HttpStatusCode.TooManyRequests) })

        val exception =
            try {
                client.complete(SYSTEM_PROMPT, "a bowl of oatmeal", emptyList(), CONFIG)
                null
            } catch (exception: AiCalculationException) {
                exception
            }

        assertEquals(AiCalculationException.RateLimited, exception)
    }

    private fun anthropicClientOf(engine: MockEngine): AnthropicClient =
        AnthropicClient(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private companion object {
        val CONFIG = AiProviderConfig(apiKey = "test-key", model = "test-model", baseUrl = null)
        const val SYSTEM_PROMPT = "system prompt"
        const val MESSAGES_RESPONSE = """{"content":[{"type":"text","text":"Oatmeal"}]}"""
    }
}