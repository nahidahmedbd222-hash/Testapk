package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "converted_videos")
data class ConvertedVideo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val beforeVideoPath: String,
    val afterVideoPath: String?,
    val styleName: String,
    val styleCategory: String, // "Anime" or "3D Cartoon"
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isDemo: Boolean = false,
    val durationSeconds: Int = 0
)
