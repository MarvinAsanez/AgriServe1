package com.group7.agriserve.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerStatusScreen(onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    
    var myRequests by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(user?.uid) {
        if (user != null) {
            db.collection("requests")
                .whereEqualTo("farmerId", user.uid)
                .addSnapshotListener { value, error ->
                    if (value != null) {
                        myRequests = value.documents.map { doc -> 
                            val data = doc.data ?: emptyMap<String, Any>()
                            data + ("id" to doc.id)
                        }.sortedByDescending { it["timestamp"] as? Timestamp ?: Timestamp.now() }
                    }
                    isLoading = false
                }
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Status") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (myRequests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No requests found.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(myRequests) { req ->
                    StatusCard(req)
                }
            }
        }
    }
}

@Composable
fun StatusCard(req: Map<String, Any>) {
    val statusField = (req["status"] ?: req["Status"] ?: req["state"] ?: "Pending").toString().trim()
    val deliveryField = (req["deliveryStatus"] ?: req["delivery_status"] ?: "").toString().trim()
    
    val isDistributed = (req["distributed"] == true) || 
                       (req["isDistributed"] == true) || 
                       statusField.equals("Distributed", ignoreCase = true) || 
                       deliveryField.equals("Distributed", ignoreCase = true) ||
                       req["distributed_at"] != null

    val isApproved = (req["approved"] == true) || 
                    (req["isApproved"] == true) || 
                    statusField.equals("Approved", ignoreCase = true) || 
                    statusField.equals("Accepted", ignoreCase = true) ||
                    (req["processed_at"] != null && !isDistributed)

    val isRejected = (req["rejected"] == true) || 
                    (req["isRejected"] == true) || 
                    statusField.equals("Rejected", ignoreCase = true) || 
                    statusField.equals("Denied", ignoreCase = true)

    val status = when {
        isDistributed -> "Distributed"
        isRejected -> "Rejected"
        isApproved -> "Approved"
        else -> statusField.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    val itemName = (req["itemName"] ?: req["item_name"] ?: req["requestType"] ?: "Unknown Item").toString()
    val category = (req["requestType"] ?: req["category"] ?: "Resource").toString()
    val quantity = (req["quantity"] ?: "0").toString()

    val statusColor = when (status) {
        "Approved" -> Color(0xFF4CAF50)    // Green
        "Distributed" -> Color(0xFF2196F3) // Blue
        "Rejected" -> Color(0xFFF44336)    // Red
        "Pending" -> Color(0xFFFF9800)     // Orange
        else -> Color.Gray
    }

    val statusIcon = when (status) {
        "Approved" -> Icons.Default.CheckCircle
        "Distributed" -> Icons.Default.LocalShipping
        "Rejected" -> Icons.Default.Cancel
        else -> Icons.Default.HourglassEmpty
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = itemName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Category: $category",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = status,
                            color = statusColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(
                        text = "Quantity: $quantity",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    val processedAt = req["processed_at"] as? Timestamp
                    if (processedAt != null) {
                        val date = processedAt.toDate()
                        val dateString = android.text.format.DateFormat.format("MMM dd, yyyy", date)
                        Text(
                            text = "Processed: $dateString",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                val timestamp = req["timestamp"] as? Timestamp
                if (timestamp != null) {
                    val date = timestamp.toDate()
                    val dateString = android.text.format.DateFormat.format("MMM dd, yyyy", date)
                    Text(
                        text = "Requested: $dateString",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
