package com.ahmadtalal.i210734_i210819

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class NotificationsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // The sample notification rows mark names with <b>…</b> in the layout.
        val notificationTexts = intArrayOf(
            R.id.notifText1, R.id.notifText2, R.id.notifText3,
            R.id.notifText4, R.id.notifText5, R.id.notifText6
        )
        for (id in notificationTexts) {
            val textView = findViewById<TextView>(id)
            textView.text = HtmlCompat.fromHtml(textView.text.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        }

        val notificationsSearchBtn = findViewById<View>(R.id.notificationsSearchBtn)

        notificationsSearchBtn.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.notificationsChatsBtn).setOnClickListener {
            startActivity(Intent(this, ChatsActivity::class.java))
        }

        val notificationsHomeBtn = findViewById<View>(R.id.notificationsHomeBtn)

        notificationsHomeBtn.setOnClickListener {
            finish()
        }

        val notificationFriendsBtn = findViewById<View>(R.id.notificationFriendsBtn)

        notificationFriendsBtn.setOnClickListener {
            val intent = Intent(this, FriendsActivity::class.java)
            startActivity(intent)
            finish()
        }

        val notificationMarketplaceBtn = findViewById<View>(R.id.notificationMarketplaceBtn)

        notificationMarketplaceBtn.setOnClickListener {
            val intent = Intent(this, MarketplaceActivity::class.java)
            startActivity(intent)
            finish()
        }

        val notificationsMenuBtn = findViewById<View>(R.id.notificationsMenuBtn)
        notificationsMenuBtn?.setOnClickListener {
            val intent = Intent(this, MenuActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
