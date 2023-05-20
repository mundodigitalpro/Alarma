package com.josejordan.alarma

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.josejordan.alarma.databinding.ActivityImageListBinding
import java.io.File

class ImageListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImageListBinding
    private lateinit var imageFiles: MutableList<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityImageListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        imageFiles = (applicationContext.getExternalFilesDir(null)?.listFiles() ?: emptyArray()).toMutableList()
        binding.recyclerView.adapter = ImageListAdapter(imageFiles, { imageFile ->
            val intent = Intent(this, ImageDetailActivity::class.java).apply {
                putExtra("image_path", imageFile.path)
            }
            startActivity(intent)
        }) {
            imageFiles = (applicationContext.getExternalFilesDir(null)?.listFiles() ?: emptyArray()).toMutableList()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_image_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                deleteSelectedImages()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteSelectedImages() {
        val adapter = binding.recyclerView.adapter as? ImageListAdapter ?: return
        val selectedViews = mutableListOf<View>()
        for (i in 0 until binding.recyclerView.childCount) {
            val view = binding.recyclerView.getChildAt(i)
            if (view.isSelected) {
                selectedViews.add(view)
            }
        }
        for (view in selectedViews) {
            val viewHolder = binding.recyclerView.getChildViewHolder(view) as ImageListAdapter.ImageViewHolder
            adapter.deleteItem(viewHolder.adapterPosition)
        }
    }
}