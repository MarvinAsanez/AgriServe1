package com.group7.agriserve.ui.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group7.agriserve.ui.components.LocationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupComplete: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }

    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var rsbsaNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // --- Dynamic Address State ---
    var regions by remember { mutableStateOf<List<LocationItem>>(emptyList()) }
    var provinces by remember { mutableStateOf<List<LocationItem>>(emptyList()) }
    var cities by remember { mutableStateOf<List<LocationItem>>(emptyList()) }
    var barangays by remember { mutableStateOf<List<LocationItem>>(emptyList()) }

    var selectedRegion by remember { mutableStateOf<LocationItem?>(null) }
    var selectedProvince by remember { mutableStateOf<LocationItem?>(null) }
    var selectedCity by remember { mutableStateOf<LocationItem?>(null) }
    var selectedBarangay by remember { mutableStateOf<LocationItem?>(null) }
    var street by remember { mutableStateOf("") }

    var regionExpanded by remember { mutableStateOf(false) }
    var provinceExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var barangayExpanded by remember { mutableStateOf(false) }

    // Fetch Regions on startup
    LaunchedEffect(Unit) {
        regions = fetchPSGCData("https://psgc.gitlab.io/api/regions/")
    }

    // Fetch Provinces when Region changes
    LaunchedEffect(selectedRegion) {
        if (selectedRegion != null) {
            provinces = fetchPSGCData("https://psgc.gitlab.io/api/regions/${selectedRegion?.code}/provinces/")
            if (provinces.isEmpty()) {
                cities = fetchPSGCData("https://psgc.gitlab.io/api/regions/${selectedRegion?.code}/cities-municipalities/")
            }
        }
    }

    // Fetch Cities when Province changes
    LaunchedEffect(selectedProvince) {
        if (selectedProvince != null) {
            cities = fetchPSGCData("https://psgc.gitlab.io/api/provinces/${selectedProvince?.code}/cities-municipalities/")
        }
    }

    // Fetch Barangays when City changes
    LaunchedEffect(selectedCity) {
        if (selectedCity != null) {
            barangays = fetchPSGCData("https://psgc.gitlab.io/api/cities-municipalities/${selectedCity?.code}/barangays/")
        }
    }

    var isLoading by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { onSignupComplete() },
            title = { Text("Success") },
            text = { Text("Your account has been created successfully! Please wait for admin verification before you can access all features.") },
            confirmButton = {
                Button(onClick = { 
                    showSuccessDialog = false
                    onSignupComplete() 
                }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Create Account", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Person, null) })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = rsbsaNumber, onValueChange = { rsbsaNumber = it }, label = { Text("RSBSA Number") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.AppRegistration, null) }, placeholder = { Text("e.g. 12-34-56-789") })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Phone, null) })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Email, null) })

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Address Details", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Start), color = MaterialTheme.colorScheme.primary)

        // --- Region Dropdown ---
        ExposedDropdownMenuBox(expanded = regionExpanded, onExpandedChange = { regionExpanded = !regionExpanded }) {
            OutlinedTextField(
                value = selectedRegion?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Region") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = regionExpanded, onDismissRequest = { regionExpanded = false }) {
                regions.forEach { item ->
                    DropdownMenuItem(text = { Text(item.name) }, onClick = {
                        selectedRegion = item
                        selectedProvince = null; selectedCity = null; selectedBarangay = null
                        regionExpanded = false
                    })
                }
            }
        }

        // --- Province Dropdown ---
        if (provinces.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = provinceExpanded, onExpandedChange = { provinceExpanded = !provinceExpanded }) {
                OutlinedTextField(
                    value = selectedProvince?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Province") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = provinceExpanded, onDismissRequest = { provinceExpanded = false }) {
                    provinces.forEach { item ->
                        DropdownMenuItem(text = { Text(item.name) }, onClick = {
                            selectedProvince = item
                            selectedCity = null; selectedBarangay = null
                            provinceExpanded = false
                        })
                    }
                }
            }
        }

        // --- City Dropdown ---
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = cityExpanded, onExpandedChange = { cityExpanded = !cityExpanded }) {
            OutlinedTextField(
                value = selectedCity?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("City/Municipality") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                enabled = (selectedProvince != null || provinces.isEmpty())
            )
            ExposedDropdownMenu(expanded = cityExpanded, onDismissRequest = { cityExpanded = false }) {
                cities.forEach { item ->
                    DropdownMenuItem(text = { Text(item.name) }, onClick = {
                        selectedCity = item
                        selectedBarangay = null
                        cityExpanded = false
                    })
                }
            }
        }

        // --- Barangay Dropdown ---
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = barangayExpanded, onExpandedChange = { barangayExpanded = !barangayExpanded }) {
            OutlinedTextField(
                value = selectedBarangay?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Barangay") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = barangayExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                enabled = selectedCity != null
            )
            ExposedDropdownMenu(expanded = barangayExpanded, onDismissRequest = { barangayExpanded = false }) {
                barangays.forEach { item ->
                    DropdownMenuItem(text = { Text(item.name) }, onClick = {
                        selectedBarangay = item
                        barangayExpanded = false
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = street, onValueChange = { street = it }, label = { Text("Street / House Number") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Security", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Start), color = MaterialTheme.colorScheme.primary)

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        val isFormComplete = name.isNotBlank() && rsbsaNumber.isNotBlank() && email.isNotBlank() && password.length >= 6 &&
                selectedRegion != null && selectedCity != null && selectedBarangay != null

        Button(
            onClick = {
                isLoading = true
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: ""
                        val userData = hashMapOf(
                            "uid" to userId, 
                            "name" to name, 
                            "email" to email, 
                            "phone" to phoneNumber,
                            "rsbsaNumber" to rsbsaNumber,
                            "region" to (selectedRegion?.name ?: ""), 
                            "province" to (selectedProvince?.name ?: ""),
                            "city" to (selectedCity?.name ?: ""), 
                            "barangay" to (selectedBarangay?.name ?: ""),
                            "street" to street, 
                            "role" to "User", 
                            "isVerified" to false,
                            "status" to "Pending"
                        )
                        firestore.collection("users").document(userId).set(userData).addOnSuccessListener {
                            isLoading = false
                            showSuccessDialog = true
                        }.addOnFailureListener { e ->
                            isLoading = false
                            Toast.makeText(context, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        isLoading = false; Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && isFormComplete,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            else Text("CREATE ACCOUNT", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onBackToLogin) { Text("Already have an account? Login") }
    }
}

// --- Helper Functions for PSGC API ---
private suspend fun fetchPSGCData(urlString: String): List<LocationItem> = withContext(Dispatchers.IO) {
    try {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val data = conn.inputStream.bufferedReader().readText()
        val jsonArray = JSONArray(data)
        val list = mutableListOf<LocationItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(LocationItem(obj.getString("code"), obj.getString("name")))
        }
        list.sortedBy { it.name }
    } catch (e: Exception) {
        Log.e("Signup", "Error fetching location: ${e.message}")
        emptyList()
    }
}
