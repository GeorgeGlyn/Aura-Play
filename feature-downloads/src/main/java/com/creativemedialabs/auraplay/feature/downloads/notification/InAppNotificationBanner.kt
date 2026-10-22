package com.creativemedialabs.auraplay.feature.downloads.notification

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun InAppNotificationBanner(
    modifier: Modifier = Modifier
) {
    val notification by InAppNotificationManager.notification.collectAsState()

    LaunchedEffect(notification?.id) {
        val n = notification ?: return@LaunchedEffect
        delay(n.durationMs)
        if (InAppNotificationManager.notification.value?.id == n.id) {
            InAppNotificationManager.dismiss()
        }
    }

    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val current = notification ?: return@AnimatedVisibility

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF2161616),
            border = BorderStroke(
                1.dp,
                if (current.type == InAppNotificationType.DOWNLOAD_COMPLETED)
                    Color(0x884CAF50)
                else
                    Color(0x55E50914)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(12.dp, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon pill
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (current.type == InAppNotificationType.DOWNLOAD_COMPLETED)
                                Color(0x334CAF50)
                            else
                                Color(0x33E50914)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (current.type == InAppNotificationType.DOWNLOAD_COMPLETED)
                            Icons.Default.CheckCircle
                        else
                            Icons.Default.Download,
                        contentDescription = null,
                        tint = if (current.type == InAppNotificationType.DOWNLOAD_COMPLETED)
                            Color(0xFF4CAF50)
                        else
                            Color(0xFFE50914),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Text details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = current.message,
                        color = Color(0xFFCCCCCC),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action button (e.g. Watch Now)
                if (current.actionLabel != null && current.onAction != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            current.onAction.invoke()
                            InAppNotificationManager.dismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = current.actionLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { InAppNotificationManager.dismiss() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
