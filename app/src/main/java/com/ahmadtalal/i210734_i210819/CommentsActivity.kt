package com.ahmadtalal.i210734_i210819

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadtalal.i210734_i210819.databinding.ActivityCommentsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream
import java.io.InputStream

class CommentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommentsBinding
    private lateinit var commentAdapter: CommentAdapter

    private var postId: String = ""
    private var activeReplyToCommentId: String? = null
    private var activeReplyToAuthorName: String? = null
    private var selectedCommentImageBase64: String? = null

    private val commentList = mutableListOf<Comment>()
    private val currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val dbRef = FirebaseDatabase.getInstance().reference

    private val pickCommentImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processCommentImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCommentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        postId = intent.getStringExtra("postId").orEmpty()
        binding.commentSectionBackBtn.setOnClickListener { finish() }

        setupRecyclerView()
        setupClickListeners()
        loadComments()
    }

    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter(this, commentList) { parentComment, authorName ->
            activeReplyToCommentId = parentComment.commentId
            activeReplyToAuthorName = authorName

            binding.replyingToAuthorTxt.text = authorName
            binding.replyingToBar.visibility = View.VISIBLE
            binding.commentInputText.requestFocus()
        }
        binding.commentsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.commentsRecyclerView.adapter = commentAdapter
    }

    private fun setupClickListeners() {
        binding.cancelReplyBtn.setOnClickListener {
            resetReplyState()
        }

        binding.commentAttachPhotoBtn.setOnClickListener {
            pickCommentImageLauncher.launch("image/*")
        }

        binding.commentSendBtn.setOnClickListener {
            postComment()
        }
    }

    private fun resetReplyState() {
        activeReplyToCommentId = null
        activeReplyToAuthorName = null
        binding.replyingToBar.visibility = View.GONE
    }

    private fun processCommentImage(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                val scaledBitmap = scaleBitmap(originalBitmap, 800)
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                val byteArray = outputStream.toByteArray()
                selectedCommentImageBase64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                Toast.makeText(this, "Image attached to comment", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to attach image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (ratio > 1) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun loadComments() {
        if (postId.isEmpty()) return

        dbRef.child("posts").child(postId).child("comments")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val rawComments = mutableListOf<Comment>()
                    for (child in snapshot.children) {
                        val comment = child.getValue(Comment::class.java)
                        if (comment != null) {
                            rawComments.add(comment)
                        }
                    }

                    binding.commentTotalCountTxt.text = "${rawComments.size} comments"
                    commentList.clear()
                    commentList.addAll(organizeThreadedComments(rawComments))
                    commentAdapter.updateData(commentList)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun organizeThreadedComments(allComments: List<Comment>): List<Comment> {
        val result = mutableListOf<Comment>()
        val parents = allComments.filter { it.replyToCommentId.isEmpty() }.sortedBy { it.timestamp }
        val repliesByParent = allComments.filter { it.replyToCommentId.isNotEmpty() }.groupBy { it.replyToCommentId }

        for (parent in parents) {
            result.add(parent)
            repliesByParent[parent.commentId]?.sortedBy { it.timestamp }?.let { replies ->
                result.addAll(replies)
            }
        }
        return result
    }

    private fun postComment() {
        val text = binding.commentInputText.text.toString().trim()

        if (text.isEmpty() && selectedCommentImageBase64.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter a comment or attach an image", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val commentId = dbRef.child("posts").child(postId).child("comments").push().key ?: return

        val commentMap = hashMapOf<String, Any>(
            "commentId" to commentId,
            "postId" to postId,
            "authorId" to currentUserId,
            "text" to text,
            "timestamp" to ServerValue.TIMESTAMP
        )

        selectedCommentImageBase64?.let { commentMap["imageBase64"] = it }
        activeReplyToCommentId?.let {
            commentMap["replyToCommentId"] = it
            commentMap["replyToAuthorName"] = activeReplyToAuthorName.orEmpty()
        }

        dbRef.child("posts").child(postId).child("comments").child(commentId).setValue(commentMap)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    binding.commentInputText.setText("")
                    selectedCommentImageBase64 = null
                    resetReplyState()
                } else {
                    Toast.makeText(this, "Failed to post comment: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
