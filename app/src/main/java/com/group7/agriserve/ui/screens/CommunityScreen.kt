package com.group7.agriserve.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.group7.agriserve.viewmodel.Comment
import com.group7.agriserve.viewmodel.CommunityViewModel
import com.group7.agriserve.viewmodel.Post
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    onBack: () -> Unit,
    userName: String,
    viewModel: CommunityViewModel = viewModel()
) {
    var showPostDialog by remember { mutableStateOf(false) }
    var postContent by remember { mutableStateOf("") }
    val posts by viewModel.posts
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Farmer Community") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPostDialog = true }) {
                Icon(Icons.Default.Comment, contentDescription = "Create Post")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(posts) { post ->
                PostCard(
                    post = post,
                    currentUserId = currentUserId,
                    onLikeClick = { viewModel.toggleLike(post.id) },
                    onCommentSubmit = { text -> viewModel.addComment(post.id, text, userName) }
                )
            }
        }

        if (showPostDialog) {
            AlertDialog(
                onDismissRequest = { showPostDialog = false },
                title = { Text("Create Post") },
                text = {
                    OutlinedTextField(
                        value = postContent,
                        onValueChange = { postContent = it },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        placeholder = { Text("Share your thoughts with other farmers...") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (postContent.isNotBlank()) {
                                viewModel.createPost(postContent, userName)
                                postContent = ""
                                showPostDialog = false
                            }
                        }
                    ) { Text("Post") }
                },
                dismissButton = {
                    TextButton(onClick = { showPostDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    currentUserId: String?,
    onLikeClick: () -> Unit,
    onCommentSubmit: (String) -> Unit
) {
    var showComments by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    val isLiked = currentUserId != null && post.likes.contains(currentUserId)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(post.authorName, fontWeight = FontWeight.Bold)
                    Text(
                        formatTimestamp(post.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(post.content)
            Spacer(modifier = Modifier.height(16.dp))
            
            Divider()
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onLikeClick) {
                    Icon(
                        if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${post.likes.size} Likes")
                }
                TextButton(onClick = { showComments = !showComments }) {
                    Icon(Icons.Default.Comment, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${post.comments.size} Comments")
                }
            }

            if (showComments) {
                Spacer(modifier = Modifier.height(8.dp))
                post.comments.forEach { comment ->
                    CommentItem(comment)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add a comment...", style = MaterialTheme.typography.bodySmall) },
                        maxLines = 2
                    )
                    IconButton(onClick = {
                        if (commentText.isNotBlank()) {
                            onCommentSubmit(commentText)
                            commentText = ""
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(comment: Comment) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(start = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(comment.authorName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(formatTimestamp(comment.timestamp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return format.format(date)
}
