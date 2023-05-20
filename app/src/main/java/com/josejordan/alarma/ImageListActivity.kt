package com.josejordan.alarma

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.josejordan.alarma.databinding.ActivityImageListBinding

class ImageListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityImageListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageFiles = applicationContext.getExternalFilesDir(null)?.listFiles() ?: emptyArray()
        binding.recyclerView.adapter = ImageListAdapter(imageFiles.toList()) { imageFile ->
            val intent = Intent(this, ImageDetailActivity::class.java).apply {
                putExtra("image_path", imageFile.path)
            }
            startActivity(intent)
        }
    }
}
