package com.josejordan.alarma

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.bumptech.glide.Glide
import com.josejordan.alarma.databinding.ActivityImageDetailBinding

class ImageDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityImageDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imagePath = intent.getStringExtra("image_path")
        Glide.with(this).load(imagePath).into(binding.imageView)
    }
}
