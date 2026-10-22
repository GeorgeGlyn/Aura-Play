package com.creativemedialabs.auraplay.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class TvNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Search : TvNavItem("search", "Search", Icons.Default.Search)
    object Home : TvNavItem("home", "Home", Icons.Default.Home)
    object HotAndNew : TvNavItem("hot", "New & Hot", Icons.Default.Whatshot)
    object Downloads : TvNavItem("downloads", "Downloads", Icons.Default.Download)
}

@Composable
fun TvNavigationRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        TvNavItem.Search,
        TvNavItem.Home,
        TvNavItem.HotAndNew,
        TvNavItem.Downloads
    )

    // Track whether any item in the rail is currently focused by D-pad
    var isRailFocused by remember { mutableStateOf(false) }

    val railWidth by animateDpAsState(
        targetValue = if (isRailFocused) 180.dp else 68.dp,
        label = "rail_width"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xF5080808),
                        Color(0xE0101010),
                        Color(0xCC080808)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x08FFFFFF))),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App Logo at Top
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22E50914))
                    .border(1.dp, Color(0x66E50914), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    color = Color(0xFFE50914),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }

            AnimatedVisibility(visible = isRailFocused) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AURA PLAY",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Center Navigation Items
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                LaunchedEffect(isFocused) {
                    if (isFocused) {
                        isRailFocused = true
                    }
                }

                TvRailItemView(
                    item = item,
                    isSelected = isSelected,
                    isFocused = isFocused,
                    isExpanded = isRailFocused,
                    interactionSource = interactionSource,
                    onClick = {
                        onNavigate(item.route)
                    }
                )
            }
        }

        // Bottom Placeholder / Device Info
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0x18FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "TV",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TvRailItemView(
    item: TvNavItem,
    isSelected: Boolean,
    isFocused: Boolean,
    isExpanded: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
) {
    val scale = if (isFocused) 1.08f else 1f
    val backgroundColor = when {
        isFocused -> Color(0x33E50914)
        isSelected -> Color(0x18FFFFFF)
        else -> Color.Transparent
    }
    val borderColor = when {
        isFocused -> Color(0xFFE50914)
        isSelected -> Color(0x33FFFFFF)
        else -> Color.Transparent
    }
    val iconTint = when {
        isFocused -> Color(0xFFE50914)
        isSelected -> Color.White
        else -> Color(0xFF777777)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(width = if (isFocused) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = item.label,
                    color = if (isFocused || isSelected) Color.White else Color(0xFFAAAAAA),
                    fontSize = 13.sp,
                    fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
