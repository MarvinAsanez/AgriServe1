package com.group7.agriserve.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.group7.agriserve.viewmodel.UserViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(
    onBack: () -> Unit,
    userViewModel: UserViewModel
) {
    val context = LocalContext.current
    val storage = FirebaseStorage.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var contactNumber by remember { mutableStateOf("") }
    var reportDescription by remember { mutableStateOf("") }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }
    
    var myReports by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        firestore.collection("reports")
            .whereEqualTo("farmerId", uid)
            .addSnapshotListener { value, _ ->
                if (value != null) {
                    myReports = value.documents.map { doc ->
                        val data = doc.data ?: emptyMap<String, Any>()
                        data + ("id" to doc.id)
                    }.sortedByDescending { it["timestamp"] as? Timestamp ?: Timestamp.now() }
                }
            }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> selectedImageUris = uris }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            Toast.makeText(context, "Location Access Granted", Toast.LENGTH_SHORT).show()
        }
    }

    fun performSubmission(location: Location?) {
        val uid = auth.currentUser?.uid ?: return
        val reportId = UUID.randomUUID().toString()
        val uploadedUrls = mutableListOf<String>()

        if (selectedImageUris.isEmpty()) {
            Toast.makeText(context, "Please select at least one photo", Toast.LENGTH_SHORT).show()
            isUploading = false
            return
        }

        var uploadedCount = 0
        selectedImageUris.forEachIndexed { index, uri ->
            val ref = storage.reference.child("reports/$reportId/img_$index.jpg")
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                    uploadedUrls.add(downloadUrl.toString())
                    uploadedCount++
                    
                    if (uploadedCount == selectedImageUris.size) {
                        val reportData = hashMapOf(
                            "reportId" to reportId,
                            "farmerId" to uid,
                            "farmerName" to userViewModel.farmerName.value,
                            "contact" to contactNumber,
                            "description" to reportDescription,
                            "imageUrls" to uploadedUrls,
                            "status" to "Pending",
                            "timestamp" to Timestamp.now(),
                            "latitude" to (location?.latitude ?: 0.0),
                            "longitude" to (location?.longitude ?: 0.0),
                            "hasGeotag" to (location != null)
                        )

                        firestore.collection("reports").document(reportId).set(reportData)
                            .addOnSuccessListener {
                                isUploading = false
                                Toast.makeText(context, "Report Submitted Successfully", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                            .addOnFailureListener {
                                isUploading = false
                                Toast.makeText(context, "Firestore Error: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }.addOnFailureListener {
                isUploading = false
                Toast.makeText(context, "Upload Failed at image $index", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun captureLocationAndSubmit() {
        isUploading = true
        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location: Location? ->
                performSubmission(location)
            }
            .addOnFailureListener {
                Toast.makeText(context, "Could not get location. Ensure GPS is on.", Toast.LENGTH_LONG).show()
                performSubmission(null)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Incident") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text(text = "Incident Evidence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.size(120.dp).clickable { 
                            imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                        },
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, null)
                                Text("Add Photos", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                items(selectedImageUris) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { selectedImageUris = selectedImageUris - uri },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = contactNumber,
                onValueChange = { contactNumber = it },
                label = { Text("Contact Number") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Phone, null) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = reportDescription,
                onValueChange = { reportDescription = it },
                label = { Text("Describe the Issue / Pest Info") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                placeholder = { Text("Example: Seen brown plant hoppers in the north quadrant...") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { 
                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    captureLocationAndSubmit() 
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading && selectedImageUris.isNotEmpty() && reportDescription.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Submitting...")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("SUBMIT GEOTAGGED REPORT")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(text = "Previous Reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            if (myReports.isEmpty()) {
                Text("No reports submitted yet.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else {
                myReports.forEach { report ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        ListItem(
                            headlineContent = { Text(report["description"]?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { 
                                val status = report["status"]?.toString() ?: "Pending"
                                Text("Status: $status", color = if(status == "Pending") Color.Gray else Color(0xFF4CAF50))
                            },
                            trailingContent = {
                                if (report["hasGeotag"] == true) {
                                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
