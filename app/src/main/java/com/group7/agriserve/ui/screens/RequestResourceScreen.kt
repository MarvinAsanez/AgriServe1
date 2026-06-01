package com.group7.agriserve.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import com.group7.agriserve.data.model.InventoryItem
import com.group7.agriserve.viewmodel.UserViewModel
import java.io.ByteArrayOutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestResourceScreen(
    onBack: () -> Unit,
    userViewModel: UserViewModel,
    preSelectedType: String = "",
    preSelectedCategory: String = ""
) {
    val context = LocalContext.current
    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    
    var itemName by remember { mutableStateOf(preSelectedType) }
    var selectedCategory by remember { mutableStateOf(preSelectedCategory) }
    var quantity by remember { mutableStateOf("") }
    var farmSize by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    
    var showSuccessDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // State for available inventory from DA
    var availableInventory by remember { mutableStateOf<List<InventoryItem>>(emptyList()) }
    var isLoadingInventory by remember { mutableStateOf(true) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> selectedMediaUri = uri }

    // Fetch available inventory
    LaunchedEffect(Unit) {
        firestore.collection("inventory")
            .whereGreaterThan("quantity", 0)
            .get()
            .addOnSuccessListener { result ->
                availableInventory = result.documents.mapNotNull { it.toObject(InventoryItem::class.java) }
                isLoadingInventory = false
                
                // Update category if itemName was pre-selected but category wasn't
                if (itemName.isNotEmpty() && selectedCategory.isEmpty()) {
                    val item = availableInventory.find { it.item_name == itemName }
                    if (item != null) {
                        selectedCategory = item.category
                    }
                }
            }
            .addOnFailureListener {
                isLoadingInventory = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Resource") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Application Form", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Display Farmer Name (read-only)
            OutlinedTextField(
                value = userViewModel.farmerName.value,
                onValueChange = {},
                readOnly = true,
                label = { Text("Farmer Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dropdown restricted to available products
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (itemName.isNotEmpty()) "$selectedCategory: $itemName" else "",
                    onValueChange = { /* readOnly */ },
                    readOnly = true,
                    label = { Text("Select Available Resource from DA") },
                    placeholder = { Text(if (isLoadingInventory) "Loading resources..." else "Select a product") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    supportingText = {
                        if (availableInventory.isEmpty() && !isLoadingInventory) {
                            Text("No items currently in stock at DA office", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableInventory.forEach { item ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("${item.category}: ${item.item_name}", fontWeight = FontWeight.Bold)
                                    Text("${item.quantity} ${item.unit} available", style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                itemName = item.item_name
                                selectedCategory = item.category
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = farmSize,
                    onValueChange = { farmSize = it },
                    label = { Text("Farm Size") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Farm Evidence (Photo Required)", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clickable { launcher.launch("image/*") }, 
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selectedMediaUri != null) {
                        AsyncImage(
                            model = selectedMediaUri,
                            contentDescription = "Selected Media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(48.dp))
                            Text("UPLOAD PHOTO OF FARM")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (itemName.isBlank() || farmSize.isBlank() || quantity.isBlank() || selectedMediaUri == null) {
                        Toast.makeText(context, "Please complete all fields", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Final validation check against current stock
                    val selectedItem = availableInventory.find { it.item_name == itemName }
                    val requestedQty = quantity.toIntOrNull() ?: 0
                    
                    if (selectedItem == null) {
                        Toast.makeText(context, "This item is no longer available", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    if (requestedQty <= 0) {
                         Toast.makeText(context, "Please enter a valid quantity", Toast.LENGTH_SHORT).show()
                         return@Button
                    }

                    if (requestedQty > selectedItem.quantity) {
                        Toast.makeText(context, "Insufficient stock. Only ${selectedItem.quantity} available.", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    isSubmitting = true
                    val userId = auth.currentUser?.uid ?: ""
                    
                    try {
                        val inputStream = context.contentResolver.openInputStream(selectedMediaUri!!)
                        val originalBitmap = BitmapFactory.decodeStream(inputStream)
                        val scaledBitmap = if (originalBitmap.width > 800) {
                            val aspectRatio = originalBitmap.height.toFloat() / originalBitmap.width.toFloat()
                            Bitmap.createScaledBitmap(originalBitmap, 800, (800 * aspectRatio).toInt(), true)
                        } else { originalBitmap }

                        val outputStream = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                        val imageByteArray = outputStream.toByteArray()
                        
                        // Generate a unique filename for the evidence
                        val filename = "requests/evidence_${UUID.randomUUID()}.jpg"
                        val storageRef = storage.reference.child(filename)
                        
                        storageRef.putBytes(imageByteArray)
                            .continueWithTask { task ->
                                if (!task.isSuccessful) task.exception?.let { throw it }
                                storageRef.downloadUrl
                            }
                            .addOnSuccessListener { downloadUrl ->
                                val requestData = hashMapOf(
                                    "farmerId" to userId,
                                    "farmerName" to userViewModel.farmerName.value,
                                    "requestType" to selectedCategory,
                                    "itemName" to itemName,
                                    "quantity" to quantity,
                                    "farmSize" to farmSize,
                                    "description" to description,
                                    "evidenceImageUrl" to downloadUrl.toString(), // Store URL instead of Base64
                                    "status" to "Pending",
                                    "timestamp" to Timestamp.now()
                                )

                                firestore.collection("requests").add(requestData)
                                    .addOnSuccessListener {
                                        isSubmitting = false
                                        showSuccessDialog = true
                                    }
                                    .addOnFailureListener { e ->
                                        isSubmitting = false
                                        Toast.makeText(context, "Firestore Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                isSubmitting = false
                                Toast.makeText(context, "Upload Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } catch (e: Exception) {
                        isSubmitting = false
                        Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting && !isLoadingInventory && availableInventory.isNotEmpty()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("SUBMIT APPLICATION")
                }
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            confirmButton = {
                TextButton(onClick = { 
                    showSuccessDialog = false
                    onBack()
                }) { Text("OK") }
            },
            title = { Text("Success") },
            text = { Text("Your application for $selectedCategory: $itemName has been submitted!") }
        )
    }
}
