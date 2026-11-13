package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.core.birthday.BirthdayHelper
import com.example.core.media.MediaStorageHelper
import com.example.ui.components.PrunelleAvatar
import com.example.ui.viewmodel.BirthdayUiState
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BirthdayCelebrationDialog(
    displayName: String,
    avatarUri: String?,
    birthdayState: BirthdayUiState,
    onRetryAiCard: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val infinite = rememberInfiniteTransition(label = "birthdayConfetti")
    val animPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confettiPhase"
    )

    val confettiColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("birthday_celebration_dialog")
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Celebratory floating confetti animation overlay (background decorative layer)
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                ) {
                    val count = 22
                    for (i in 0 until count) {
                        val seed = i * 0.6180339f
                        val xFrac = ((seed * 1.7f) + 0.06f * sin((animPhase * 6.28f) + i)) % 1f
                        val yFrac = ((i / count.toFloat()) + animPhase) % 1f
                        val x = (if (xFrac < 0f) xFrac + 1f else xFrac) * size.width
                        val y = yFrac * size.height
                        val radius = (3.dp + ((i % 3) * 1.5).dp).toPx()
                        val sway = cos((animPhase * 6.28f) + i * 0.9f) * 6.dp.toPx()
                        drawCircle(
                            color = confettiColors[i % confettiColors.size].copy(alpha = 0.28f),
                            radius = radius,
                            center = Offset(x + sway, y)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cake,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Joyeux anniversaire ${displayName.ifBlank { "" }}".trim(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    val cardPath = birthdayState.birthdayCardPath
                    val hasRealCardFile = !cardPath.isNullOrBlank() && File(cardPath).exists()

                    when {
                        birthdayState.birthdayCardLoading -> {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Préparation de ta carte Prunelle IA...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        hasRealCardFile -> {
                            AsyncImage(
                                model = File(cardPath!!),
                                contentDescription = "Carte d'anniversaire générée par IA",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .testTag("birthday_ai_card_image")
                            )
                        }

                        else -> {
                            // Never fabricate a fake portrait illustration; show user's real avatar & explicit retry if error
                            PrunelleAvatar(
                                name = displayName.ifBlank { "Moi" },
                                avatarUri = avatarUri,
                                size = 96.dp
                            )
                            birthdayState.birthdayCardError?.let { err ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Carte portrait IA indisponible : $err",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            textAlign = TextAlign.Center
                                        )
                                        TextButton(onClick = onRetryAiCard) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Réessayer la carte IA")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = BirthdayHelper.SELF_BIRTHDAY_MESSAGE,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("dismiss_birthday_dialog")
                        ) {
                            Text("Fermer")
                        }
                        Button(
                            onClick = {
                                if (hasRealCardFile) {
                                    MediaStorageHelper.shareImageFile(
                                        context = context,
                                        filePath = cardPath!!,
                                        chooserTitle = "Partager ma carte d'anniversaire Prunelle",
                                        textCaption = BirthdayHelper.SELF_BIRTHDAY_MESSAGE
                                    )
                                } else {
                                    MediaStorageHelper.shareFormattedText(
                                        context = context,
                                        title = "Joyeux anniversaire Prunelle",
                                        text = BirthdayHelper.SELF_BIRTHDAY_MESSAGE
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .heightIn(min = 48.dp)
                                .testTag("share_birthday_card_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Partager ma carte")
                        }
                    }
                }
            }
        }
    }
}
