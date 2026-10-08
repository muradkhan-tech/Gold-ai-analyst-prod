package com.example.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<ContentItem>,
    val systemInstruction: ContentItem? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class ContentItem(
    val role: String? = null,
    val parts: List<PartItem>
)

@JsonClass(generateAdapter = true)
data class PartItem(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val thinkingConfig: ThinkingConfig? = null
)

@JsonClass(generateAdapter = true)
data class ThinkingConfig(
    val thinkingLevel: String // "high", "low"
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<CandidateItem>? = null,
    val error: GeminiError? = null
)

@JsonClass(generateAdapter = true)
data class CandidateItem(
    val content: ContentItem? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)
