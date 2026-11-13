package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.media.MediaStorageHelper
import com.example.data.local.TikTokAnalysisEntity
import com.example.ui.components.AnalysisDashboardSkeleton
import com.example.ui.components.FirebaseRequiredBanner
import com.example.ui.components.formatTimeShort
import com.example.ui.viewmodel.AiHubUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TikTokAiScreen(
    state: AiHubUiState,
    onAnalyze: (String?, String?) -> Unit,
    onSelectAnalysis: (String) -> Unit,
    onAskQuestion: (TikTokAnalysisEntity, String) -> Unit,
    onDeleteAnalysis: (String) -> Unit,
    onDismissError: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    var tiktokUrl by remember { mutableStateOf("") }
    var localVideoUri by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var followUpQuestion by remember { mutableStateOf("") }
    var isExportingImage by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            localVideoUri = uri.toString()
            urlError = null
        }
    }

    val selectedAnalysis = state.tikTokAnalyses.firstOrNull { it.id == state.selectedTikTokAnalysisId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Prominent Minimalist Header
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tiktok_ai_header")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "100% Gratuit • Sans abonnement",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Analyse vidéo IA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = "Prunelle TikTok AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Évaluez votre accroche, votre rythme de montage et la qualité visuelle avant publication.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!state.isCloudAiEnabled) {
            FirebaseRequiredBanner(
                notice = "Prunelle TikTok AI (100% gratuit) : l'analyse multimodale réelle nécessite Firebase AI Logic. En mode local, un rapport d'exemple transparent est enregistré dans votre historique Room sans statistiques inventées."
            )
        }

        state.aiError?.let { errorText ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tiktok_ai_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissError) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Fermer l'alerte",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Input Card: Link Field + Video File Selection CTA + Analyze CTA
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = tiktokUrl,
                    onValueChange = {
                        tiktokUrl = it
                        urlError = null
                    },
                    label = { Text("Lien TikTok public") },
                    placeholder = { Text("https://www.tiktok.com/@createur/video/...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Link, contentDescription = null)
                    },
                    trailingIcon = {
                        if (tiktokUrl.isNotEmpty()) {
                            IconButton(onClick = {
                                tiktokUrl = ""
                                urlError = null
                            }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Effacer le lien")
                            }
                        }
                    },
                    singleLine = true,
                    isError = urlError != null,
                    supportingText = urlError?.let { { Text(it) } },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tiktok_url_input")
                )

                // Prominent CTA Button for Video File Selection
                FilledTonalButton(
                    onClick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("tiktok_import_video_button")
                ) {
                    Icon(
                        imageVector = if (localVideoUri == null) Icons.Rounded.VideoLibrary else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (localVideoUri == null) "Sélectionner une vidéo depuis la galerie"
                        else "Vidéo locale sélectionnée ✓ (changer)"
                    )
                }

                Button(
                    onClick = {
                        val cleanUrl = tiktokUrl.trim()
                        if (cleanUrl.isNotEmpty() && !cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                            urlError = "Lien invalide. Collez une URL https:// ou importez une vidéo."
                        } else {
                            onAnalyze(cleanUrl.ifEmpty { null }, localVideoUri)
                        }
                    },
                    enabled = (tiktokUrl.isNotBlank() || localVideoUri != null) && !state.isAnalyzingTikTok,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("tiktok_analyze_button")
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyser une vidéo", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (state.isAnalyzingTikTok) {
            AnalysisDashboardSkeleton()
        }

        // Room-Persisted History Selector
        if (state.tikTokAnalyses.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Historique des analyses (${state.tikTokAnalyses.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Sauvegardé localement",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("tiktok_history_row")
                ) {
                    items(state.tikTokAnalyses, key = { it.id }) { item ->
                        val isSelected = item.id == selectedAnalysis?.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectAnalysis(item.id) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "${item.overallScore}/100 • ${item.sourceLabel.take(20)} • ${formatTimeShort(item.createdAt)}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }

        // Result Dashboard UI
        selectedAnalysis?.let { report ->
            val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()
            val bgColorArgb = MaterialTheme.colorScheme.surface.toArgb()
            val textColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
            val mutedColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

            ScoreHeroCard(
                report = report,
                isExportingImage = isExportingImage,
                onShareText = {
                    MediaStorageHelper.shareFormattedText(
                        context = context,
                        title = "Analyse Prunelle TikTok AI — ${report.overallScore}/100",
                        text = formatTikTokShareText(report)
                    )
                },
                onShareImage = {
                    if (!isExportingImage) {
                        isExportingImage = true
                        scope.launch {
                            val savedPath = withContext(Dispatchers.Default) {
                                val bmp = renderTikTokSummaryBitmap(
                                    report = report,
                                    isDark = isDark,
                                    primaryArgb = primaryColorArgb,
                                    bgArgb = bgColorArgb,
                                    textArgb = textColorArgb,
                                    mutedArgb = mutedColorArgb
                                )
                                MediaStorageHelper.saveBitmapToInternalPng(
                                    context = context,
                                    bitmap = bmp,
                                    subdir = "media_cache",
                                    fileName = "tiktok_report_${report.id.take(8)}.png"
                                )
                            }
                            isExportingImage = false
                            if (savedPath != null) {
                                MediaStorageHelper.shareImageFile(
                                    context = context,
                                    filePath = savedPath,
                                    chooserTitle = "Partager le tableau de bord TikTok AI",
                                    textCaption = "Score Prunelle TikTok AI : ${report.overallScore}/100 (${report.sourceLabel})"
                                )
                            }
                        }
                    }
                },
                onDelete = { onDeleteAnalysis(report.id) }
            )

            // Segmented Analysis Cards: Hook, Rhythm, Visuals
            Text(
                text = "Analyse segmentée",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val visualsText = report.visualsAnalysis.ifBlank {
                "Cadrage vertical (${report.resolutionFormatted}), contraste des sous-titres et lisibilité mobile."
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 600.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SegmentedAnalysisCard(
                            title = "Accroche",
                            subtitle = "0–3 secondes",
                            score = report.hookScore,
                            description = report.hookAnalysis,
                            icon = Icons.Rounded.Bolt,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("segment_card_hook")
                        )
                        SegmentedAnalysisCard(
                            title = "Rythme",
                            subtitle = "Montage & tempo",
                            score = report.rhythmScore,
                            description = report.pacingAnalysis,
                            icon = Icons.Rounded.Speed,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("segment_card_rhythm")
                        )
                        SegmentedAnalysisCard(
                            title = "Visuels",
                            subtitle = "Clarté & texte",
                            score = report.visualsScore,
                            description = visualsText,
                            icon = Icons.Rounded.Visibility,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("segment_card_visuals")
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SegmentedAnalysisCard(
                            title = "Accroche (Hook)",
                            subtitle = "Premières 3 secondes",
                            score = report.hookScore,
                            description = report.hookAnalysis,
                            icon = Icons.Rounded.Bolt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("segment_card_hook")
                        )
                        SegmentedAnalysisCard(
                            title = "Rythme (Rhythm)",
                            subtitle = "Montage & transitions",
                            score = report.rhythmScore,
                            description = report.pacingAnalysis,
                            icon = Icons.Rounded.Speed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("segment_card_rhythm")
                        )
                        SegmentedAnalysisCard(
                            title = "Visuels (Visuals)",
                            subtitle = "Cadrage, lumière & sous-titres",
                            score = report.visualsScore,
                            description = visualsText,
                            icon = Icons.Rounded.Visibility,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("segment_card_visuals")
                        )
                    }
                }
            }

            // Pacing Curve Compose Canvas Visualization (adapts to dark/light system theme)
            TikTokPacingChart(
                pacingCurveCsv = report.pacingCurveCsv,
                hookScore = report.hookScore,
                rhythmScore = report.rhythmScore,
                visualsScore = report.visualsScore
            )

            // Summary Section for Improvement Tips
            ImprovementSummaryCard(report = report)

            // Interactive Follow-up AI Assistant
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Assistant interactif sur cette vidéo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.selectedTikTokQa.forEach { qa ->
                        Surface(
                            color = if (qa.isFromUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = qa.content,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = followUpQuestion,
                            onValueChange = { followUpQuestion = it },
                            placeholder = { Text("Ex : Propose-moi 3 autres accroches...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                onAskQuestion(report, followUpQuestion)
                                followUpQuestion = ""
                            },
                            enabled = followUpQuestion.isNotBlank(),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Poser la question")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreHeroCard(
    report: TikTokAnalysisEntity,
    isExportingImage: Boolean,
    onShareText: () -> Unit,
    onShareImage: () -> Unit,
    onDelete: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (report.overallScore.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 700),
        label = "scoreProgress"
    )
    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
    val arcColor = MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tiktok_score_dashboard")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(92.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10.dp.toPx()
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = arcColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${report.overallScore}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "/ 100",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Score global de potentiel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = report.sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "Durée : ${report.durationFormatted} • ${report.resolutionFormatted}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Supprimer l'analyse",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f))

            Text(
                text = report.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onShareImage,
                    enabled = !isExportingImage,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("tiktok_share_png_button")
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isExportingImage) "Export..." else "Partager PNG")
                }
                OutlinedButton(
                    onClick = onShareText,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("tiktok_share_text_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Partager texte")
                }
            }
        }
    }
}

@Composable
private fun TikTokPacingChart(
    pacingCurveCsv: String,
    hookScore: Int,
    rhythmScore: Int,
    visualsScore: Int
) {
    val points = remember(pacingCurveCsv) {
        pacingCurveCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull()?.coerceIn(0, 100) }
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val fillStartColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nodeBgColor = MaterialTheme.colorScheme.surface

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tiktok_pacing_chart")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Courbe d'évolution du rythme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "0s → Fin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (points.size >= 2) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    val leftPad = 12.dp.toPx()
                    val rightPad = 12.dp.toPx()
                    val topPad = 14.dp.toPx()
                    val bottomPad = 14.dp.toPx()
                    val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    // Reference grid lines (25 / 50 / 75 / 100)
                    listOf(0.25f, 0.5f, 0.75f, 1f).forEach { lvl ->
                        val y = topPad + chartH * (1f - lvl)
                        drawLine(
                            color = gridColor.copy(alpha = 0.55f),
                            start = Offset(leftPad, y),
                            end = Offset(leftPad + chartW, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val coords = points.mapIndexed { idx, value ->
                        val x = leftPad + (idx / (points.size - 1).toFloat()) * chartW
                        val y = topPad + (1f - (value / 100f)) * chartH
                        Offset(x, y)
                    }

                    val linePath = Path().apply {
                        coords.forEachIndexed { idx, pt ->
                            if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(coords.last().x, topPad + chartH)
                        lineTo(coords.first().x, topPad + chartH)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(fillStartColor, Color.Transparent),
                            startY = topPad,
                            endY = topPad + chartH
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    coords.forEach { pt ->
                        drawCircle(color = lineColor, radius = 5.dp.toPx(), center = pt)
                        drawCircle(color = nodeBgColor, radius = 2.5.dp.toPx(), center = pt)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    points.forEachIndexed { idx, score ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$score",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "S${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Aucune donnée de courbe segmentée retournée pour cette analyse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Accroche : $hookScore/100", style = MaterialTheme.typography.labelMedium)
                Text("Rythme : $rhythmScore/100", style = MaterialTheme.typography.labelMedium)
                Text("Visuels : $visualsScore/100", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun formatTikTokShareText(report: TikTokAnalysisEntity): String {
    val strengths = report.strengthsCsv.split("||").filter { it.isNotBlank() }
    val fixes = report.priorityFixesCsv.split("||").filter { it.isNotBlank() }
    return buildString {
        appendLine("Prunelle TikTok AI — Rapport d'analyse")
        appendLine("Source : ${report.sourceLabel} (${report.durationFormatted}, ${report.resolutionFormatted})")
        appendLine("Score global : ${report.overallScore}/100")
        appendLine("• Accroche : ${report.hookScore}/100")
        appendLine("• Rythme : ${report.rhythmScore}/100")
        appendLine("• Visuels : ${report.visualsScore}/100")
        appendLine()
        appendLine("Résumé : ${report.summary}")
        if (strengths.isNotEmpty()) {
            appendLine()
            appendLine("Points forts :")
            strengths.forEach { appendLine("✓ $it") }
        }
        if (fixes.isNotEmpty()) {
            appendLine()
            appendLine("Priorités :")
            fixes.forEach { appendLine("→ $it") }
        }
        if (report.hashtagsCsv.isNotBlank()) {
            appendLine()
            appendLine("Hashtags : ${report.hashtagsCsv}")
        }
    }
}

private fun renderTikTokSummaryBitmap(
    report: TikTokAnalysisEntity,
    isDark: Boolean,
    primaryArgb: Int,
    bgArgb: Int,
    textArgb: Int,
    mutedArgb: Int
): Bitmap {
    val width = 1080
    val height = 1080
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(bgArgb)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryArgb
        textSize = 54f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryArgb
        textSize = 112f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textArgb
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textArgb
        textSize = 30f
    }
    val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedArgb
        textSize = 28f
    }
    val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDark) 0xFF332D36.toInt() else 0xFFE9E0EC.toInt()
    }
    val barFgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryArgb
    }

    canvas.drawText("Prunelle TikTok AI", 72f, 110f, titlePaint)
    canvas.drawText("${report.sourceLabel} • ${report.durationFormatted}", 72f, 162f, mutedPaint)
    canvas.drawText("${report.overallScore}/100", 72f, 300f, scorePaint)
    canvas.drawText("Potentiel global analysé", 72f, 355f, headingPaint)

    val metrics = listOf(
        "Accroche (0–3s)" to report.hookScore,
        "Rythme & Montage" to report.rhythmScore,
        "Visuels & Sous-titres" to report.visualsScore
    )
    var y = 450f
    metrics.forEach { (label, value) ->
        canvas.drawText("$label : $value/100", 72f, y, bodyPaint)
        y += 22f
        canvas.drawRoundRect(72f, y, 1008f, y + 26f, 13f, 13f, barBgPaint)
        val fillRight = 72f + (936f * (value.coerceIn(0, 100) / 100f))
        canvas.drawRoundRect(72f, y, fillRight, y + 26f, 13f, 13f, barFgPaint)
        y += 82f
    }

    canvas.drawText("Priorité principale :", 72f, 800f, headingPaint)
    val firstFix = report.priorityFixesCsv.split("||").firstOrNull { it.isNotBlank() } ?: report.summary
    val clipped = if (firstFix.length > 140) firstFix.take(137) + "..." else firstFix
    val line1 = clipped.take(54)
    val line2 = clipped.drop(54).take(54)
    canvas.drawText(line1, 72f, 855f, bodyPaint)
    if (line2.isNotBlank()) canvas.drawText(line2, 72f, 898f, bodyPaint)
    canvas.drawText(report.hashtagsCsv.take(65), 72f, 995f, mutedPaint)
    return bitmap
}

@Composable
private fun SegmentedAnalysisCard(
    title: String,
    subtitle: String,
    score: Int,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$score/100",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { score.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ImprovementSummaryCard(report: TikTokAnalysisEntity) {
    val strengths = remember(report.strengthsCsv) {
        report.strengthsCsv.split("||").filter { it.isNotBlank() }
    }
    val weaknesses = remember(report.weaknessesCsv) {
        report.weaknessesCsv.split("||").filter { it.isNotBlank() }
    }
    val priorityFixes = remember(report.priorityFixesCsv) {
        report.priorityFixesCsv.split("||").filter { it.isNotBlank() }
    }
    val retentionTips = remember(report.retentionTipsCsv) {
        report.retentionTipsCsv.split("||").filter { it.isNotBlank() }
    }
    val titles = remember(report.suggestedTitlesCsv) {
        report.suggestedTitlesCsv.split("||").filter { it.isNotBlank() }
    }
    val descriptions = remember(report.suggestedDescriptionsCsv) {
        report.suggestedDescriptionsCsv.split("||").filter { it.isNotBlank() }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tiktok_improvement_summary")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.TipsAndUpdates,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Résumé & Conseils d'amélioration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (priorityFixes.isNotEmpty()) {
                TipGroup("Corrections prioritaires", priorityFixes)
            }
            if (strengths.isNotEmpty()) {
                TipGroup("Points forts", strengths)
            }
            if (weaknesses.isNotEmpty()) {
                TipGroup("Points à renforcer", weaknesses)
            }
            if (retentionTips.isNotEmpty()) {
                TipGroup("Conseils de rétention", retentionTips)
            }
            if (titles.isNotEmpty() || descriptions.isNotEmpty() || report.hashtagsCsv.isNotBlank()) {
                TipGroup(
                    "Idées de titres, descriptions & hashtags",
                    titles + descriptions + listOfNotNull(report.hashtagsCsv.takeIf { it.isNotBlank() })
                )
            }
        }
    }
}

@Composable
private fun TipGroup(heading: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        items.forEach { item ->
            Text(
                text = "• $item",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
