package com.group7.agriserve.viewmodel

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.group7.agriserve.util.ContentSafetyModerator
import java.util.UUID

data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: String = "Farmer",
    val isAuthorVerified: Boolean = false,
    val category: String = "General",
    val content: String = "",
    val imageUrl: String? = null,
    val timestamp: Long = 0,
    val likes: List<String> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val reportCount: Int = 0,
    val isFlagged: Boolean = false
)

data class Comment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: String = "Farmer",
    val isAuthorVerified: Boolean = false,
    val text: String = "",
    val timestamp: Long = 0
)

class CommunityViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    var posts = mutableStateOf<List<Post>>(emptyList())
        private set

    var selectedCategory = mutableStateOf("All")
    var searchQuery = mutableStateOf("")
    var hiddenPostIds = mutableStateOf<Set<String>>(emptySet())
    var isUploadingPost = mutableStateOf(false)

    init {
        fetchPosts()
    }

    private fun fetchPosts() {
        db.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    posts.value = snapshot.toObjects(Post::class.java)
                }
            }
    }

    /**
     * Creates a new community post with strict real-time anti-violence inspection.
     */
    fun createPost(
        content: String,
        authorName: String,
        category: String = "General",
        isAuthorVerified: Boolean = false,
        imageUri: Uri? = null,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        // Strict real-time anti-violence content safety filter
        val moderation = ContentSafetyModerator.checkContent(content)
        if (!moderation.isSafe) {
            onResult(false, moderation.reason ?: "Violent or harmful language is not allowed in the community.")
            return
        }

        val uid = auth.currentUser?.uid ?: "anonymous"
        val postId = db.collection("posts").document().id
        isUploadingPost.value = true

        if (imageUri != null) {
            val imgRef = storage.reference.child("community_posts/${postId}_${UUID.randomUUID()}.jpg")
            imgRef.putFile(imageUri)
                .continueWithTask { it.result?.storage?.downloadUrl }
                .addOnSuccessListener { downloadUrl ->
                    savePostDocument(
                        postId = postId,
                        uid = uid,
                        authorName = authorName,
                        category = category,
                        content = content,
                        imageUrl = downloadUrl.toString(),
                        isAuthorVerified = isAuthorVerified,
                        onResult = onResult
                    )
                }
                .addOnFailureListener {
                    // Fallback to text-only if image upload fails
                    savePostDocument(
                        postId = postId,
                        uid = uid,
                        authorName = authorName,
                        category = category,
                        content = content,
                        imageUrl = null,
                        isAuthorVerified = isAuthorVerified,
                        onResult = onResult
                    )
                }
        } else {
            savePostDocument(
                postId = postId,
                uid = uid,
                authorName = authorName,
                category = category,
                content = content,
                imageUrl = null,
                isAuthorVerified = isAuthorVerified,
                onResult = onResult
            )
        }
    }

    private fun savePostDocument(
        postId: String,
        uid: String,
        authorName: String,
        category: String,
        content: String,
        imageUrl: String?,
        isAuthorVerified: Boolean,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        val post = Post(
            id = postId,
            authorId = uid,
            authorName = authorName,
            authorRole = if (isAuthorVerified) "Verified Farmer" else "Farmer",
            isAuthorVerified = isAuthorVerified,
            category = category,
            content = content,
            imageUrl = imageUrl,
            timestamp = System.currentTimeMillis()
        )

        db.collection("posts").document(postId).set(post)
            .addOnSuccessListener {
                isUploadingPost.value = false
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                isUploadingPost.value = false
                onResult(false, e.localizedMessage ?: "Failed to publish post")
            }
    }

    /**
     * Adds a comment with strict real-time anti-violence inspection.
     */
    fun addComment(
        postId: String,
        text: String,
        authorName: String,
        isAuthorVerified: Boolean = false,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        // Strict anti-violence inspection
        val moderation = ContentSafetyModerator.checkContent(text)
        if (!moderation.isSafe) {
            onResult(false, moderation.reason ?: "Violent or harmful comments are not permitted.")
            return
        }

        val uid = auth.currentUser?.uid ?: "anonymous"
        val comment = Comment(
            id = UUID.randomUUID().toString(),
            authorId = uid,
            authorName = authorName,
            authorRole = if (isAuthorVerified) "Verified Farmer" else "Farmer",
            isAuthorVerified = isAuthorVerified,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        db.collection("posts").document(postId)
            .update("comments", FieldValue.arrayUnion(comment))
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.localizedMessage) }
    }

    /**
     * Toggles like on a post.
     */
    fun toggleLike(postId: String) {
        val uid = auth.currentUser?.uid ?: return
        val postRef = db.collection("posts").document(postId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(postRef)
            @Suppress("UNCHECKED_CAST")
            val likes = snapshot.get("likes") as? List<String> ?: emptyList()

            if (likes.contains(uid)) {
                transaction.update(postRef, "likes", FieldValue.arrayRemove(uid))
            } else {
                transaction.update(postRef, "likes", FieldValue.arrayUnion(uid))
            }
        }
    }

    /**
     * Reports a post for violent or prohibited content.
     */
    fun reportPost(postId: String, reason: String, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: "anonymous"
        val report = hashMapOf(
            "postId" to postId,
            "reportedBy" to uid,
            "reason" to reason,
            "timestamp" to System.currentTimeMillis(),
            "status" to "Pending Admin Review"
        )

        db.collection("community_reports").add(report)
        db.collection("posts").document(postId).update("reportCount", FieldValue.increment(1))

        // Hide post locally for the reporter
        hiddenPostIds.value = hiddenPostIds.value + postId
        onComplete()
    }

    /**
     * Deletes a post created by the current user.
     */
    fun deletePost(postId: String, onComplete: () -> Unit) {
        db.collection("posts").document(postId).delete()
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onComplete() }
    }
}
