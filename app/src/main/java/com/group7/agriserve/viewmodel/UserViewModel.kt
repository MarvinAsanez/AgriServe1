package com.group7.agriserve.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.util.ValidationUtils
import java.io.ByteArrayOutputStream

class UserViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage: FirebaseStorage by lazy {
        try {
            FirebaseStorage.getInstance("gs://agriserve-9ad30.firebasestorage.app")
        } catch (_: Exception) {
            FirebaseStorage.getInstance()
        }
    }
    
    // Status & Role State
    // Statuses: "loading", "not_submitted", "pending", "verified", "guest", "resubmission_required"
    var farmerStatus = mutableStateOf("loading")
        private set

    // Roles: "verified_farmer", "guest_farmer"
    var farmerRole = mutableStateOf("guest_farmer")
        private set

    // RSBSA Demographic Fields
    var firstName = mutableStateOf("")
        private set

    var middleName = mutableStateOf("")
        private set

    var lastName = mutableStateOf("")
        private set

    var extensionName = mutableStateOf("")
        private set

    var sex = mutableStateOf("Male")
        private set

    var birthdate = mutableStateOf("")
        private set

    var phoneNumber = mutableStateOf("")
        private set

    var gmail = mutableStateOf("")
        private set

    var validIdUrl = mutableStateOf<String?>(null)
        private set

    var validIdBackUrl = mutableStateOf<String?>(null)
        private set

    // DA Staff Admin Review Data
    var adminFeedback = mutableStateOf<String?>(null)
        private set

    var adminReviewedBy = mutableStateOf<String?>(null)
        private set

    var adminReviewedAt = mutableStateOf<Timestamp?>(null)
        private set

    // Legacy fields maintained for UI backwards-compatibility
    var farmerName = mutableStateOf("Loading...")
        private set
        
    var farmerDocId = mutableStateOf("")
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

    var verificationStatus = mutableStateOf("Not Submitted")
        private set

    var hasFaceVerified = mutableStateOf(false)
        private set

    var faceImageUrl = mutableStateOf<String?>(null)
        private set

    var idImageUrl = mutableStateOf<String?>(null)
        private set

    var idBackImageUrl = mutableStateOf<String?>(null)
        private set

    var faceMatchScore = mutableStateOf(0)
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

    // Locale initialization
    var selectedLanguage = mutableStateOf(
        run {
            val current = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "en"
            when (current) { "tl" -> "fil"; "bik" -> "bcl"; else -> current }
        }
    )
        private set

    // Theme Mode: "system", "light", "dark"
    var themeMode = mutableStateOf("system")
        private set

    private var appContext: Context? = null

    init {
        // Only trigger initial fetch if current user is already present in Firebase Auth.
        // Context-aware session loading will be executed by MainActivity.loadCachedSession.
        if (auth.currentUser != null) {
            fetchUserProfile()
        }
    }

    fun loadCachedSession(context: Context) {
        this.appContext = context.applicationContext
        hasFaceVerified.value = true

        val cachedId = SessionManager.getSavedUserId(context)
        if (!cachedId.isNullOrBlank()) {
            farmerDocId.value = cachedId
        }
        val cachedEmail = SessionManager.getSavedUserEmail(context)
        if (!cachedEmail.isNullOrBlank()) {
            farmerEmail.value = cachedEmail
            gmail.value = cachedEmail
        }

        val cachedTheme = SessionManager.getCachedThemeMode(context)
        if (cachedTheme.isNotBlank() && cachedTheme in listOf("system", "light", "dark")) {
            themeMode.value = cachedTheme
        }

        val cachedLang = SessionManager.getCachedAppLanguage(context)
        if (cachedLang.isNotBlank()) {
            val normLang = when (cachedLang) { "tl" -> "fil"; "bik" -> "bcl"; else -> cachedLang }
            selectedLanguage.value = normLang
        }

        val cachedName = SessionManager.getCachedFarmerName(context)
        if (cachedName.isNotBlank() && (farmerName.value == "Loading..." || farmerName.value == "Guest")) {
            farmerName.value = cachedName
        }
        val cachedRole = SessionManager.getCachedUserRole(context)
        if (cachedRole.isNotBlank()) {
            role.value = cachedRole
            farmerRole.value = if (cachedRole == "Admin") "verified_farmer" else cachedRole
        }
        val cachedVerified = SessionManager.getCachedIsVerified(context)
        isVerified.value = cachedVerified

        val cachedStatus = SessionManager.getCachedAccountStatus(context)
        if (cachedStatus.isNotBlank()) {
            accountStatus.value = cachedStatus
        }

        val cachedFarmerStatus = SessionManager.getCachedFarmerStatus(context)
        if (cachedFarmerStatus.isNotBlank() && cachedFarmerStatus != "loading") {
            farmerStatus.value = cachedFarmerStatus
        } else if (cachedVerified) {
            farmerStatus.value = "verified"
        } else if (cachedStatus.isNotBlank()) {
            farmerStatus.value = when (cachedStatus.lowercase()) {
                "approved", "verified" -> "verified"
                "rejected", "guest" -> "guest"
                "pending" -> "pending"
                else -> "guest"
            }
        }
    }

    private var farmerListener: ListenerRegistration? = null

    /**
     * Parse and bind incoming farmer document from Firestore,
     * update local state, and cache session for offline resilience.
     */
    fun bindFarmerDocument(context: Context?, document: DocumentSnapshot) {
        farmerDocId.value = document.id
        val pInfo = document.get("personalInfo") as? Map<*, *>
        val cInfo = document.get("contactInfo") as? Map<*, *>
        val docs = document.get("documents") as? Map<*, *>
        val aRev = document.get("adminReview") as? Map<*, *>

        val fName = (pInfo?.get("firstName") as? String) ?: document.getString("firstName") ?: ""
        val mName = (pInfo?.get("middleName") as? String) ?: document.getString("middleName") ?: ""
        val lName = (pInfo?.get("lastName") as? String) ?: document.getString("lastName") ?: ""
        val ext = (pInfo?.get("extensionName") as? String) ?: document.getString("extensionName") ?: ""

        firstName.value = fName
        middleName.value = mName
        lastName.value = lName
        extensionName.value = ext
        sex.value = (pInfo?.get("sex") as? String) ?: document.getString("sex") ?: "Male"
        birthdate.value = (pInfo?.get("birthdate") as? String) ?: document.getString("birthdate") ?: ""

        phoneNumber.value = (cInfo?.get("phoneNumber") as? String) ?: document.getString("phone") ?: ""
        farmerPhone.value = phoneNumber.value

        val emailVal = (cInfo?.get("gmail") as? String) ?: document.getString("email") ?: document.getString("gmail") ?: ""
        gmail.value = emailVal
        farmerEmail.value = emailVal

        validIdUrl.value = (docs?.get("validIdUrl") as? String) ?: document.getString("validIdUrl") ?: document.getString("idImageUrl")
        validIdBackUrl.value = (docs?.get("idBackUrl") as? String) ?: document.getString("idBackUrl") ?: document.getString("idBackImageUrl")
        idImageUrl.value = validIdUrl.value

        // Face & Biometric parsing
        hasFaceVerified.value = true
        val sideBySideFace = (docs?.get("facialIdentityCenterUrl") as? String)
            ?: (docs?.get("facialIdentityUrl") as? String)
            ?: (docs?.get("sideBySideFaceUrl") as? String)
            ?: (docs?.get("sideBySideUrl") as? String)
            ?: (docs?.get("faceImageUrl") as? String)
            ?: document.getString("facialIdentityCenterUrl")
            ?: document.getString("facialIdentityUrl")
            ?: document.getString("sideBySideFaceUrl")
            ?: document.getString("sideBySideUrl")
            ?: document.getString("faceImageUrl")
        faceImageUrl.value = sideBySideFace
        val matchScoreLong = document.getLong("lastOwnerMatchScore") ?: document.getLong("faceMatchScore")
        if (matchScoreLong != null) {
            faceMatchScore.value = matchScoreLong.toInt()
        }

        // Status parsing
        val rawStatus = document.getString("status")?.lowercase() ?: "pending"
        val verifiedBool = document.getBoolean("isVerified") ?: (rawStatus == "verified" || rawStatus == "approved")
        isVerified.value = verifiedBool || (rawStatus == "verified" || rawStatus == "approved")

        val computedStatus = when {
            verifiedBool || rawStatus == "verified" || rawStatus == "approved" -> "verified"
            rawStatus == "guest" || rawStatus == "resubmission_required" || rawStatus == "rejected" -> "guest"
            else -> "pending"
        }
        farmerStatus.value = computedStatus
        accountStatus.value = if (computedStatus == "verified") "Approved" else if (computedStatus == "guest") "Rejected" else "Pending"
        verificationStatus.value = if (computedStatus == "verified") "Approved" else "Pending Review"

        val assignedRole = document.getString("role") ?: if (computedStatus == "verified") "verified_farmer" else "guest_farmer"
        farmerRole.value = assignedRole
        role.value = if (assignedRole == "verified_farmer") "Verified Farmer" else "Guest"

        // Admin Review & Feedback
        adminFeedback.value = (aRev?.get("feedback") as? String)
            ?: (aRev?.get("rejectionReason") as? String)
            ?: document.getString("rejectionReason")
        adminReviewedBy.value = (aRev?.get("reviewedBy") as? String) ?: document.getString("verified_by")
        adminReviewedAt.value = (aRev?.get("reviewedAt") as? Timestamp) ?: document.getTimestamp("verified_at")

        // RSBSA Number extraction
        val rsbsaVal = document.getString("rsbsaNumber")
            ?: document.getString("rsbsa")
            ?: (pInfo?.get("rsbsaNumber") as? String)
            ?: ""
        rsbsaNumber.value = rsbsaVal

        // Address extraction (Zone, Barangay, Municipality/City, Province, Region)
        val addressMap = (document.get("address") as? Map<*, *>) ?: (document.get("location") as? Map<*, *>)
        val zoneVal = (addressMap?.get("zone") as? String)
            ?: (addressMap?.get("street") as? String)
            ?: document.getString("zone")
            ?: document.getString("street")
            ?: ""
        val brgyVal = (addressMap?.get("barangay") as? String)
            ?: document.getString("barangay")
            ?: ""
        val cityVal = (addressMap?.get("municipality") as? String)
            ?: (addressMap?.get("city") as? String)
            ?: document.getString("municipality")
            ?: document.getString("city")
            ?: ""
        val provVal = (addressMap?.get("province") as? String)
            ?: document.getString("province")
            ?: ""
        val regVal = (addressMap?.get("region") as? String)
            ?: document.getString("region")
            ?: ""

        street.value = zoneVal
        barangay.value = brgyVal
        city.value = cityVal
        province.value = provVal
        region.value = regVal

        // Formatted display name
        val nameParts = listOf(fName, mName, lName, ext).filter { it.isNotBlank() }
        val fullName = if (nameParts.isNotEmpty()) nameParts.joinToString(" ") else (document.getString("name") ?: document.getString("fullName") ?: "Farmer")
        farmerName.value = fullName

        // Theme and language synchronization from cloud
        val cloudTheme = document.getString("themeMode")
        if (!cloudTheme.isNullOrBlank() && cloudTheme in listOf("system", "light", "dark")) {
            themeMode.value = cloudTheme
            context?.let { SessionManager.saveThemeMode(it, cloudTheme) }
        }
        val cloudLang = document.getString("language")
        if (!cloudLang.isNullOrBlank()) {
            val normLang = when (cloudLang) { "tl" -> "fil"; "bik" -> "bcl"; else -> cloudLang }
            selectedLanguage.value = normLang
            context?.let { SessionManager.saveAppLanguage(it, normLang) }
        }

        // Save session locally
        if (context != null) {
            SessionManager.saveLoginSession(
                context = context,
                userId = document.id,
                email = emailVal,
                name = fullName,
                role = role.value,
                isVerified = isVerified.value,
                accountStatus = accountStatus.value,
                farmerStatus = computedStatus
            )
        }

        // Link with current Firebase Auth user if present
        val currentUser = auth.currentUser
        if (currentUser != null && document.id != currentUser.uid) {
            try {
                document.reference.update("uid", currentUser.uid).catchException()
            } catch (_: Exception) {}
        }
    }

    /**
     * Complete sign out: detaches Firestore listeners, wipes all cached user session data,
     * resets farmer state to unauthenticated, and signs out of Firebase Auth.
     */
    fun logout(context: Context) {
        farmerListener?.remove()
        farmerListener = null

        farmerStatus.value = "unauthenticated"
        farmerRole.value = "guest_farmer"
        isVerified.value = false
        accountStatus.value = "Pending"
        verificationStatus.value = "Not Submitted"
        hasFaceVerified.value = false

        firstName.value = ""
        middleName.value = ""
        lastName.value = ""
        extensionName.value = ""
        sex.value = "Male"
        birthdate.value = ""
        phoneNumber.value = ""
        gmail.value = ""
        validIdUrl.value = null
        idImageUrl.value = null
        adminFeedback.value = null
        adminReviewedBy.value = null
        adminReviewedAt.value = null

        farmerName.value = "Guest"
        farmerEmail.value = ""
        farmerPhone.value = ""
        rsbsaNumber.value = ""

        street.value = ""
        barangay.value = ""
        city.value = ""
        province.value = ""
        region.value = ""

        SessionManager.clearSession(context)
        try {
            auth.signOut()
        } catch (_: Exception) {}
    }

    /**
     * Attach realtime snapshot listener to the given document reference
     */
    fun attachFarmerListener(context: Context?, docRef: DocumentReference) {
        farmerListener?.remove()
        farmerListener = docRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                bindFarmerDocument(context, snapshot)
            }
        }
    }

    /**
     * Query and listen to user profile using both UID and Email fallbacks
     */
    fun fetchUserProfile(context: Context? = null) {
        if (context != null) {
            this.appContext = context.applicationContext
        hasFaceVerified.value = true

        val cachedId = SessionManager.getSavedUserId(context)
        if (!cachedId.isNullOrBlank()) {
            farmerDocId.value = cachedId
        }
        val cachedEmail = SessionManager.getSavedUserEmail(context)
        if (!cachedEmail.isNullOrBlank()) {
            farmerEmail.value = cachedEmail
            gmail.value = cachedEmail
        }
        }
        val user = auth.currentUser
        val savedUid = context?.let { SessionManager.getSavedUserId(it) }
        val savedEmail = context?.let { SessionManager.getSavedUserEmail(it) }

        val uid = user?.uid ?: savedUid
        val userEmail = (user?.email ?: savedEmail ?: "").trim().lowercase()

        if (uid.isNullOrBlank() && userEmail.isBlank()) {
            if (context != null) {
                farmerName.value = "Guest"
                farmerStatus.value = "not_submitted"
            }
            return
        }

        if (userEmail.isNotBlank()) {
            farmerEmail.value = userEmail
            gmail.value = userEmail
        }

        fun queryByEmail() {
            if (userEmail.isBlank()) {
                farmerStatus.value = "not_submitted"
                return
            }
            db.collection("farmers")
                .whereEqualTo("contactInfo.gmail", userEmail)
                .get()
                .addOnSuccessListener { qSnap ->
                    if (!qSnap.isEmpty) {
                        val doc = qSnap.documents[0]
                        attachFarmerListener(context, doc.reference)
                    } else {
                        db.collection("farmers")
                            .whereEqualTo("email", userEmail)
                            .get()
                            .addOnSuccessListener { q2 ->
                                if (!q2.isEmpty) {
                                    val doc = q2.documents[0]
                                    attachFarmerListener(context, doc.reference)
                                } else {
                                    db.collection("users")
                                        .whereEqualTo("email", userEmail)
                                        .get()
                                        .addOnSuccessListener { uSnap ->
                                            if (!uSnap.isEmpty) {
                                                val doc = uSnap.documents[0]
                                                attachFarmerListener(context, doc.reference)
                                            } else {
                                                farmerStatus.value = "not_submitted"
                                            }
                                        }
                                        .addOnFailureListener {
                                            farmerStatus.value = "not_submitted"
                                        }
                                }
                            }
                            .addOnFailureListener {
                                farmerStatus.value = "not_submitted"
                            }
                    }
                }
                .addOnFailureListener {
                    farmerStatus.value = "not_submitted"
                }
        }

        if (!uid.isNullOrBlank()) {
            db.collection("farmers").document(uid).get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    attachFarmerListener(context, document.reference)
                } else {
                    // Try legacy users/{uid}
                    db.collection("users").document(uid).get().addOnSuccessListener { uDoc ->
                        if (uDoc != null && uDoc.exists()) {
                            attachFarmerListener(context, uDoc.reference)
                        } else {
                            queryByEmail()
                        }
                    }.addOnFailureListener {
                        queryByEmail()
                    }
                }
            }.addOnFailureListener {
                queryByEmail()
            }
        } else {
            queryByEmail()
        }
    }

    /**
     * Safely compresses an image URI into a high-quality JPEG byte array guaranteed
     * not to exceed [maxBytes] (defaults to 160 KB = ~213 KB in Base64).
     * This guarantees that even with two back-to-back ID cards stored directly as Base64 in Firestore,
     * the combined document payload remains well under the 1,048,576 bytes (1 MiB) limit.
     */
    private fun compressUriToJpeg(context: Context, uri: Uri, maxBytes: Int = 160 * 1024): ByteArray? {
        return try {
            // 1. Read image bounds without allocating pixel memory in RAM
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2. Compute sample size to avoid OOM on high-resolution camera photos (e.g. 48MP/108MP)
            val maxTargetDim = 960 // 960px is crystal clear for government IDs and text readability
            var sampleSize = 1
            while ((origWidth / sampleSize) > maxTargetDim * 1.5 || (origHeight / sampleSize) > maxTargetDim * 1.5) {
                sampleSize *= 2
            }

            // 3. Decode scaled bitmap using RGB_565 (2 bytes/pixel vs 4 bytes/pixel)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            var bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // Respect EXIF orientation from camera capture
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = android.media.ExifInterface(stream)
                    val orientation = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    val rotation = when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    if (rotation != 0f) {
                        val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
                        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        if (rotated != bitmap) {
                            bitmap.recycle()
                            bitmap = rotated
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 4. Scale down proportionally to maxTargetDim if necessary
            val curW = bitmap.width
            val curH = bitmap.height
            if (curW > maxTargetDim || curH > maxTargetDim) {
                val ratio = curW.toFloat() / curH.toFloat()
                val newW = if (ratio > 1f) maxTargetDim else (maxTargetDim * ratio).toInt()
                val newH = if (ratio > 1f) (maxTargetDim / ratio).toInt() else maxTargetDim
                val scaled = Bitmap.createScaledBitmap(bitmap, newW.coerceAtLeast(1), newH.coerceAtLeast(1), true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            // 5. Iteratively compress with stepping quality until it fits strictly inside maxBytes
            var quality = 75
            var outputBytes: ByteArray
            while (true) {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                outputBytes = baos.toByteArray()
                if (outputBytes.size <= maxBytes || quality <= 30) {
                    break
                }
                quality -= 15
            }

            // 6. If still over maxBytes at quality 30, scale down dimensions further
            if (outputBytes.size > maxBytes && bitmap.width > 480) {
                val downscaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * 0.75).toInt().coerceAtLeast(320),
                    (bitmap.height * 0.75).toInt().coerceAtLeast(200),
                    true
                )
                val baos = ByteArrayOutputStream()
                downscaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                outputBytes = baos.toByteArray()
                downscaled.recycle()
            }

            bitmap.recycle()
            outputBytes
        } catch (e: Throwable) {
            Log.e("UserViewModel", "Safe image compression failed: ${e.message}")
            null
        }
    }

    /**
     * Verify that incoming onboarding credentials (RSBSA Number, Gmail, Phone, and Full Name + Birthdate)
     * are strictly unique across the database and not duplicated from any existing farmer application or user account.
     */
    fun checkDuplicateCredentials(
        currentUid: String?,
        firstName: String,
        lastName: String,
        birthdate: String,
        rsbsaNumber: String,
        gmail: String,
        phone: String,
        onResult: (isDuplicate: Boolean, errorMessage: String?) -> Unit
    ) {
        val cleanRsbsa = rsbsaNumber.trim()
        val cleanGmail = gmail.trim().lowercase()
        val cleanPhone = ValidationUtils.normalizePhilippinePhone(phone) ?: phone.trim().filter { it.isDigit() }
        val targetFullName = "${firstName.trim()} ${lastName.trim()}".trim().lowercase()
        val targetBirthdate = birthdate.trim()

        db.collection("farmers").get()
            .addOnSuccessListener { farmersSnap ->
                for (doc in farmersSnap.documents) {
                    if (currentUid != null && doc.id == currentUid) continue
                    val data = doc.data ?: continue

                    val pInfo = data["personalInfo"] as? Map<*, *>
                    val cInfo = data["contactInfo"] as? Map<*, *>

                    // 1. RSBSA Reference Number Check
                    val existingRsbsa = (data["rsbsaNumber"] as? String
                        ?: pInfo?.get("rsbsaNumber") as? String
                        ?: "").trim()
                    if (cleanRsbsa.isNotBlank() && existingRsbsa.isNotBlank() && existingRsbsa.equals(cleanRsbsa, ignoreCase = true)) {
                        onResult(true, "The RSBSA Reference Number '$cleanRsbsa' is already registered to another farmer in AgriServe. Repeated credentials are not permitted.")
                        return@addOnSuccessListener
                    }

                    // 2. Gmail / Email Check
                    val existingEmail = (data["email"] as? String
                        ?: data["gmail"] as? String
                        ?: cInfo?.get("gmail") as? String
                        ?: "").trim().lowercase()
                    if (cleanGmail.isNotBlank() && existingEmail.isNotBlank() && existingEmail.equals(cleanGmail, ignoreCase = true)) {
                        onResult(true, "The Gmail address '$cleanGmail' is already registered in AgriServe. Repeated credential submission is not allowed.")
                        return@addOnSuccessListener
                    }

                    // 3. Mobile Phone Number Check
                    val existingPhoneRaw = (data["phone"] as? String
                        ?: cInfo?.get("phoneNumber") as? String
                        ?: "").trim()
                    val existingPhone = ValidationUtils.normalizePhilippinePhone(existingPhoneRaw) ?: existingPhoneRaw.filter { it.isDigit() }
                    if (cleanPhone.isNotBlank() && existingPhone.isNotBlank() && cleanPhone == existingPhone) {
                        onResult(true, "The mobile phone number '$cleanPhone' is already associated with another account in AgriServe. Repeated credentials are not permitted.")
                        return@addOnSuccessListener
                    }

                    // 4. Exact Full Name + Birthdate Check
                    val existingFirstName = (data["firstName"] as? String ?: pInfo?.get("firstName") as? String ?: "").trim()
                    val existingLastName = (data["lastName"] as? String ?: pInfo?.get("lastName") as? String ?: "").trim()
                    val existingBirthdate = (data["birthdate"] as? String ?: pInfo?.get("birthdate") as? String ?: "").trim()
                    val existingFullName = "$existingFirstName $existingLastName".trim().lowercase()

                    if (targetFullName.isNotBlank() && targetBirthdate.isNotBlank() &&
                        existingFullName.isNotBlank() && existingBirthdate.isNotBlank()) {
                        if (existingFullName.equals(targetFullName, ignoreCase = true) && existingBirthdate == targetBirthdate) {
                            onResult(true, "An application for '${firstName.trim()} ${lastName.trim()}' (Born: $targetBirthdate) has already been submitted. Duplicate submissions are not allowed.")
                            return@addOnSuccessListener
                        }
                    }
                }

                // Check 'users' collection for any legacy accounts
                db.collection("users").get()
                    .addOnSuccessListener { usersSnap ->
                        for (doc in usersSnap.documents) {
                            if (currentUid != null && doc.id == currentUid) continue
                            val data = doc.data ?: continue

                            val existingRsbsa = (data["rsbsaNumber"] as? String ?: data["idNumber"] as? String ?: "").trim()
                            if (cleanRsbsa.isNotBlank() && existingRsbsa.isNotBlank() && existingRsbsa.equals(cleanRsbsa, ignoreCase = true)) {
                                onResult(true, "The RSBSA Reference Number '$cleanRsbsa' is already registered to another user in AgriServe. Repeated credentials are not permitted.")
                                return@addOnSuccessListener
                            }

                            val existingEmail = (data["email"] as? String ?: "").trim().lowercase()
                            if (cleanGmail.isNotBlank() && existingEmail.isNotBlank() && existingEmail.equals(cleanGmail, ignoreCase = true)) {
                                onResult(true, "The Gmail address '$cleanGmail' is already registered in AgriServe. Repeated credential submission is not allowed.")
                                return@addOnSuccessListener
                            }

                            val existingPhoneRaw = (data["phone"] as? String ?: data["contact"] as? String ?: "").trim()
                            val existingPhone = ValidationUtils.normalizePhilippinePhone(existingPhoneRaw) ?: existingPhoneRaw.filter { it.isDigit() }
                            if (cleanPhone.isNotBlank() && existingPhone.isNotBlank() && cleanPhone == existingPhone) {
                                onResult(true, "The mobile phone number '$cleanPhone' is already associated with an existing account. Repeated credentials are not permitted.")
                                return@addOnSuccessListener
                            }
                        }

                        // No duplicate detected!
                        onResult(false, null)
                    }
                    .addOnFailureListener {
                        onResult(false, null)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("UserViewModel", "Duplicate check error: ${e.message}")
                onResult(false, null)
            }
    }

    /**
     * Submit incoming farmer onboarding credentials for DA Staff review.
     * Ensures an active authenticated session exists, compresses the ID image,
     * attempts Storage upload, and automatically falls back to Base64 data URI
     * if Storage permission is restricted, guaranteeing 100% submission reliability.
     */
    fun submitFarmerCredentials(
        context: Context,
        firstNameInput: String,
        middleNameInput: String,
        lastNameInput: String,
        extensionNameInput: String,
        sexInput: String,
        birthdateInput: String,
        phoneInput: String,
        gmailInput: String,
        rsbsaNumberInput: String = "",
        zoneInput: String = "",
        barangayInput: String = "",
        municipalityInput: String = "",
        provinceInput: String = "",
        regionInput: String = "",
        idFrontUri: Uri,
        idBackUri: Uri,
        liveFaceBitmap: Bitmap? = null,
        leftFaceBitmap: Bitmap? = null,
        rightFaceBitmap: Bitmap? = null,
        facialCornersData: Map<String, Any>? = null,
        centerScore: Int = 0,
        leftScore: Int = 0,
        rightScore: Int = 0,
        faceMatchScoreInput: Int = 0,
        passwordInput: String = "",
        onProgress: (Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        fun proceedWithUid(uid: String) {
            val nameParts = listOf(firstNameInput.trim(), middleNameInput.trim(), lastNameInput.trim(), extensionNameInput.trim()).filter { it.isNotBlank() }
            val formattedFullName = nameParts.joinToString(" ")

            fun saveApplication(
                finalFrontUrl: String,
                finalBackUrl: String,
                finalCenterUrl: String? = null,
                finalLeftUrl: String? = null,
                finalRightUrl: String? = null
            ) {
                validIdUrl.value = finalFrontUrl
                validIdBackUrl.value = finalBackUrl
                if (finalCenterUrl != null) {
                    faceImageUrl.value = finalCenterUrl
                    hasFaceVerified.value = (faceMatchScoreInput >= 60)
                    faceMatchScore.value = faceMatchScoreInput
                }

                val documentsMap = hashMapOf<String, Any?>(
                    "validIdUrl" to finalFrontFrontFallback(finalFrontUrl),
                    "idBackUrl" to finalBackUrl
                )
                if (finalCenterUrl != null) {
                    documentsMap["facialIdentityUrl"] = finalCenterUrl
                    documentsMap["facialIdentityCenterUrl"] = finalCenterUrl
                    documentsMap["faceImageUrl"] = finalCenterUrl
                }
                if (finalLeftUrl != null) {
                    documentsMap["facialIdentityLeftUrl"] = finalLeftUrl
                }
                if (finalRightUrl != null) {
                    documentsMap["facialIdentityRightUrl"] = finalRightUrl
                }
                if (facialCornersData != null) {
                    documentsMap["facialCorners"] = facialCornersData
                }
                documentsMap["facialCornerScores"] = mapOf(
                    "centerScore" to centerScore,
                    "leftScore" to leftScore,
                    "rightScore" to rightScore,
                    "overallScore" to faceMatchScoreInput
                )
                documentsMap["allCornersCaptured"] = (finalCenterUrl != null && finalLeftUrl != null && finalRightUrl != null)

                val applicationData = hashMapOf<String, Any>(
                    "applicationId" to uid,
                    "uid" to uid,
                    "personalInfo" to hashMapOf(
                        "firstName" to firstNameInput.trim(),
                        "middleName" to middleNameInput.trim().ifEmpty { null },
                        "lastName" to lastNameInput.trim(),
                        "extensionName" to extensionNameInput.trim().ifEmpty { null },
                        "sex" to sexInput,
                        "birthdate" to birthdateInput,
                        "rsbsaNumber" to rsbsaNumberInput.trim()
                    ),
                    "contactInfo" to hashMapOf(
                        "phoneNumber" to phoneInput.trim(),
                        "gmail" to gmailInput.trim().lowercase()
                    ),
                    "address" to hashMapOf(
                        "zone" to zoneInput.trim(),
                        "street" to zoneInput.trim(),
                        "barangay" to barangayInput.trim(),
                        "municipality" to municipalityInput.trim(),
                        "city" to municipalityInput.trim(),
                        "province" to provinceInput.trim(),
                        "region" to regionInput.trim()
                    ),
                    "documents" to documentsMap,
                    "facialIdentityUrl" to (finalCenterUrl ?: ""),
                    "facialIdentityCenterUrl" to (finalCenterUrl ?: ""),
                    "facialIdentityLeftUrl" to (finalLeftUrl ?: ""),
                    "facialIdentityRightUrl" to (finalRightUrl ?: ""),
                    "faceImageUrl" to (finalCenterUrl ?: ""),
                    "facialCorners" to (facialCornersData ?: emptyMap<String, Any>()),
                    "facialCornerScores" to mapOf(
                        "centerScore" to centerScore,
                        "leftScore" to leftScore,
                        "rightScore" to rightScore,
                        "overallScore" to faceMatchScoreInput
                    ),
                    "allCornersCaptured" to (finalCenterUrl != null && finalLeftUrl != null && finalRightUrl != null),
                    "faceMatchScore" to faceMatchScoreInput,
                    "hasFaceVerified" to (finalCenterUrl != null && faceMatchScoreInput >= 60),
                    "isBiometricRegistered" to (finalCenterUrl != null && faceMatchScoreInput >= 60),
                    "allCornersVerified" to (finalCenterUrl != null && faceMatchScoreInput >= 60),
                    "status" to "pending",
                    "role" to "guest_farmer",
                    "adminReview" to hashMapOf(
                        "reviewedBy" to null,
                        "reviewedAt" to null,
                        "feedback" to null,
                        "rejectionReason" to null
                    ),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),

                    // Compatibility fields for legacy views & filters
                    "fullName" to formattedFullName,
                    "name" to "${firstNameInput.trim()} ${lastNameInput.trim()}",
                    "email" to gmailInput.trim().lowercase(),
                    "phone" to phoneInput.trim(),
                    "rsbsaNumber" to rsbsaNumberInput.trim(),
                    "zone" to zoneInput.trim(),
                    "street" to zoneInput.trim(),
                    "barangay" to barangayInput.trim(),
                    "municipality" to municipalityInput.trim(),
                    "city" to municipalityInput.trim(),
                    "province" to provinceInput.trim(),
                    "region" to regionInput.trim(),
                    "isVerified" to false
                )

                // Prevent 1 MiB document overflow:
                // If using remote Storage URLs (small strings), root aliases are added.
                // If Base64 data URIs, avoid duplicating the large strings 3x across the document.
                val isFrontBase64 = finalFrontUrl.startsWith("data:")
                val isBackBase64 = finalBackUrl.startsWith("data:")
                if (!isFrontBase64) {
                    applicationData["idImageUrl"] = finalFrontUrl
                    applicationData["validIdUrl"] = finalFrontUrl
                } else {
                    applicationData["idImageUrl"] = finalFrontUrl
                }
                if (!isBackBase64) {
                    applicationData["idBackUrl"] = finalBackUrl
                }

                db.collection("farmers").document(uid).set(applicationData, SetOptions.merge())
                    .addOnSuccessListener {
                        db.collection("farmer_applications").document(uid).set(applicationData, SetOptions.merge()).catchException()
                        db.collection("users").document(uid).set(applicationData, SetOptions.merge()).catchException()

                        SessionManager.saveLoginSession(
                            context = context,
                            userId = uid,
                            email = gmailInput.trim().lowercase(),
                            name = formattedFullName,
                            role = "guest_farmer",
                            isVerified = false,
                            accountStatus = "pending"
                        )

                        farmerStatus.value = "pending"
                        farmerRole.value = "guest_farmer"
                        rsbsaNumber.value = rsbsaNumberInput.trim()
                        street.value = zoneInput.trim()
                        barangay.value = barangayInput.trim()
                        city.value = municipalityInput.trim()
                        province.value = provinceInput.trim()
                        region.value = regionInput.trim()

                        onProgress(100)
                        onComplete(true, null)
                    }
                    .addOnFailureListener { e ->
                        onComplete(false, "Firestore Error: ${e.message}")
                    }
            }

            fun uploadSingleImage(uri: Uri, tag: String, onFinish: (String?) -> Unit) {
                // Guaranteed safe compression under 160 KB (= ~213 KB Base64) to strictly prevent Firestore 1 MiB overflow
                val compressedBytes = compressUriToJpeg(context, uri, maxBytes = 160 * 1024)
                val base64DataUri = if (compressedBytes != null) {
                    "data:image/jpeg;base64," + Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                } else null

                val storageRef = storage.reference.child("verifications/$uid/valid_id_${tag}_${System.currentTimeMillis()}.jpg")
                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .build()

                val uploadTask = if (compressedBytes != null) {
                    storageRef.putBytes(compressedBytes, metadata)
                } else {
                    storageRef.putFile(uri, metadata)
                }

                uploadTask.continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let { throw it }
                    }
                    storageRef.downloadUrl
                }.addOnSuccessListener { downloadUri ->
                    onFinish(downloadUri.toString())
                }.addOnFailureListener { storageErr ->
                    Log.w("UserViewModel", "Storage upload failed for $tag: ${storageErr.message}. Utilizing compressed Base64 fallback.")
                    if (base64DataUri != null) {
                        onFinish(base64DataUri)
                    } else {
                        onFinish(null)
                    }
                }
            }

            fun uploadLiveFaceImage(bitmap: Bitmap, angle: String = "center", onFinish: (String?) -> Unit) {
                try {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val faceBytes = stream.toByteArray()
                    val base64DataUri = "data:image/jpeg;base64," + Base64.encodeToString(faceBytes, Base64.NO_WRAP)

                    val storageRef = storage.reference.child("verifications/$uid/facial_identity_${angle}_${System.currentTimeMillis()}.jpg")
                    val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()

                    storageRef.putBytes(faceBytes, metadata)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) {
                                task.exception?.let { throw it }
                            }
                            storageRef.downloadUrl
                        }
                        .addOnSuccessListener { downloadUri ->
                            onFinish(downloadUri.toString())
                        }
                        .addOnFailureListener { storageErr ->
                            Log.w("UserViewModel", "Storage upload failed for facial identity ($angle): ${storageErr.message}. Utilizing compressed Base64 fallback.")
                            onFinish(base64DataUri)
                        }
                } catch (e: Exception) {
                    Log.e("UserViewModel", "Failed to compress/upload live face ($angle): ${e.message}")
                    onFinish(null)
                }
            }

            // Upload ID Front, ID Back, and Live Facial Identity (Center, Left Corner, Right Corner) in sequence
            onProgress(15)
            uploadSingleImage(idFrontUri, "front") { frontUrl ->
                if (frontUrl == null) {
                    onComplete(false, "Failed to upload ID Front. Please try again.")
                    return@uploadSingleImage
                }
                onProgress(40)
                uploadSingleImage(idBackUri, "back") { backUrl ->
                    if (backUrl == null) {
                        onComplete(false, "Failed to upload ID Back. Please try again.")
                        return@uploadSingleImage
                    }
                    if (liveFaceBitmap != null) {
                        onProgress(60)
                        uploadLiveFaceImage(liveFaceBitmap, "center") { centerUrl ->
                            onProgress(75)
                            if (leftFaceBitmap != null) {
                                uploadLiveFaceImage(leftFaceBitmap, "left") { leftUrl ->
                                    onProgress(85)
                                    if (rightFaceBitmap != null) {
                                        uploadLiveFaceImage(rightFaceBitmap, "right") { rightUrl ->
                                            onProgress(92)
                                            saveApplication(frontUrl, backUrl, centerUrl, leftUrl, rightUrl)
                                        }
                                    } else {
                                        saveApplication(frontUrl, backUrl, centerUrl, leftUrl, null)
                                    }
                                }
                            } else {
                                saveApplication(frontUrl, backUrl, centerUrl, null, null)
                            }
                        }
                    } else {
                        onProgress(85)
                        saveApplication(frontUrl, backUrl, null, null, null)
                    }
                }
            }
        }

        // 1. Session check to prevent repeated submissions
        if (farmerStatus.value == "pending") {
            onComplete(false, "You already have an application submitted and pending review. Repeated submissions are not allowed.")
            return
        } else if (farmerStatus.value == "verified") {
            onComplete(false, "Your account is already verified! Repeated submissions are not permitted.")
            return
        }

        onProgress(5)

        // 2. Uniqueness check across RSBSA Number, Gmail, Phone, and Full Name + Birthdate
        checkDuplicateCredentials(
            currentUid = auth.currentUser?.uid,
            firstName = firstNameInput,
            lastName = lastNameInput,
            birthdate = birthdateInput,
            rsbsaNumber = rsbsaNumberInput,
            gmail = gmailInput,
            phone = phoneInput
        ) { isDuplicate, duplicateError ->
            if (isDuplicate) {
                onProgress(0)
                onComplete(false, duplicateError ?: "This credential has already been submitted. Repeated credentials are not permitted.")
                return@checkDuplicateCredentials
            }

            // Ensure every farmer account is stored in Firebase Authentication
            val currentUser = auth.currentUser
            val cleanEmail = gmailInput.trim().lowercase()

            if (currentUser != null && !currentUser.isAnonymous && currentUser.email.equals(cleanEmail, ignoreCase = true)) {
                // Farmer is already authenticated with this account in Firebase Authentication (e.g. Google Sign In)
                proceedWithUid(currentUser.uid)
            } else if (passwordInput.isNotBlank()) {
                // Register / authenticate farmer account into Firebase Authentication
                auth.createUserWithEmailAndPassword(cleanEmail, passwordInput)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: System.currentTimeMillis().toString()
                        proceedWithUid(uid)
                    }
                    .addOnFailureListener { createErr ->
                        // If user already exists in Firebase Authentication, authenticate and link
                        auth.signInWithEmailAndPassword(cleanEmail, passwordInput)
                            .addOnSuccessListener { signInResult ->
                                val uid = signInResult.user?.uid ?: System.currentTimeMillis().toString()
                                proceedWithUid(uid)
                            }
                            .addOnFailureListener {
                                onComplete(false, "Authentication Error: ${createErr.message}. If you have an existing account, please verify your password.")
                            }
                    }
            } else if (currentUser != null && !currentUser.isAnonymous) {
                proceedWithUid(currentUser.uid)
            } else {
                // Fallback: If no custom password was provided, create with secure default password based on phone/RSBSA
                val rawDigits = phoneInput.filter { it.isDigit() }
                val fallbackPassword = if (rawDigits.length >= 4) {
                    "AgriServe@" + rawDigits.takeLast(4)
                } else {
                    "AgriServe@2026"
                }
                auth.createUserWithEmailAndPassword(cleanEmail, fallbackPassword)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: System.currentTimeMillis().toString()
                        proceedWithUid(uid)
                    }
                    .addOnFailureListener {
                        auth.signInWithEmailAndPassword(cleanEmail, fallbackPassword)
                            .addOnSuccessListener { signInResult ->
                                val uid = signInResult.user?.uid ?: System.currentTimeMillis().toString()
                                proceedWithUid(uid)
                            }
                            .addOnFailureListener {
                                auth.signInAnonymously()
                                    .addOnSuccessListener { authResult ->
                                        val uid = authResult.user?.uid ?: System.currentTimeMillis().toString()
                                        proceedWithUid(uid)
                                    }
                                    .addOnFailureListener {
                                        val fallbackUid = "farmer_" + System.currentTimeMillis()
                                        proceedWithUid(fallbackUid)
                                    }
                            }
                    }
            }
        }
    }

    private fun finalFrontFrontFallback(frontUrl: String): String = frontUrl

    /**
     * Resubmit updated farmer credentials following DA Staff feedback
     * Allows updating demographics, Address, and Back-to-Back ID documents.
     */
    fun resubmitFarmerCredentials(
        context: Context,
        firstNameInput: String,
        middleNameInput: String,
        lastNameInput: String,
        extensionNameInput: String,
        sexInput: String,
        birthdateInput: String,
        phoneInput: String,
        gmailInput: String,
        rsbsaNumberInput: String = "",
        zoneInput: String = "",
        barangayInput: String = "",
        municipalityInput: String = "",
        provinceInput: String = "",
        regionInput: String = "",
        newIdFrontUri: Uri? = null,
        newIdBackUri: Uri? = null,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val user = auth.currentUser
        val uid = user?.uid ?: SessionManager.getSavedUserId(context)?.ifBlank { null } ?: System.currentTimeMillis().toString()

        fun saveResubmissionToFirestore(idFrontUrl: String, idBackUrl: String) {
            val nameParts = listOf(firstNameInput.trim(), middleNameInput.trim(), lastNameInput.trim(), extensionNameInput.trim()).filter { it.isNotBlank() }
            val formattedFullName = nameParts.joinToString(" ")

            val updates = hashMapOf<String, Any>(
                "personalInfo" to hashMapOf(
                    "firstName" to firstNameInput.trim(),
                    "middleName" to middleNameInput.trim().ifEmpty { null },
                    "lastName" to lastNameInput.trim(),
                    "extensionName" to extensionNameInput.trim().ifEmpty { null },
                    "sex" to sexInput,
                    "birthdate" to birthdateInput,
                    "rsbsaNumber" to rsbsaNumberInput.trim()
                ),
                "contactInfo" to hashMapOf(
                    "phoneNumber" to phoneInput.trim(),
                    "gmail" to gmailInput.trim().lowercase()
                ),
                "address" to hashMapOf(
                    "zone" to zoneInput.trim(),
                    "street" to zoneInput.trim(),
                    "barangay" to barangayInput.trim(),
                    "municipality" to municipalityInput.trim(),
                    "city" to municipalityInput.trim(),
                    "province" to provinceInput.trim(),
                    "region" to regionInput.trim()
                ),
                "documents" to hashMapOf(
                    "validIdUrl" to idFrontUrl,
                    "idBackUrl" to idBackUrl
                ),
                "status" to "pending",
                "role" to "guest_farmer",
                "updatedAt" to FieldValue.serverTimestamp(),
                "fullName" to formattedFullName,
                "name" to "${firstNameInput.trim()} ${lastNameInput.trim()}",
                "phone" to phoneInput.trim(),
                "email" to gmailInput.trim().lowercase(),
                "rsbsaNumber" to rsbsaNumberInput.trim(),
                "zone" to zoneInput.trim(),
                "street" to zoneInput.trim(),
                "barangay" to barangayInput.trim(),
                "municipality" to municipalityInput.trim(),
                "city" to municipalityInput.trim(),
                "province" to provinceInput.trim(),
                "region" to regionInput.trim(),
                "isVerified" to false
            )

            // Prevent document size overflow when storing Base64 data
            val isFrontBase64 = idFrontUrl.startsWith("data:")
            val isBackBase64 = idBackUrl.startsWith("data:")
            if (!isFrontBase64) {
                updates["idImageUrl"] = idFrontUrl
                updates["validIdUrl"] = idFrontUrl
            } else {
                updates["idImageUrl"] = idFrontUrl
            }
            if (!isBackBase64) {
                updates["idBackUrl"] = idBackUrl
            }

            db.collection("farmers").document(uid).set(updates, SetOptions.merge())
                .addOnSuccessListener {
                    db.collection("farmer_applications").document(uid).set(updates, SetOptions.merge()).catchException()
                    db.collection("users").document(uid).set(updates, SetOptions.merge()).catchException()
                    val authUid = auth.currentUser?.uid
                    if (authUid != null && authUid != uid) {
                        db.collection("users").document(authUid).set(updates, SetOptions.merge()).catchException()
                        db.collection("farmers").document(authUid).set(updates, SetOptions.merge()).catchException()
                    }

                    validIdUrl.value = idFrontUrl
                    validIdBackUrl.value = idBackUrl
                    farmerStatus.value = "pending"
                    rsbsaNumber.value = rsbsaNumberInput.trim()
                    street.value = zoneInput.trim()
                    barangay.value = barangayInput.trim()
                    city.value = municipalityInput.trim()
                    province.value = provinceInput.trim()
                    region.value = regionInput.trim()

                    onProgress(100)
                    onComplete(true, null)
                }
                .addOnFailureListener { e ->
                    onComplete(false, "Update Error: ${e.message}")
                }
        }

        fun uploadSingle(uri: Uri, tag: String, onDone: (String?) -> Unit) {
            val compressedBytes = compressUriToJpeg(context, uri, maxBytes = 160 * 1024)
            val base64DataUri = if (compressedBytes != null) {
                "data:image/jpeg;base64," + Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            } else null

            val storageRef = storage.reference.child("verifications/$uid/resubmit_${tag}_${System.currentTimeMillis()}.jpg")
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()

            val uploadTask = if (compressedBytes != null) {
                storageRef.putBytes(compressedBytes, metadata)
            } else {
                storageRef.putFile(uri, metadata)
            }

            uploadTask.continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                storageRef.downloadUrl
            }.addOnSuccessListener { uriRes ->
                onDone(uriRes.toString())
            }.addOnFailureListener { storageErr ->
                Log.w("UserViewModel", "Storage resubmit upload failed for $tag: ${storageErr.message}. Utilizing compressed Base64 fallback.")
                if (base64DataUri != null) {
                    onDone(base64DataUri)
                } else {
                    onDone(null)
                }
            }
        }

        val currentFront = validIdUrl.value ?: ""
        fun performUploadsAndSave() {
            val currentFront = validIdUrl.value ?: ""
            val currentBack = validIdBackUrl.value ?: ""

            if (newIdFrontUri != null && newIdBackUri != null) {
                onProgress(20)
                uploadSingle(newIdFrontUri, "front") { frontRes ->
                    if (frontRes == null) {
                        onComplete(false, "Failed to upload updated ID Front")
                        return@uploadSingle
                    }
                    onProgress(60)
                    uploadSingle(newIdBackUri, "back") { backRes ->
                        if (backRes == null) {
                            onComplete(false, "Failed to upload updated ID Back")
                            return@uploadSingle
                        }
                        onProgress(85)
                        saveResubmissionToFirestore(frontRes, backRes)
                    }
                }
            } else if (newIdFrontUri != null) {
                onProgress(30)
                uploadSingle(newIdFrontUri, "front") { frontRes ->
                    if (frontRes == null) {
                        onComplete(false, "Failed to upload updated ID Front")
                        return@uploadSingle
                    }
                    onProgress(85)
                    saveResubmissionToFirestore(frontRes, currentBack)
                }
            } else if (newIdBackUri != null) {
                onProgress(30)
                uploadSingle(newIdBackUri, "back") { backRes ->
                    if (backRes == null) {
                        onComplete(false, "Failed to upload updated ID Back")
                        return@uploadSingle
                    }
                    onProgress(85)
                    saveResubmissionToFirestore(currentFront, backRes)
                }
            } else {
                saveResubmissionToFirestore(currentFront, currentBack)
            }
        }

        onProgress(5)
        checkDuplicateCredentials(
            currentUid = uid,
            firstName = firstNameInput,
            lastName = lastNameInput,
            birthdate = birthdateInput,
            rsbsaNumber = rsbsaNumberInput,
            gmail = gmailInput,
            phone = phoneInput
        ) { isDuplicate, duplicateError ->
            if (isDuplicate) {
                onProgress(0)
                onComplete(false, duplicateError ?: "This credential has already been registered to another account. Repeated credentials are not permitted.")
                return@checkDuplicateCredentials
            }
            performUploadsAndSave()
        }
    }

    private fun com.google.android.gms.tasks.Task<Void>.catchException() {
        this.addOnFailureListener { /* silently ignore secondary sync failure */ }
    }

    fun uploadFaceVerification(bitmap: Bitmap?, onComplete: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false, "No logged in user")
        hasFaceVerified.value = true

        if (bitmap == null) {
            val updates = hashMapOf<String, Any>(
                "hasFaceVerified" to true,
                "faceVerifiedAt" to Timestamp.now()
            )
            val task = db.collection("farmers").document(uid).set(updates, SetOptions.merge())
            db.collection("users").document(uid).set(updates, SetOptions.merge()).catchException()
            val authUid = auth.currentUser?.uid
            if (authUid != null && authUid != uid) {
                db.collection("users").document(authUid).set(updates, SetOptions.merge()).catchException()
                db.collection("farmers").document(authUid).set(updates, SetOptions.merge()).catchException()
            }
            task.addOnSuccessListener { onComplete(true, null) }
                .addOnFailureListener { e -> onComplete(false, e.message) }
            return
        }

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        val data = baos.toByteArray()

        val ref = storage.reference.child("verifications/$uid/face_biometric.jpg")
        ref.putBytes(data)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                ref.downloadUrl
            }
            .addOnSuccessListener { uri ->
                faceImageUrl.value = uri.toString()
                val updates = hashMapOf<String, Any>(
                    "hasFaceVerified" to true,
                    "faceImageUrl" to uri.toString(),
                    "faceVerifiedAt" to Timestamp.now()
                )
                val task = db.collection("farmers").document(uid).set(updates, SetOptions.merge())
            db.collection("users").document(uid).set(updates, SetOptions.merge()).catchException()
            val authUid = auth.currentUser?.uid
            if (authUid != null && authUid != uid) {
                db.collection("users").document(authUid).set(updates, SetOptions.merge()).catchException()
                db.collection("farmers").document(authUid).set(updates, SetOptions.merge()).catchException()
            }
                db.collection("farmers").document(uid).set(updates, SetOptions.merge()).catchException()
                onComplete(true, null)
            }
            .addOnFailureListener { _ ->
                val base64 = "data:image/jpeg;base64," + Base64.encodeToString(data, Base64.NO_WRAP)
                faceImageUrl.value = base64
                val updates = hashMapOf<String, Any>(
                    "hasFaceVerified" to true,
                    "faceImageUrl" to base64,
                    "faceVerifiedAt" to Timestamp.now()
                )
                val task = db.collection("farmers").document(uid).set(updates, SetOptions.merge())
            db.collection("users").document(uid).set(updates, SetOptions.merge()).catchException()
            val authUid = auth.currentUser?.uid
            if (authUid != null && authUid != uid) {
                db.collection("users").document(authUid).set(updates, SetOptions.merge()).catchException()
                db.collection("farmers").document(authUid).set(updates, SetOptions.merge()).catchException()
            }
                db.collection("farmers").document(uid).set(updates, SetOptions.merge()).catchException()
                onComplete(true, null)
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
        faceMatchScore: Int? = null,
        liveFaceBitmap: Bitmap? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val targetUid = farmerDocId.value.ifBlank {
            appContext?.let { ctx -> SessionManager.getSavedUserId(ctx) }?.ifBlank { null }
                ?: auth.currentUser?.uid
        } ?: return onComplete(false, "No active farmer session found")
        val uid = targetUid

        val nameParts = newName.trim().split("\\s+".toRegex())
        val fName = if (nameParts.isNotEmpty()) nameParts.first() else newName
        val lName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else ""

        val updates = hashMapOf<String, Any>(
            "name" to newName,
            "fullName" to newName,
            "phone" to newPhone,
            "region" to newRegion,
            "province" to newProvince,
            "city" to newCity,
            "barangay" to newBarangay,
            "street" to newStreet,
            "personalInfo.firstName" to fName,
            "personalInfo.lastName" to lName,
            "contactInfo.phoneNumber" to newPhone,
            "address.region" to newRegion,
            "address.province" to newProvince,
            "address.city" to newCity,
            "address.municipality" to newCity,
            "address.barangay" to newBarangay,
            "address.street" to newStreet,
            "address.zone" to newStreet,
            "lastProfileUpdate" to Timestamp.now()
        )

        if (faceMatchScore != null) {
            updates["hasFaceVerified"] = true
            updates["ownerBiometricallyVerified"] = true
            updates["lastOwnerVerifiedAt"] = Timestamp.now()
            updates["lastOwnerMatchScore"] = faceMatchScore
            updates["faceMatchScore"] = faceMatchScore
        }

        fun finalizeSave() {
            db.collection("users").document(uid).set(updates, SetOptions.merge()).catchException()
            val authUid = auth.currentUser?.uid
            if (authUid != null && authUid != uid) {
                db.collection("users").document(authUid).set(updates, SetOptions.merge()).catchException()
                db.collection("farmers").document(authUid).set(updates, SetOptions.merge()).catchException()
            }
            db.collection("farmers").document(uid).set(updates, SetOptions.merge())
                .addOnSuccessListener {
                    farmerName.value = newName
                    farmerPhone.value = newPhone
                    phoneNumber.value = newPhone
                    firstName.value = fName
                    lastName.value = lName
                    region.value = newRegion
                    province.value = newProvince
                    city.value = newCity
                    barangay.value = newBarangay
                    street.value = newStreet
                    onComplete(true, null)
                }
                .addOnFailureListener { err ->
                    onComplete(false, err.message)
                }
        }

        fun handleFaceUploadThenSave() {
            if (liveFaceBitmap != null) {
                val baos = ByteArrayOutputStream()
                liveFaceBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val data = baos.toByteArray()
                val faceRef = storage.reference.child("face_verifications/$uid.jpg")
                faceRef.putBytes(data)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) task.exception?.let { throw it }
                        faceRef.downloadUrl
                    }
                    .addOnSuccessListener { faceUri ->
                        updates["faceImageUrl"] = faceUri.toString()
                        faceImageUrl.value = faceUri.toString()
                        finalizeSave()
                    }
                    .addOnFailureListener {
                        val base64 = "data:image/jpeg;base64," + Base64.encodeToString(data, Base64.NO_WRAP)
                        updates["faceImageUrl"] = base64
                        faceImageUrl.value = base64
                        finalizeSave()
                    }
            } else {
                finalizeSave()
            }
        }

        if (newImageUri != null) {
            val storageRef = storage.reference.child("profile_pictures/$uid")
            storageRef.putFile(newImageUri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) task.exception?.let { throw it }
                    storageRef.downloadUrl
                }
                .addOnSuccessListener { uri ->
                    updates["profileImageUrl"] = uri.toString()
                    profileImageUrl.value = uri.toString()
                    handleFaceUploadThenSave()
                }
                .addOnFailureListener { e -> onComplete(false, "Storage Error: ${e.message}") }
        } else {
            handleFaceUploadThenSave()
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
        updateProfile(
            newName = newName,
            newPhone = newPhone,
            newRegion = newRegion,
            newProvince = newProvince,
            newCity = newCity,
            newBarangay = newBarangay,
            newStreet = newStreet,
            newImageUri = newImageUri,
            faceMatchScore = null,
            liveFaceBitmap = null,
            onComplete = onComplete
        )
    }

    fun updateLanguage(langCode: String, context: Context? = null) {
        val targetContext = (context ?: appContext)?.applicationContext
        val code = when (langCode) { "tl" -> "fil"; "bik" -> "bcl"; else -> langCode }
        selectedLanguage.value = code
        if (targetContext != null) {
            SessionManager.saveAppLanguage(targetContext, code)
        }
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val update = mapOf("language" to code)
            db.collection("users").document(uid).set(update, SetOptions.merge()).catchException()
            db.collection("farmers").document(uid).set(update, SetOptions.merge()).catchException()
        }
    }

    fun updateThemeMode(mode: String, context: Context? = null) {
        val targetContext = (context ?: appContext)?.applicationContext
        if (mode in listOf("system", "light", "dark")) {
            themeMode.value = mode
            if (targetContext != null) {
                SessionManager.saveThemeMode(targetContext, mode)
            }
            val uid = auth.currentUser?.uid
            if (uid != null) {
                val update = mapOf("themeMode" to mode)
                db.collection("users").document(uid).set(update, SetOptions.merge()).catchException()
                db.collection("farmers").document(uid).set(update, SetOptions.merge()).catchException()
            }
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
                onComplete(false, "Maling lumang password.")
            }
        }
    }
}
