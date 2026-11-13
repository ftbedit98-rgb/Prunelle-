package com.example.data.repository

import android.content.Context
import com.example.data.local.TikTokAiDao
import com.example.data.local.TikTokAnalysisEntity
import com.example.data.local.TikTokQaEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Local Room-backed repository storing the history of Prunelle TikTok AI video analysis reports
 * and their follow-up Q&A threads so users can revisit past analyses anytime.
 */
class TikTokAnalysisRepository(
    private val tikTokAiDao: TikTokAiDao,
    private val aiRepository: AiRepository
) {
    fun observeHistory(): Flow<List<TikTokAnalysisEntity>> =
        tikTokAiDao.observeAnalyses()

    fun observeReportById(id: String): Flow<TikTokAnalysisEntity?> =
        tikTokAiDao.observeAnalysisById(id)

    fun observeFollowUpQa(analysisId: String): Flow<List<TikTokQaEntity>> =
        tikTokAiDao.observeQa(analysisId)

    suspend fun analyzeAndSave(
        context: Context,
        tiktokUrl: String?,
        localVideoUri: String?
    ): TikTokAnalysisEntity {
        val report = aiRepository.analyzeTikTokVideo(
            context = context,
            tiktokUrl = tiktokUrl,
            localVideoUri = localVideoUri
        )
        tikTokAiDao.insertAnalysis(report)
        return report
    }

    suspend fun saveReport(report: TikTokAnalysisEntity) {
        tikTokAiDao.insertAnalysis(report)
    }

    suspend fun deleteReport(id: String) {
        tikTokAiDao.deleteAnalysis(id)
    }

    suspend fun askFollowUp(
        report: TikTokAnalysisEntity,
        question: String
    ): AiReplyResult {
        val cleanQuestion = question.trim()
        tikTokAiDao.insertQa(
            TikTokQaEntity(
                id = UUID.randomUUID().toString(),
                analysisId = report.id,
                isFromUser = true,
                content = cleanQuestion
            )
        )
        val reply = aiRepository.askAboutTikTokAnalysis(report, cleanQuestion)
        tikTokAiDao.insertQa(
            TikTokQaEntity(
                id = UUID.randomUUID().toString(),
                analysisId = report.id,
                isFromUser = false,
                content = reply.text
            )
        )
        return reply
    }
}
