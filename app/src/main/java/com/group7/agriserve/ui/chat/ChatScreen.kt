package com.group7.agriserve.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AgriServeGreen = Color(0xFF2E7D32)
private val AgriServeLightGreen = Color(0xFFE8F5E9)
private val AgriServeDarkGreen = Color(0xFF1B5E20)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Automatically scroll to the bottom when new message arrives
    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    val suggestionChips = listOf(
        "🌱 How to Request Seeds",
        "🛡️ How to Verify Account",
        "⚠️ How to Report an Incident",
        "📍 How to Tag My Farm",
        "🌾 Available Seeds Stock",
        "📦 Track My Request Status",
        "🌦️ Check Weather Forecast",
        "🏪 Bantay Presyo / Markets",
        "👥 Farmer Community",
        "📞 Contact DA Support"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AgriServeLightGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = AgriServeGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "AgriServe AI Support",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                                Text(
                                    text = "Online • 24/7 Support Agent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Chat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
            ) {
                // Quick Suggestion Chips (Horizontally Scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestionChips.forEach { chipText ->
                        SuggestionChip(
                            onClick = {
                                viewModel.sendMessage(chipText)
                            },
                            label = {
                                Text(
                                    text = chipText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            )
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Sticky Bottom Input Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = "Ask how to report, verify, request seeds, etc...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        shape = RoundedCornerShape(26.dp),
                        maxLines = 4,
                        enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() && !uiState.isLoading) {
                                    val textToSend = inputText
                                    inputText = ""
                                    viewModel.sendMessage(textToSend)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgriServeGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !uiState.isLoading) {
                                val textToSend = inputText
                                inputText = ""
                                viewModel.sendMessage(textToSend)
                            }
                        },
                        enabled = inputText.isNotBlank() && !uiState.isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank() && !uiState.isLoading) AgriServeGreen
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ChatMessageBubble(
                        message = message,
                        onNavigate = onNavigate
                    )
                }

                if (uiState.isLoading) {
                    item {
                        ThinkingBubble()
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onNavigate: (String) -> Unit = {}
) {
    val isUser = message.sender == MessageSender.USER
    val isSystem = message.sender == MessageSender.SYSTEM
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    val alignment = when {
        isUser -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    val bubbleShape = when {
        isUser -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 2.dp)
        else -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 2.dp, bottomEnd = 18.dp)
    }

    val containerColor = when {
        isUser -> AgriServeGreen
        isSystem && message.isError -> MaterialTheme.colorScheme.errorContainer
        isSystem -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isUser -> Color.White
        isSystem && message.isError -> MaterialTheme.colorScheme.onErrorContainer
        isSystem -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.94f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(AgriServeLightGreen)
                        .align(Alignment.Bottom),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSystem) Icons.Default.Info else Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = AgriServeGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Column(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(containerColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp
                    ),
                    color = contentColor
                )

                // Clickable Navigation Action Button inside/below the assistant's message
                message.navAction?.let { action ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onNavigate(action.route) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AgriServeGreen,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        val iconVector = when (action.actionType) {
                            NavActionType.REQUEST -> Icons.Default.AddCircle
                            NavActionType.INVENTORY -> Icons.Default.Inventory
                            NavActionType.STATUS -> Icons.AutoMirrored.Filled.Assignment
                            NavActionType.VERIFY -> Icons.Default.VerifiedUser
                            NavActionType.REPORT -> Icons.Default.Report
                            NavActionType.WEATHER -> Icons.Default.WbSunny
                            NavActionType.FARM_TAGGING -> Icons.Default.AddLocationAlt
                            NavActionType.FARMS_MAP -> Icons.Default.Map
                            NavActionType.MARKETS -> Icons.Default.Storefront
                            NavActionType.COMMUNITY -> Icons.Default.Groups
                            NavActionType.PROFILE -> Icons.Default.Person
                            NavActionType.FEEDBACK -> Icons.Default.Feedback
                            NavActionType.SUPPORT -> Icons.Default.SupportAgent
                        }

                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Navigate",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) Color.White.copy(alpha = 0.7f) else contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.align(if (isUser) Alignment.End else Alignment.Start)
                )
            }
        }
    }
}

@Composable
fun ThinkingBubble() {
    Row(
        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(AgriServeLightGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = AgriServeGreen,
                modifier = Modifier.size(18.dp)
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AgriServeGreen
                )
                Text(
                    text = "Checking guidelines & records...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
