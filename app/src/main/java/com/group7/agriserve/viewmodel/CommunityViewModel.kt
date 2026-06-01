package com.group7.agriserve.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val content: String = "",
    val timestamp: Long = 0,
    val likes: List<String> = emptyList(),
    val comments: List<Comment> = emptyList()
)

data class Comment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val timestamp: Long = 0
)

class CommunityViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    var posts = mutableStateOf<List<Post>>(emptyList())
        private set

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

    fun createPost(content: String, authorName: String) {
        val uid = auth.currentUser?.uid ?: return
        val postId = db.collection("posts").document().id
        val post = Post(
            id = postId,
            authorId = uid,
            authorName = authorName,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        db.collection("posts").document(postId).set(post)
    }

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
        }.addOnFailureListener { _ ->
            // Log error
        }
    }

    fun addComment(postId: String, text: String, authorName: String) {
        val uid = auth.currentUser?.uid ?: return
        val comment = Comment(
            id = db.collection("posts").document().id,
            authorId = uid,
            authorName = authorName,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        db.collection("posts").document(postId).update("comments", FieldValue.arrayUnion(comment))
    }
}
