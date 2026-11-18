package com.ahmadtalal.i210734_i210819

import android.graphics.Bitmap
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Name to show for a user: full name, else first + last, else username. */
fun User.displayName(): String =
    name.ifEmpty { "$firstName $lastName".trim().ifEmpty { username } }

fun initialsOf(name: String): String =
    name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifEmpty { "U" }

/**
 * Load an image stored on the server (a path such as "uploads/abc.jpg").
 * Glide caches it on disk, so pictures already seen also show while offline.
 */
fun ImageView.loadRemote(path: String) {
    if (path.startsWith("drawable:")) {
        // A picture bundled in the app (used by the sample content).
        val resId = resources.getIdentifier(path.removePrefix("drawable:"), "drawable", context.packageName)
        Glide.with(context.applicationContext).clear(this)
        if (resId != 0) setImageResource(resId)
        return
    }
    Glide.with(context.applicationContext).load(Api.imageUrl(path)).into(this)
}

/** Show the user's profile picture, or their initials when they have none. */
fun bindAvatar(profilePic: String, name: String, imageView: ImageView, initialsView: TextView) {
    if (profilePic.isNotEmpty()) {
        imageView.visibility = View.VISIBLE
        initialsView.visibility = View.GONE
        imageView.loadRemote(profilePic)
    } else {
        imageView.visibility = View.GONE
        initialsView.visibility = View.VISIBLE
        initialsView.text = initialsOf(name)
    }
}

/** JPEG-compress a bitmap and Base64-encode it for upload to the web services. */
fun Bitmap.toBase64Jpeg(quality: Int): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

/** Id sent with a new post/comment/message/story so a retried upload is stored only once. */
fun newClientId(): String = UUID.randomUUID().toString()
