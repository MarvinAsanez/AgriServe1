package com.group7.agriserve.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

/**
 * Coordinates offline-first image compression and automatic background
 * synchronization once internet connectivity is restored.
 */
object OfflineSyncManager {
    private const val TAG = "OfflineSyncManager"

    @Volatile
    private var isRegistered = false

    fun startMonitoring(context: Context) {
        if (isRegistered) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d(TAG, "Network restored; flushing pending Firestore offline writes...")
                    syncQueuedSubmissions(appContext)
                }
            })
            isRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register network callback: ${e.message}")
        }
    }

    fun syncQueuedSubmissions(context: Context) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: SessionManager.getSavedUserId(context)
            ?: return

        db.waitForPendingWrites()
            .addOnSuccessListener {
                val syncedUpdate = mapOf(
                    "isOffline" to false,
                    "syncStatus" to "Synced"
                )
                listOf("requests", "reports", "farms").forEach { collection ->
                    db.collection(collection)
                        .whereEqualTo("userId", uid)
                        .whereEqualTo("syncStatus", "Queued Offline")
                        .get()
                        .addOnSuccessListener { snap ->
                            for (doc in snap.documents) {
                                doc.reference.update(syncedUpdate)
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "waitForPendingWrites note: ${e.message}")
            }
    }

    /**
     * Compresses an image URI into a compact Base64 JPEG data URI suitable for
     * offline Firestore persistence when Firebase Storage is unreachable.
     */
    fun compressUriToDataUri(context: Context, uri: Uri, maxDim: Int = 800, quality: Int = 60): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * ratio).toInt().coerceAtLeast(1),
                    (original.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                original
            }
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compress offline image: ${e.message}")
            null
        }
    }
}
