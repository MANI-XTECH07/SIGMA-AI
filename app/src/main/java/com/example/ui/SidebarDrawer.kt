package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaWhite

@Composable
fun SidebarDrawer(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Color(0xFF070406))
            .border(width = 1.dp, color = Color(0xFF260A10), shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP AVATAR & TITLE
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, SigmaNeonRed, RoundedCornerShape(14.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sigma_app_icon),
                        contentDescription = "SIGMA",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SIGMA",
                        color = SigmaWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "AI ASSISTANT",
                        color = SigmaNeonRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // NAVIGATION ITEMS (Mockup matching)
            val menuItems = listOf(
                Triple("HOME", "Home", Icons.Default.Home),
                Triple("HISTORY", "Chat History", Icons.AutoMirrored.Filled.Chat),
                Triple("APPS", "App Control", Icons.Default.Apps),
                Triple("AUTOMATION", "Automation", Icons.Default.SmartToy),
                Triple("SCREEN", "Screen Analyzer", Icons.Default.ScreenSearchDesktop),
                Triple("DIAGNOSTICS", "Diagnostics", Icons.Default.SmartToy),
                Triple("CONTACTS", "Contacts", Icons.Default.ContactPhone),
                Triple("SETTINGS", "Settings", Icons.Default.Settings)
            )

            menuItems.forEach { (route, label, icon) ->
                val isSelected = selectedRoute == route
                DrawerItemRow(
                    label = label,
                    icon = icon,
                    isSelected = isSelected,
                    onClick = {
                        onNavigate(route)
                        onCloseDrawer()
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // BOTTOM QUOTE CARD WITH ANIME CHARACTER SILHOUETTE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF14070B))
                .border(1.dp, SigmaNeonRed.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "\"Better than yesterday.\"",
                    color = SigmaWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "- SIGMA",
                    color = SigmaNeonRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerItemRow(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Color(0xFF22080E) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) SigmaNeonRed.copy(alpha = 0.6f) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) SigmaNeonRed else Color(0xFFCCCCCC),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = label,
                    color = if (isSelected) SigmaWhite else Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (isSelected) SigmaNeonRed else Color(0xFF555555),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
