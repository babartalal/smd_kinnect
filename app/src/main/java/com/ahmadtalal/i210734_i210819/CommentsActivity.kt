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
    private var commentsQuery: LiveQuery? = null

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
        if (postId.isNotEmpty()) {
            commentsQuery = createCommentsQuery()
        }
    }

    override fun onStart() {
        super.onStart()
        commentsQuery?.start()
    }

    override fun onStop() {
        super.onStop()
        commentsQuery?.stop()
    }

    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter(this, commentList, onLikeChanged = { commentsQuery?.refresh() }) { parentComment, authorName ->
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

    /** Keeps the comment list in sync with the server while the screen is open. */
    private fun createCommentsQuery(): LiveQuery =
        LiveQuery("comments/list.php", mapOf("post_id" to postId), intervalMs = 4000L) { result ->
            val comments = result.arr()
            val rawComments = mutableListOf<Comment>()
            for (i in 0 until comments.length()) {
                rawComments.add(Comment.fromJson(comments.getJSONObject(i)))
            }

            binding.commentTotalCountTxt.text = "${rawComments.size} comments"

            commentList.clear()
            commentList.addAll(organizeThreadedComments(rawComments))
            commentAdapter.updateData(commentList)
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

        if (!Session.isLoggedIn) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val body = mapOf(
            "post_id" to postId,
            "text" to text,
            "image" to selectedCommentImageBase64,
            "replyToCommentId" to activeReplyToCommentId,
            "replyToAuthorName" to activeReplyToCommentId?.let { activeReplyToAuthorName.orEmpty() },
            "clientId" to newClientId()
        )

        binding.commentSendBtn.isEnabled = false
        Api.post("comments/create.php", body, queueIfOffline = true) { result ->
            if (isFinishing || isDestroyed) return@post
            binding.commentSendBtn.isEnabled = true
            if (result.ok || result.queued) {
                binding.commentInputText.setText("")
                selectedCommentImageBase64 = null
                resetReplyState()
                if (result.ok) {
                    commentsQuery?.refresh()
                } else {
                    // Queued in pending_actions: show it now, marked as pending.
                    commentList.add(
                        Comment(
                            postId = postId,
                            authorId = Session.userId,
                            text = text,
                            author = LocalDb.get().getUser(Session.userId)?.let { User.fromJson(it) },
                            pending = true
                        )
                    )
                    commentAdapter.updateData(commentList)
                    Toast.makeText(this, result.error, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Failed to post comment: ${result.error}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
