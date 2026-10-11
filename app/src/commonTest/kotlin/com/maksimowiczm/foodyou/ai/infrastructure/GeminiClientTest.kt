package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import com.maksimowiczm.foodyou.ai.infrastructure.gemini.GeminiClient
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

class GeminiClientTest {

    @Test
    fun `sends generate content request`() = runBlocking {
        var captured: HttpRequestData? = null
        val client =
            geminiClientOf(MockEngine { request ->
                captured = request
                respond(content = GENERATE_CONTENT_RESPONSE)
            })

        client.complete(
            system = SYSTEM_PROMPT,
            user = "a bowl of oatmeal",
            images = listOf(PreparedImage(jpeg = byteArrayOf(), base64 = "Zmlyc3Q=")),
            config = CONFIG,
        )

        val request = assertNotNull(captured)
        assertTrue(request.url.toString().contains("/v1beta/models/test-model:generateContent"))
        assertEquals("test-key", request.headers["x-goog-api-key"])
        val body = request.bodyText()
        assertTrue(body.contains("response_mime_type"))
        assertTrue(body.contains("inline_data"))
    }

    @Test
    fun `returns parts text`() = runBlocking {
        val client = geminiClientOf(MockEngine { respond(content = GENERATE_CONTENT_RESPONSE) })

        val text = client.complete(SYSTEM_PROMPT, "a bowl of oatmeal", emptyList(), CONFIG)

        assertEquals("Oatmeal", text)
    }

    @Test
    fun `401 maps to Unauthorized`() = runBlocking {
        val client =
            geminiClientOf(
                MockEngine { respond(content = "error", status = HttpStatusCode.Unauthorized) }
            )

        val exception =
            try {
                client.complete(SYSTEM_PROMPT, "a bowl of oatmeal", emptyList(), CONFIG)
                null
            } catch (exception: AiCalculationException) {
                exception
            }

        assertEquals(AiCalculationException.Unauthorized, exception)
    }

    @Test
    fun `malformed body maps to InvalidResponse`() = runBlocking {
        val client = geminiClientOf(MockEngine { respond(content = MALFORMED_RESPONSE) })

        val exception =
            try {
                client.complete(SYSTEM_PROMPT, "a bowl of oatmeal", emptyList(), CONFIG)
                null
            } catch (exception: AiCalculationException) {
                exception
            }

        assertEquals(AiCalculationException.InvalidResponse, exception)
    }

    private fun geminiClientOf(engine: MockEngine): GeminiClient =
        GeminiClient(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private companion object {
        val CONFIG = AiProviderConfig(apiKey = "test-key", model = "test-model", baseUrl = null)
        const val SYSTEM_PROMPT = "system prompt"
        const val GENERATE_CONTENT_RESPONSE =
            """{"candidates":[{"content":{"parts":[{"text":"Oatmeal"}]}}]}"""
        const val MALFORMED_RESPONSE = "not json"
    }
}
