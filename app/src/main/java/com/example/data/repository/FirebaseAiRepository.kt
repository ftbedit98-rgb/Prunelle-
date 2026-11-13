package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.core.media.MediaStorageHelper
import com.example.core.media.VideoMetadataExtractor
import com.example.data.local.AiCreationEntity
import com.example.data.local.TikTokAnalysisEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

class FirebaseAiRepository : AiRepository {
    override val isCloudAiEnabled: Boolean = true

    private val assistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "Tu es Prunelle IA, un assistant francophone élégant, clair, créatif et bienveillant intégré à l'application Prunelle. " +
                        "Réponds en français de façon structurée, utile et honnête. N'invente jamais de statistiques ou faits non fournis."
                )
            }
        )
    }

    private val companionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "Tu es Ami IA de Prunelle, un compagnon conversationnel empathique, respectueux et transparent. " +
                        "Tu rappelles clairement être une IA si demandé. Appuie-toi uniquement sur les souvenirs autorisés fournis."
                )
            }
        )
    }

    private val tikTokSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "overallScore" to Schema.integer("Note globale sur 100"),
                "hookScore" to Schema.integer("Note de l'accroche des 3 premières secondes sur 100"),
                "rhythmScore" to Schema.integer("Note du rythme et montage sur 100"),
                "visualsScore" to Schema.integer("Note qualité visuelle, cadrage et sous-titres sur 100"),
                "summary" to Schema.string("Synthèse détaillée et honnête sans inventer de statistiques externes (vues, likes, rétention réelle)"),
                "hookAnalysis" to Schema.string("Analyse détaillée de l'accroche visuelle, sonore et textuelle des premières secondes"),
                "pacingAnalysis" to Schema.string("Analyse du rythme, coupes, transitions et passages à dynamiser"),
                "visualsAnalysis" to Schema.string("Analyse du cadrage, luminosité, sous-titres et texte à l'écran"),
                "strengths" to Schema.array(Schema.string(), "3 à 5 points forts concrets"),
                "weaknesses" to Schema.array(Schema.string(), "2 à 4 points faibles ou axes d'amélioration"),
                "priorityFixes" to Schema.array(Schema.string(), "3 corrections prioritaires immédiates"),
                "retentionTips" to Schema.array(Schema.string(), "3 à 4 conseils concrets pour garder l'attention des spectateurs"),
                "suggestedTitles" to Schema.array(Schema.string(), "2 à 3 idées de titres accrocheurs"),
                "suggestedDescriptions" to Schema.array(Schema.string(), "1 à 2 propositions de descriptions adaptées"),
                "hashtags" to Schema.array(Schema.string(), "5 à 7 hashtags pertinents commençant par #"),
                "pacingCurve" to Schema.array(Schema.integer(), "8 scores entiers de 0 à 100 représentant l'intensité du rythme sur 8 segments temporels du début à la fin de la vidéo")
            )
        )
    }

    private val tikTokAnalysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = tikTokSchema
            }
        )
    }

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    override suspend fun askAssistant(
        prompt: String,
        mode: AiChatMode,
        memoryContext: List<String>
    ): AiReplyResult {
        val fullPrompt = buildChatPrompt(prompt, mode, memoryContext)
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                val model = if (mode == AiChatMode.COMPANION) companionModel else assistantModel
                model.generateContent(fullPrompt)
            }
            val text = response.text?.trim().orEmpty()
            if (text.isEmpty()) {
                throw IllegalStateException("Le modèle Gemini n'a renvoyé aucun texte.")
            }
            AiReplyResult(text = text, isPlaceholder = false, notice = "Gemini 3.8 Flash")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "askAssistant timed out", e)
            throw IllegalStateException("La requête Gemini a expiré après 60 secondes. Veuillez réessayer.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "askAssistant failed", e)
            throw e
        }
    }

    override fun askAssistantStream(
        prompt: String,
        mode: AiChatMode,
        memoryContext: List<String>
    ): Flow<String> = flow {
        val fullPrompt = buildChatPrompt(prompt, mode, memoryContext)
        val model = if (mode == AiChatMode.COMPANION) companionModel else assistantModel
        val buffer = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            model.generateContentStream(fullPrompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    buffer.append(part)
                    emit(buffer.toString())
                }
            }
        }
        if (buffer.isBlank()) {
            throw IllegalStateException("Le modèle Gemini n'a renvoyé aucun contenu.")
        }
    }.catch { err ->
        if (err is CancellationException && err !is TimeoutCancellationException) throw err
        Log.e(TAG, "askAssistantStream failed", err)
        if (err is TimeoutCancellationException) {
            throw IllegalStateException("Le streaming Gemini a expiré après 60 secondes.")
        }
        throw err
    }.flowOn(Dispatchers.IO)

    private fun buildChatPrompt(prompt: String, mode: AiChatMode, memoryContext: List<String>): String {
        return if (mode == AiChatMode.COMPANION && memoryContext.isNotEmpty()) {
            "Souvenirs autorisés de l'utilisateur :\n" +
                memoryContext.joinToString("\n") { "- $it" } +
                "\n\nMessage de l'utilisateur : $prompt"
        } else {
            prompt
        }
    }

    override suspend fun generateStudioImage(
        context: Context,
        prompt: String,
        style: String,
        aspectRatio: String,
        referenceMediaUri: String?
    ): AiCreationEntity {
        val refBitmap = withContext(Dispatchers.IO) {
            MediaStorageHelper.loadBitmapFromUriOrPath(context, referenceMediaUri, maxDim = 1024)
        }
        val richPrompt = "Create a high quality $style image, aspect ratio framing $aspectRatio. " +
            "Subject/request: $prompt. Crisp lighting, cohesive composition, no watermarks or unreadable garbled text."
        val bitmap: Bitmap = try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                if (refBitmap != null) {
                    imageModel.generateContent(
                        content {
                            image(refBitmap)
                            text(richPrompt)
                        }
                    )
                } else {
                    imageModel.generateContent(richPrompt)
                }
            }
            response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image
                ?: throw IllegalStateException("Aucune image retournée par Gemini Nano Banana 2.1.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "generateStudioImage timed out", e)
            throw IllegalStateException("La génération d'image a expiré après 60 secondes.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "generateStudioImage failed", e)
            throw e
        }

        val id = UUID.randomUUID().toString()
        val savedPath = MediaStorageHelper.saveBitmapToInternalPng(
            context = context,
            bitmap = bitmap,
            subdir = "ai_studio",
            fileName = "studio_${id.take(8)}.png"
        ) ?: throw IllegalStateException("Impossible d'enregistrer l'image générée sur l'appareil.")

        return AiCreationEntity(
            id = id,
            creationType = "IMAGE",
            prompt = prompt,
            style = style,
            aspectRatioOrDuration = aspectRatio,
            referenceMediaUri = referenceMediaUri,
            generatedImagePath = savedPath,
            outputPreviewText = "$prompt • Style $style ($aspectRatio)",
            isPlaceholder = false
        )
    }

    override suspend fun generateStudioVideo(
        prompt: String,
        durationSeconds: Int,
        style: String,
        referenceMediaUri: String?,
        onProgress: (Float) -> Unit
    ): AiCreationEntity {
        throw IllegalStateException(
            "La génération vidéo IA directe n'est pas proposée par le SDK Firebase AI Logic gratuit. Utilisez Image IA (Nano Banana 2.1) ou Prunelle TikTok AI."
        )
    }

    override suspend fun generateBirthdayPortrait(
        context: Context,
        displayName: String,
        avatarBitmap: Bitmap?,
        year: Int
    ): String {
        val bitmap: Bitmap = try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                if (avatarBitmap != null) {
                    imageModel.generateContent(
                        content {
                            image(avatarBitmap)
                            text(
                                "Create a warm, celebratory festive studio birthday portrait of the exact person shown in the input photo. " +
                                    "Preserve the person's exact facial features, skin tone, hairstyle, and likeness faithfully. " +
                                    "Add subtle deep plum, rose-gold, and warm champagne bokeh sparkles, festive birthday ambiance, refined editorial lighting. " +
                                    "Do not distort or change who the person is. No garbled text."
                            )
                        }
                    )
                } else {
                    imageModel.generateContent(
                        "Warm festive artistic birthday celebration illustration in deep plum, violet, and rose-gold tones with glowing candle sparks, delicate plum blossoms, and elegant bokeh sparkles, modern editorial aesthetic, no random text."
                    )
                }
            }
            response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image
                ?: throw IllegalStateException("Le modèle d'image n'a renvoyé aucun visuel d'anniversaire.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "generateBirthdayPortrait timed out", e)
            throw IllegalStateException("La génération du portrait d'anniversaire a expiré.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "generateBirthdayPortrait failed", e)
            throw e
        }

        return MediaStorageHelper.saveBitmapToInternalPng(
            context = context,
            bitmap = bitmap,
            subdir = "birthdays",
            fileName = "birthday_card_${year}.png"
        ) ?: throw IllegalStateException("Impossible de sauvegarder la carte d'anniversaire.")
    }

    override suspend fun analyzeTikTokVideo(
        context: Context,
        tiktokUrl: String?,
        localVideoUri: String?
    ): TikTokAnalysisEntity {
        val meta = localVideoUri?.let { VideoMetadataExtractor.extract(context, it) }
        val durationStr = meta?.takeIf { it.durationMs > 0 }?.durationFormatted ?: "Non mesurée (lien)"
        val resolutionStr = meta?.takeIf { it.width > 0 }?.resolutionFormatted ?: "Non mesurée"
        val sourceLabel = when {
            !localVideoUri.isNullOrBlank() -> "Vidéo locale ($durationStr • $resolutionStr)"
            !tiktokUrl.isNullOrBlank() -> tiktokUrl.take(48)
            else -> "Source vidéo"
        }

        val payload = withContext(Dispatchers.IO) {
            if (!localVideoUri.isNullOrBlank()) {
                val uri = Uri.parse(localVideoUri)
                val mime = context.contentResolver.getType(uri).orEmpty().ifBlank { "video/mp4" }
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val buffer = ByteArray(MAX_INLINE_VIDEO_BYTES + 1)
                        var total = 0
                        while (total <= MAX_INLINE_VIDEO_BYTES) {
                            val read = stream.read(buffer, total, buffer.size - total)
                            if (read <= 0) break
                            total += read
                        }
                        if (total <= MAX_INLINE_VIDEO_BYTES) buffer.copyOf(total) else null
                    }
                }.getOrNull()
                if (bytes != null) {
                    VideoPayload.InlineBytes(bytes, mime)
                } else {
                    VideoPayload.KeyFrames(VideoMetadataExtractor.extractKeyFrames(context, localVideoUri, 4))
                }
            } else {
                VideoPayload.UrlOnly
            }
        }

        val instructions = buildString {
            appendLine("Tu es Prunelle TikTok AI, analyste expert de vidéos courtes verticales en français.")
            appendLine("RÈGLE CRITIQUE D'HONNÊTETÉ : N'invente JAMAIS de statistiques réelles de vues, likes, partages, abonnés ou rétention mesurée auxquelles tu n'as pas accès.")
            appendLine("Si seul un lien est fourni sans fichier vidéo décodable, évalue l'angle éditorial, la structure probable du sujet/lien, propose des améliorations d'accroche/montage/rétention/titres/hashtags, et précise clairement ce qui est observé vs ce qui dépend de la vidéo.")
            appendLine("Métadonnées locales mesurées : durée=$durationStr, résolution=$resolutionStr.")
            if (!tiktokUrl.isNullOrBlank()) appendLine("Lien fourni : $tiktokUrl")
            if (payload is VideoPayload.KeyFrames) {
                appendLine("Note technique : vidéo volumineuse (>18 Mo), analyse basée sur ${payload.frames.size} images clés extraites du début à la fin.")
            }
            appendLine("Fournis pacingCurve avec exactement 8 entiers (0..100) reflétant l'énergie/rythme des 8 segments temporels.")
        }

        val response = try {
            withTimeout(AI_TIMEOUT_MS) {
                tikTokAnalysisModel.generateContent(
                    content {
                        when (payload) {
                            is VideoPayload.InlineBytes -> inlineData(payload.bytes, payload.mime)
                            is VideoPayload.KeyFrames -> payload.frames.forEach { image(it) }
                            is VideoPayload.UrlOnly -> Unit
                        }
                        text(instructions)
                    }
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "analyzeTikTokVideo timed out", e)
            throw IllegalStateException("L'analyse vidéo Gemini a expiré après 60 secondes.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "analyzeTikTokVideo failed", e)
            throw e
        }

        val rawJson = response.text ?: throw IllegalStateException("Réponse JSON vide de Gemini.")
        val json = JSONObject(rawJson)

        val hookScore = json.optInt("hookScore", 75).coerceIn(0, 100)
        val rhythmScore = json.optInt("rhythmScore", 75).coerceIn(0, 100)
        val visualsScore = json.optInt("visualsScore", 75).coerceIn(0, 100)
        val overallScore = json.optInt("overallScore", (hookScore + rhythmScore + visualsScore) / 3).coerceIn(0, 100)

        fun readList(key: String): String {
            val arr = json.optJSONArray(key) ?: return ""
            return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.joinToString("||")
        }

        val hashtagsArr = json.optJSONArray("hashtags")
        val hashtagsCsv = if (hashtagsArr != null) {
            (0 until hashtagsArr.length()).mapNotNull { hashtagsArr.optString(it).takeIf { s -> s.isNotBlank() } }.joinToString(" ")
        } else ""

        val curveArr = json.optJSONArray("pacingCurve")
        val pacingCurveCsv = if (curveArr != null && curveArr.length() > 0) {
            (0 until curveArr.length()).map { curveArr.optInt(it, overallScore).coerceIn(0, 100) }.joinToString(",")
        } else {
            listOf(hookScore, rhythmScore, overallScore, visualsScore, rhythmScore, overallScore, visualsScore, hookScore).joinToString(",")
        }

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
            summary = json.optString("summary"),
            hookAnalysis = json.optString("hookAnalysis"),
            pacingAnalysis = json.optString("pacingAnalysis"),
            visualsAnalysis = json.optString("visualsAnalysis"),
            strengthsCsv = readList("strengths"),
            weaknessesCsv = readList("weaknesses"),
            priorityFixesCsv = readList("priorityFixes"),
            retentionTipsCsv = readList("retentionTips"),
            suggestedTitlesCsv = readList("suggestedTitles"),
            suggestedDescriptionsCsv = readList("suggestedDescriptions"),
            hashtagsCsv = hashtagsCsv,
            pacingCurveCsv = pacingCurveCsv,
            isPlaceholder = false
        )
    }

    override suspend fun askAboutTikTokAnalysis(
        analysis: TikTokAnalysisEntity,
        question: String
    ): AiReplyResult {
        val contextPrompt = buildString {
            appendLine("Contexte de l'analyse Prunelle TikTok AI :")
            appendLine("Source : ${analysis.sourceLabel} (durée ${analysis.durationFormatted}, résolution ${analysis.resolutionFormatted})")
            appendLine("Scores : Global ${analysis.overallScore}/100 | Accroche ${analysis.hookScore}/100 | Rythme ${analysis.rhythmScore}/100 | Visuels ${analysis.visualsScore}/100")
            appendLine("Résumé : ${analysis.summary}")
            appendLine("Accroche : ${analysis.hookAnalysis}")
            appendLine("Rythme : ${analysis.pacingAnalysis}")
            appendLine("Visuels : ${analysis.visualsAnalysis}")
            appendLine()
            appendLine("Question de l'utilisateur : $question")
            appendLine("Réponds précisément en français sans inventer de statistiques externes.")
        }
        return askAssistant(contextPrompt, AiChatMode.ASSISTANT, emptyList())
    }

    private sealed interface VideoPayload {
        data class InlineBytes(val bytes: ByteArray, val mime: String) : VideoPayload
        data class KeyFrames(val frames: List<Bitmap>) : VideoPayload
        data object UrlOnly : VideoPayload
    }

    private companion object {
        const val TAG = "FirebaseAiRepository"
        const val AI_TIMEOUT_MS = 60_000L
        const val MAX_INLINE_VIDEO_BYTES = 18 * 1024 * 1024
    }
}
