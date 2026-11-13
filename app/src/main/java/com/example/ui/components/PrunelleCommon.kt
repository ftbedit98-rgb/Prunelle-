package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.network.NetworkStatus
import com.example.data.local.ReadStatus
import com.example.ui.theme.OfflineAmber
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.ReadBlue
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PrunelleAvatar(
    name: String,
    avatarUri: String?,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    isGroup: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size)) {
        if (!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            val initials = name.trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifEmpty { "P" }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (isGroup) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(size * 0.52f)
                    )
                } else {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(OnlineEmerald)
            )
        }
    }
}

@Composable
fun ReadReceiptTicks(
    status: ReadStatus,
    modifier: Modifier = Modifier
) {
    when (status) {
        ReadStatus.PENDING -> Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = "En attente de synchronisation",
            tint = OfflineAmber,
            modifier = modifier.size(15.dp)
        )
        ReadStatus.SENT -> Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Envoyé",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(15.dp)
        )
        ReadStatus.DELIVERED -> Icon(
            imageVector = Icons.Rounded.DoneAll,
            contentDescription = "Livré",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(15.dp)
        )
        ReadStatus.READ -> Icon(
            imageVector = Icons.Rounded.DoneAll,
            contentDescription = "Lu",
            tint = ReadBlue,
            modifier = modifier.size(15.dp)
        )
    }
}

@Composable
fun NetworkStatusBanner(
    status: NetworkStatus,
    pendingSyncCount: Int,
    modifier: Modifier = Modifier
) {
    val bg = when {
        !status.isOnline -> OfflineAmber.copy(alpha = 0.16f)
        pendingSyncCount > 0 -> MaterialTheme.colorScheme.primaryContainer
        else -> OnlineEmerald.copy(alpha = 0.12f)
    }
    val fg = when {
        !status.isOnline -> OfflineAmber
        pendingSyncCount > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> OnlineEmerald
    }
    val text = when {
        !status.isOnline && pendingSyncCount > 0 -> "Hors ligne • $pendingSyncCount en attente"
        !status.isOnline -> "Hors ligne • Mode local"
        pendingSyncCount > 0 -> "Synchronisation ($pendingSyncCount)..."
        else -> "En ligne (${status.transportLabel})"
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (status.isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                contentDescription = text,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FirebaseRequiredBanner(
    notice: String = "Mode local actif — Activez Firebase dans AI Studio pour les réponses Gemini, la génération IA et la sync cloud.",
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

fun formatTimeShort(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
