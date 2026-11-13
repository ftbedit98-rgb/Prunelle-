package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.core.media.LocalVideoMeta
import com.example.core.media.VideoMetadataExtractor
import com.example.data.local.AiCreationEntity
import com.example.data.local.TikTokAnalysisEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

enum class AiChatMode {
    ASSISTANT,
    COMPANION
}

data class AiReplyResult(
    val text: String,
    val isPlaceholder: Boolean = true,
    val notice: String = "Mode exemple — Activez Firebase AI Logic dans AI Studio pour connecter Gemini."
)

interface AiRepository {
    val isCloudAiEnabled: Boolean

    suspend fun askAssistant(
        prompt: String,
        mode: AiChatMode,
        memoryContext: List<String>
    ): AiReplyResult

    fun askAssistantStream(
        prompt: String,
        mode: AiChatMode,
        memoryContext: List<String>
    ): Flow<String>

    suspend fun generateStudioImage(
        context: Context,
        prompt: String,
        style: String,
        aspectRatio: String,
        referenceMediaUri: String?
    ): AiCreationEntity

    suspend fun generateStudioVideo(
        prompt: String,
        durationSeconds: Int,
        style: String,
        referenceMediaUri: String?,
        onProgress: (Float) -> Unit
    ): AiCreationEntity

    suspend fun generateBirthdayPortrait(
        context: Context,
        displayName: String,
        avatarBitmap: Bitmap?,
        year: Int
    ): String

    suspend fun analyzeTikTokVideo(
        context: Context,
        tiktokUrl: String?,
        localVideoUri: String?
    ): TikTokAnalysisEntity

    suspend fun askAboutTikTokAnalysis(
        analysis: TikTokAnalysisEntity,
        question: String
    ): AiReplyResult
}

/**
 * Fallback/test implementation kept for offline Robolectric unit tests when Firebase AI Logic is inactive.
 */
class PlaceholderAiRepository : AiRepository {
    override val isCloudAiEnabled: Boolean = false

    override suspend fun askAssistant(
        prompt: String,
        mode: AiChatMode,
        memoryContext: List<String>
    ): AiReplyResult {
        delay(250)
        val memoryHint = if (mode == AiChatMode.COMPANION && memoryContext.isNotEmpty()) {
            "\n(Souvenirs autorisés actifs : ${memoryContext.take(2).joinToString(" • ")})"
        } else ""
        val prefix = if (mode == AiChatMode.COMPANION) {
            "[Réponse d'exemple — Ami IA]\nBonjour ! En tant qu'assistant IA de Prunelle, je peux discuter avec vous sur « $prompt ».$memoryHint"
        } else {
            "[Réponse d'exemple — Prunelle IA]\nVoici une réponse de démonstration pour votre demande : « $prompt »."
        }
        return AiReplyResult(
            text = "$prefix\n\nℹ️ La génération réelle par Gemini nécessite Firebase AI Logic.",
            isPlaceholder = true
        )
    }

    override fun askAssistantStream(
        prompt: String,
        mode: AiChatMode,
        memoryContext: List<String>
    ): Flow<String> = flow {
        emit(askAssistant(prompt, mode, memoryContext).text)
    }

    override suspend fun generateStudioImage(
        context: Context,
        prompt: String,
        style: String,
        aspectRatio: String,
        referenceMediaUri: String?
    ): AiCreationEntity {
        delay(250)
        val refLabel = if (referenceMediaUri != null) " (avec image de référence)" else ""
        return AiCreationEntity(
            id = UUID.randomUUID().toString(),
            creationType = "IMAGE",
            prompt = prompt,
            style = style,
            aspectRatioOrDuration = aspectRatio,
            referenceMediaUri = referenceMediaUri,
            generatedImagePath = null,
            outputPreviewText = "[Mode local — Firebase AI Logic requis]\nPrompt : $prompt\nStyle : $style • Format : $aspectRatio$refLabel",
            isPlaceholder = true
        )
    }

    override suspend fun generateStudioVideo(
        prompt: String,
        durationSeconds: Int,
        style: String,
        referenceMediaUri: String?,
        onProgress: (Float) -> Unit
    ): AiCreationEntity {
        throw IllegalStateException("La synthèse vidéo IA n'est pas disponible sans module vidéo cloud.")
    }

    override suspend fun generateBirthdayPortrait(
        context: Context,
        displayName: String,
        avatarBitmap: Bitmap?,
        year: Int
    ): String {
        throw IllegalStateException("Génération du portrait anniversaire IA désactivée en mode test/exemple.")
    }

    override suspend fun analyzeTikTokVideo(
        context: Context,
        tiktokUrl: String?,
        localVideoUri: String?
    ): TikTokAnalysisEntity {
        delay(250)
        val meta: LocalVideoMeta? = localVideoUri?.let { VideoMetadataExtractor.extract(context, it) }
        val durationStr = meta?.takeIf { it.durationMs > 0 }?.durationFormatted ?: "Non mesurée (lien distant)"
        val resolutionStr = meta?.takeIf { it.width > 0 }?.resolutionFormatted ?: "Non mesurée"
        val sourceLabel = when {
            !localVideoUri.isNullOrBlank() -> "Vidéo locale ($durationStr • $resolutionStr)"
            !tiktokUrl.isNullOrBlank() -> tiktokUrl.take(48)
            else -> "Source vidéo"
        }

        val hookScore = 78
        val rhythmScore = 74
        val visualsScore = if (meta != null && meta.width >= 1080) 82 else 76
        val overallScore = (hookScore + rhythmScore + visualsScore) / 3

        return TikTokAnalysisEntity(
            id = UUID.randomUUID().toString(),
            sourceLabel = sourceLabel,
            sourceUrl = tiktokUrl,
            localVideoUri = localVideoUri,
            durationFormatted = durationStr,
            resolutionFormatted = resolutionStr,
            overallScore = overallScore,
            hookScore = hookScore,
            rhythmScore = rhythmScore,
            visualsScore = visualsScore,
            summary = "[Rapport d'exemple — Firebase AI Logic requis pour l'analyse multimodale réelle]\n" +
                "Métadonnées détectées : durée $durationStr, résolution $resolutionStr.",
            hookAnalysis = "[Exemple] Vérifiez que l'action visuelle et la promesse texte apparaissent dès les 2 premières secondes.",
            pacingAnalysis = "[Exemple] Privilégiez des coupes dynamiques toutes les 2 à 3 secondes.",
            visualsAnalysis = "[Exemple] Soignez le cadrage vertical ($resolutionStr) et les sous-titres contrastés.",
            strengthsCsv = listOf(
                "Format vertical adapté au visionnage mobile ($resolutionStr)",
                "Structure claire permettant une accroche directe"
            ).joinToString("||"),
            weaknessesCsv = listOf(
                "Analyse visuelle complète non exécutée sans Firebase AI Logic"
            ).joinToString("||"),
            priorityFixesCsv = listOf(
                "Utiliser Firebase AI Logic pour analyser les images et l'audio réels"
            ).joinToString("||"),
            retentionTipsCsv = listOf(
                "Annoncer le bénéfice dès la 1ère seconde",
                "Varier les plans avant chaque baisse d'attention"
            ).joinToString("||"),
            suggestedTitlesCsv = listOf("3 détails clés à retenir dès maintenant").joinToString("||"),
            suggestedDescriptionsCsv = listOf("Points essentiels expliqués simplement.").joinToString("||"),
            hashtagsCsv = "#createur #conseils #video #prunelle #montage",
            pacingCurveCsv = "78,76,72,74,75,79,77,80",
            isPlaceholder = true
        )
    }

    override suspend fun askAboutTikTokAnalysis(
        analysis: TikTokAnalysisEntity,
        question: String
    ): AiReplyResult {
        delay(200)
        return AiReplyResult(
            text = "[Réponse d'exemple — TikTok AI] Concernant « ${analysis.sourceLabel} » : concentrez-vous sur une accroche visuelle immédiate.",
            isPlaceholder = true
        )
    }
}
