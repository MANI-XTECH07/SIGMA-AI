package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.apps.AppItem
import com.example.apps.InstalledAppRepository
import com.example.ui.components.SigmaGlassCard
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaSurfaceBlack
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaWhite

@Composable
fun AppControlScreen(
    appRepository: InstalledAppRepository,
    onLaunchApp: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun refreshApps() {
        isLoading = true
        apps = appRepository.getInstalledApps(forceRefresh = true)
        isLoading = false
    }

    LaunchedEffect(Unit) {
        apps = appRepository.getInstalledApps(forceRefresh = false)
        isLoading = false
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SigmaBlack)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SigmaWhite
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "APP CONTROL",
                        color = SigmaWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "${filteredApps.size} Launchable Apps Detected",
                        color = SigmaNeonRed,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(onClick = { refreshApps() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = SigmaNeonRed
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps or packages...", color = SigmaTextSecondary, fontSize = 13.sp) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = SigmaNeonRed)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SigmaSurfaceBlack)
                .border(1.dp, SigmaNeonRed.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SigmaNeonRed,
                unfocusedBorderColor = SigmaNeonRed.copy(alpha = 0.35f),
                focusedTextColor = SigmaWhite,
                unfocusedTextColor = SigmaWhite,
                cursorColor = SigmaNeonRed
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Scanning installed applications...", color = SigmaTextSecondary)
            }
        } else if (filteredApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No applications found matching \"$searchQuery\"", color = SigmaTextSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppItemRow(
                        app = app,
                        onLaunch = { onLaunchApp(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItemRow(
    app: AppItem,
    onLaunch: () -> Unit
) {
    val iconBitmap: Bitmap? = remember(app.packageName) {
        try {
            app.icon?.toBitmap(width = 96, height = 96)
        } catch (_: Exception) {
            null
        }
    }

    SigmaGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLaunch)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SigmaSurfaceBlack)
                            .border(1.dp, SigmaNeonRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.label.take(1).uppercase(),
                            color = SigmaNeonRedBright,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = app.label,
                        color = SigmaWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = app.packageName,
                        color = SigmaTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            IconButton(onClick = onLaunch, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Launch",
                    tint = SigmaNeonRedBright
                )
            }
        }
    }
}
