package com.ahmadtalal.i210734

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CreateAccountActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_account)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val daySpinner = findViewById<Spinner>(R.id.daySpinner)

        val days = (1..31).toList()

        val dayAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            days
        )

        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item)
        daySpinner.adapter = dayAdapter


        val monthSpinner = findViewById<Spinner>(R.id.monthSpinner)

        val months = arrayOf(
            "January", "February", "March", "April",
            "May", "June", "July", "August",
            "September", "October", "November", "December"
        )

        val monthAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            months
        )

        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item)
        monthSpinner.adapter = monthAdapter


        val yearSpinner = findViewById<Spinner>(R.id.yearSpinner)

        val years = (1950..2026).toList()

        val yearAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            years
        )

        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item)
        yearSpinner.adapter = yearAdapter


        val loginLink = findViewById<TextView>(R.id.loginLink)

        loginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)

            startActivity(intent)
        }

        val backToLoginBtn = findViewById<Button>(R.id.backToLoginBtn)

        backToLoginBtn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)

            startActivity(intent)
        }
    }
}