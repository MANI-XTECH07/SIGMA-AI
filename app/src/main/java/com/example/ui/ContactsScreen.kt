package com.example.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.device.ContactMatch
import com.example.device.DeviceController
import com.example.ui.components.SigmaGlassCard
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaSurfaceBlack
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaWhite

@Composable
fun ContactsScreen(
    deviceController: DeviceController,
    onCallContact: (String) -> Unit,
    onSmsContact: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<ContactMatch>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery) {
        isLoading = true
        contacts = deviceController.searchContacts(searchQuery)
        isLoading = false
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
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    text = "CONTACTS",
                    color = SigmaWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Direct dialing & messaging integration",
                    color = SigmaNeonRed,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search contact name...", color = SigmaTextSecondary, fontSize = 13.sp) },
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
                Text("Querying device contacts...", color = SigmaTextSecondary)
            }
        } else if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isBlank()) "No contacts detected. Please grant Contacts permission in Settings."
                    else "No contacts found matching \"$searchQuery\"",
                    color = SigmaTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(contacts, key = { it.name + it.phoneNumber }) { contact ->
                    ContactItemRow(
                        contact = contact,
                        onCall = { onCallContact(contact.phoneNumber) },
                        onSms = { onSmsContact(contact.phoneNumber) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactItemRow(
    contact: ContactMatch,
    onCall: () -> Unit,
    onSms: () -> Unit
) {
    SigmaGlassCard(
        modifier = Modifier.fillMaxWidth()
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
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22080E))
                        .border(1.dp, SigmaNeonRed.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = SigmaNeonRedBright,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = contact.name,
                        color = SigmaWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = contact.phoneNumber,
                        color = SigmaTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSms, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "SMS",
                        tint = SigmaWhite
                    )
                }
                IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = SigmaNeonRedBright
                    )
                }
            }
        }
    }
}
