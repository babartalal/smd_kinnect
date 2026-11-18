package com.ahmadtalal.i210734_i210819

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val closeBtnPhotoPicker = findViewById<Button>(R.id.closeBtnPhotoPicker)
        closeBtnPhotoPicker.setOnClickListener {
            finish()
        }


        val recyclerView = findViewById<RecyclerView>(R.id.photoGridRecyclerView)


        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = layoutInflater.inflate(R.layout.item_photo_grid, parent, false)
                // Square tiles: a quarter of the grid's width.
                if (parent.width > 0) view.layoutParams.height = parent.width / 4
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun getItemCount(): Int {
                return 16
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                // Sample selection state from the design: tiles 1 and 6 picked, tile 7 is a video.
                val overlay = holder.itemView.findViewById<View>(R.id.gridOverlay)
                val indicator = holder.itemView.findViewById<TextView>(R.id.selectionIndicator)
                val duration = holder.itemView.findViewById<TextView>(R.id.gridDuration)
                val order = when (position) {
                    0 -> "1"
                    5 -> "2"
                    else -> ""
                }
                indicator.text = order
                indicator.setBackgroundResource(
                    if (order.isEmpty()) R.drawable.story_k_bg_select_ring else R.drawable.story_k_bg_select_badge
                )
                if (position == 0) {
                    overlay.setBackgroundResource(R.drawable.story_k_bg_tile_selected)
                } else {
                    overlay.background = null
                }
                indicator.visibility = if (position == 6) View.INVISIBLE else View.VISIBLE
                duration.visibility = if (position == 6) View.VISIBLE else View.GONE
            }
        }
    }
}