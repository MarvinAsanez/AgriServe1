package com.group7.agriserve.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.group7.agriserve.data.model.Announcement

class AnnouncementViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private var snapshotListener: ListenerRegistration? = null

    var announcements = mutableStateOf<List<Announcement>>(emptyList())
        private set

    var emergencyAlerts = mutableStateOf<List<Announcement>>(emptyList())
        private set

    var selectedAnnouncement = mutableStateOf<Announcement?>(null)

    var isLoading = mutableStateOf(false)
        private set

    var errorMessage = mutableStateOf<String?>(null)
        private set

    init {
        listenToAnnouncements()
    }

    fun listenToAnnouncements() {
        isLoading.value = true
        snapshotListener?.remove()

        try {
            snapshotListener = db.collection("announcements")
                .addSnapshotListener { snapshot, error ->
                    isLoading.value = false
                    if (error != null) {
                        Log.e("AnnouncementVM", "Error listening to announcements", error)
                        errorMessage.value = error.message
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val list = mutableListOf<Announcement>()
                        for (doc in snapshot.documents) {
                            try {
                                val item = doc.toObject(Announcement::class.java)?.copy(id = doc.id)
                                if (item != null && item.isActive) {
                                    list.add(item)
                                }
                            } catch (e: Exception) {
                                Log.w("AnnouncementVM", "Error parsing announcement ${doc.id}", e)
                            }
                        }

                        val sortedList = list.sortedByDescending { it.createdAt?.seconds ?: 0L }
                        announcements.value = sortedList
                        emergencyAlerts.value = sortedList.filter { it.severity.equals("Emergency", ignoreCase = true) }
                    }
                }
        } catch (e: Exception) {
            isLoading.value = false
            Log.e("AnnouncementVM", "Failed to setup announcements listener", e)
        }
    }

    fun openAnnouncementDetails(announcement: Announcement) {
        selectedAnnouncement.value = announcement
    }

    fun closeAnnouncementDetails() {
        selectedAnnouncement.value = null
    }

    override fun onCleared() {
        super.onCleared()
        snapshotListener?.remove()
    }
}

