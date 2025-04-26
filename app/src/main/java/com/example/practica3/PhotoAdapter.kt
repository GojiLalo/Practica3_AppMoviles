package com.example.practica3

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class PhotoAdapter(private val photos: List<File>, private val onClick: (File) -> Unit) :
    RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoFile = photos[position]
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        holder.imageView.setImageBitmap(bitmap)
        holder.itemView.setOnClickListener { onClick(photoFile) }
    }

    override fun getItemCount(): Int = photos.size
}