package com.josejordan.alarma

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.josejordan.alarma.databinding.ListItemImageBinding
import java.io.File
class ImageListAdapter(
    private val imageFiles: List<File>,
    private val onImageClicked: (File) -> Unit
) : RecyclerView.Adapter<ImageListAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(val binding: ListItemImageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(imageFile: File) {
            binding.imageName.text = imageFile.name
            Glide.with(binding.root).load(imageFile).into(binding.imageThumbnail)
            binding.root.setOnClickListener { onImageClicked(imageFile) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ListItemImageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imageFiles[position])
    }

    override fun getItemCount() = imageFiles.size
}
