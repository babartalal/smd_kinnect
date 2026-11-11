package com.ahmadtalal.i210734_i210819

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ahmadtalal.i210734_i210819.databinding.ItemSelectedPhotoBinding

class SelectedPhotoAdapter(
    private val context: Context,
    private val photoList: MutableList<Bitmap>,
    private val onPhotoRemoved: (Int) -> Unit
) : RecyclerView.Adapter<SelectedPhotoAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemSelectedPhotoBinding.inflate(LayoutInflater.from(context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val bitmap = photoList[position]
        Glide.with(context).load(bitmap).into(holder.binding.selectedPhotoImageView)
        holder.binding.removePhotoBtn.setOnClickListener {
            onPhotoRemoved(position)
        }
    }

    override fun getItemCount(): Int = photoList.size

    inner class PhotoViewHolder(val binding: ItemSelectedPhotoBinding) :
        RecyclerView.ViewHolder(binding.root)
}
