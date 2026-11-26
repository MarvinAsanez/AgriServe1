package com.group7.agriserve.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.group7.agriserve.viewmodel.UserViewModel
import com.group7.agriserve.util.NetworkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    userViewModel: UserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""

    // Form state
    var overallRating by remember { mutableIntStateOf(5) }
    var puRating by remember { mutableIntStateOf(5) }   // Perceived Usefulness
    var peouRating by remember { mutableIntStateOf(5) } // Perceived Ease of Use
    var effRating by remember { mutableIntStateOf(5) }  // System Efficiency
    var relRating by remember { mutableIntStateOf(5) }  // Reliability
    var selectedCategory by remember { mutableStateOf("General Experience") }
    var qualitativeComment by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // Farmer previous submissions
    var myFeedbackList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    val categories = listOf(
        "General Experience",
        "Seeds & Planting Aid",
        "Fertilizer Programs",
        "Machinery & Tools",
        "Incident & Pest Response"
    )

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            db.collection("feedback")
                .whereEqualTo("userId", currentUserId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        myFeedbackList = snapshot.documents.map { doc ->
                            val data = doc.data ?: emptyMap<String, Any>()
                            data + ("id" to doc.id)
                        }.sortedByDescending { it["timestamp"] as? Timestamp ?: Timestamp.now() }
                    }
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("System Evaluation & Feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Technology Acceptance Model (TAM)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Intro Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Help Us Improve AgriServe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your evaluation helps the Department of Agriculture measure system effectiveness, improve farm assistance distribution, and enhance digital services.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Overall Star Rating
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Overall Experience Rating", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(horizontalArrangement = Arrangement.Center) {
                            for (star in 1..5) {
                                val isSelected = star <= overallRating
                                IconButton(onClick = { overallRating = star }) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "$star stars",
                                        tint = if (isSelected) Color(0xFFFFB300) else Color.LightGray,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }

                        val ratingText = when (overallRating) {
                            5 -> "5 / 5 - Strongly Agree (Excellent)"
                            4 -> "4 / 5 - Agree (Good)"
                            3 -> "3 / 5 - Moderate (Satisfactory)"
                            2 -> "2 / 5 - Disagree (Needs Work)"
                            else -> "1 / 5 - Strongly Disagree (Poor)"
                        }
                        Text(ratingText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Category Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Category of Assistance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.forEach { category ->
                                val isSelected = selectedCategory == category
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCategory = category },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = isSelected, onClick = { selectedCategory = category })
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(category, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAM Dimension Likert Questions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Detailed Evaluation (1 = Low, 5 = High)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        LikertDimensionRow(
                            title = "Perceived Usefulness (PU)",
                            subtitle = "AgriServe speeds up receipt of DA aid and seeds.",
                            value = puRating,
                            onValueChange = { puRating = it }
                        )

                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        LikertDimensionRow(
                            title = "Perceived Ease of Use (PEOU)",
                            subtitle = "The app interface is clear and straightforward.",
                            value = peouRating,
                            onValueChange = { peouRating = it }
                        )

                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        LikertDimensionRow(
                            title = "System Efficiency (EFF)",
                            subtitle = "Digital records eliminate paperwork delays.",
                            value = effRating,
                            onValueChange = { effRating = it }
                        )

                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        LikertDimensionRow(
                            title = "Reliability & Security (REL)",
                            subtitle = "The system is dependable and keeps farm data safe.",
                            value = relRating,
                            onValueChange = { relRating = it }
                        )
                    }
                }
            }

            // Qualitative Feedback / Recommendations
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Suggestions & Government Program Needs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "What other tools, assistance programs, or training should the Department of Agriculture provide?",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                        )

                        OutlinedTextField(
                            value = qualitativeComment,
                            onValueChange = { qualitativeComment = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            placeholder = { Text("Enter your recommendations for DA...") },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Submit Button
            item {
                Button(
                    onClick = {
                        if (currentUserId.isEmpty()) {
                            Toast.makeText(context, "Please log in to submit feedback.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSubmitting = true
                        val isOnline = NetworkUtils.isOnline(context)
                        val feedbackData = hashMapOf(
                            "isOffline" to !isOnline,
                            "syncStatus" to if (!isOnline) "Queued Offline" else "Synced",
                            "userId" to currentUserId,
                            "farmerName" to userViewModel.farmerName.value,
                            "farmerEmail" to userViewModel.farmerEmail.value,
                            "rating" to overallRating,
                            "perceivedUsefulness" to puRating,
                            "easeOfUse" to peouRating,
                            "systemEfficiency" to effRating,
                            "reliability" to relRating,
                            "category" to selectedCategory,
                            "comment" to qualitativeComment.trim(),
                            "feedback" to qualitativeComment.trim(), // alias for compatibility
                            "status" to "Pending",
                            "timestamp" to FieldValue.serverTimestamp()
                        )

                        db.collection("feedback").add(feedbackData)
                            .addOnSuccessListener {
                                isSubmitting = false
                                qualitativeComment = ""
                                val msg = if (!isOnline) "Evaluation saved locally! Will automatically sync to DA when online." else "Thank you! Your evaluation was submitted to DA."
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { e ->
                                isSubmitting = false
                                Toast.makeText(context, "Failed to submit: " + (e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                            }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Submitting Evaluation...")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("SUBMIT EVALUATION TO DA", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // My Past Feedback Submissions
            if (myFeedbackList.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Your Previous Evaluations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                items(myFeedbackList) { itemData ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = (itemData["category"] ?: "General").toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val ratingVal = (itemData["rating"] as? Number)?.toInt() ?: 5
                                Row {
                                    for (i in 1..ratingVal) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            val commentText = (itemData["comment"] ?: itemData["feedback"] ?: "").toString()
                            if (commentText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "\"" + commentText + "\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }

                            val timestamp = itemData["timestamp"] as? Timestamp
                            if (timestamp != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val dateStr = android.text.format.DateFormat.format("MMM dd, yyyy", timestamp.toDate())
                                Text(
                                    text = "Submitted on $dateStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LikertDimensionRow(
    title: String,
    subtitle: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (score in 1..5) {
                val isSelected = value == score
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onValueChange(score) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = score.toString(),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
