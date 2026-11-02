package com.ahmadtalal.i210734

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView

class PhotoPickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_photo_picker)

        val mainView = findViewById<View>(R.id.main)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        findViewById<View>(R.id.closeBtnPhotoPicker)?.setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.doneBtn)?.setOnClickListener {
            finish()
        }

        val colors = arrayOf(
            "#C2B299", "#8FA3A3", "#A594B8", "#7A93B2",
            "#7C986F", "#C49A8D", "#2D3C4D", "#8FA3A3",
            "#A594B8", "#C2B299", "#7C986F", "#7A93B2",
            "#C49A8D", "#2D3C4D", "#7A93B2", "#C2B299"
        )

        val recyclerView = findViewById<RecyclerView>(R.id.photoGridRecyclerView)
        recyclerView?.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = layoutInflater.inflate(R.layout.item_photo_grid, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun getItemCount(): Int = 16

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val bg = holder.itemView.findViewById<View>(R.id.gridBg)
                val badge = holder.itemView.findViewById<TextView>(R.id.badgeNumber)
                val videoTime = holder.itemView.findViewById<TextView>(R.id.videoTime)

                bg?.setBackgroundColor(android.graphics.Color.parseColor(colors[position % colors.size]))

                when (position) {
                    0 -> {
                        badge?.visibility = View.VISIBLE
                        badge?.text = "1"
                        videoTime?.visibility = View.GONE
                    }
                    5 -> {
                        badge?.visibility = View.VISIBLE
                        badge?.text = "2"
                        videoTime?.visibility = View.VISIBLE
                        videoTime?.text = "0:23"
                    }
                    else -> {
                        badge?.visibility = View.GONE
                        videoTime?.visibility = View.GONE
                    }
                }
            }
        }
    }
}