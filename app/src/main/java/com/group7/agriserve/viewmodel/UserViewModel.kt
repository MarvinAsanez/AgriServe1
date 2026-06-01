package com.group7.agriserve.viewmodel

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

class UserViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    var farmerName = mutableStateOf("Loading...")
        private set
        
    var farmerEmail = mutableStateOf("")
        private set

    var farmerPhone = mutableStateOf("")
        private set

    var rsbsaNumber = mutableStateOf("")
        private set

    var isVerified = mutableStateOf(false)
        private set

    var accountStatus = mutableStateOf("Pending") // "Pending", "Approved", "Rejected"
        private set

    var role = mutableStateOf("User")
        private set

    var profileImageUrl = mutableStateOf<String?>(null)
        private set

    var region = mutableStateOf("")
        private set

    var province = mutableStateOf("")
        private set

    var city = mutableStateOf("")
        private set

    var barangay = mutableStateOf("")
        private set

    var street = mutableStateOf("")
        private set

    init {
        fetchUserProfile()
    }

    fun fetchUserProfile() {
        val user = auth.currentUser
        if (user == null) {
            farmerName.value = "Guest"
            return
        }
        
        val uid = user.uid
        farmerEmail.value = user.email ?: ""
        
        db.collection("users").document(uid).addSnapshotListener { document, _ ->
            if (document != null && document.exists()) {
                farmerName.value = document.getString("name") ?: document.getString("fullName") ?: "Farmer"
                farmerEmail.value = document.getString("email") ?: user.email ?: ""
                farmerPhone.value = document.getString("phone") ?: ""
                rsbsaNumber.value = document.getString("rsbsaNumber") ?: ""
                isVerified.value = document.getBoolean("isVerified") ?: false
                
                // Get status from Firestore, default to "Approved" if isVerified is true but status is missing
                val status = document.getString("status") ?: if (isVerified.value) "Approved" else "Pending"
                accountStatus.value = status

                role.value = document.getString("role") ?: "User"
                profileImageUrl.value = document.getString("profileImageUrl") ?: document.getString("idImageUrl")
                region.value = document.getString("region") ?: ""
                province.value = document.getString("province") ?: ""
                city.value = document.getString("city") ?: ""
                barangay.value = document.getString("barangay") ?: ""
                street.value = document.getString("street") ?: ""
            } else {
                farmerName.value = user.displayName ?: "Farmer"
            }
        }
    }

    fun updateProfile(
        newName: String, 
        newPhone: String,
        newRegion: String,
        newProvince: String,
        newCity: String,
        newBarangay: String,
        newStreet: String,
        newImageUri: Uri?, 
        onComplete: (Boolean, String?) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        
        val updates = hashMapOf<String, Any>(
            "name" to newName,
            "phone" to newPhone,
            "region" to newRegion,
            "province" to newProvince,
            "city" to newCity,
            "barangay" to newBarangay,
            "street" to newStreet
        )

        if (newImageUri != null) {
            val storageRef = storage.reference.child("profile_pictures/$uid")
            storageRef.putFile(newImageUri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) task.exception?.let { throw it }
                    storageRef.downloadUrl
                }
                .addOnSuccessListener { uri ->
                    updates["profileImageUrl"] = uri.toString()
                    db.collection("users").document(uid).set(updates, SetOptions.merge())
                        .addOnSuccessListener { onComplete(true, null) }
                        .addOnFailureListener { e -> onComplete(false, "Firestore Error: ${e.message}") }
                }
                .addOnFailureListener { e -> onComplete(false, "Storage Error: ${e.message}") }
        } else {
            db.collection("users").document(uid).set(updates, SetOptions.merge())
                .addOnSuccessListener { onComplete(true, null) }
                .addOnFailureListener { e -> onComplete(false, "Firestore Error: ${e.message}") }
        }
    }

    fun changePassword(oldPass: String, newPass: String, onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        val credential = EmailAuthProvider.getCredential(user.email!!, oldPass)

        user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
            if (reauthTask.isSuccessful) {
                user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                    if (updateTask.isSuccessful) {
                        onComplete(true, null)
                    } else {
                        onComplete(false, updateTask.exception?.message)
                    }
                }
            } else {
                onComplete(false, "Authentication failed: Re-check your old password")
            }
        }
    }
}
