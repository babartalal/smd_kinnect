package com.ahmadtalal.i210734_i210819

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

object FirebaseManager {
    val auth: FirebaseAuth by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            throw IllegalStateException("Firebase Auth initialization failed. Ensure google-services.json is configured.", e)
        }
    }

    val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance()
    }

    val storage: FirebaseStorage by lazy {
        FirebaseStorage.getInstance()
    }

    val currentUserId: String?
        get() = auth.currentUser?.uid
}
