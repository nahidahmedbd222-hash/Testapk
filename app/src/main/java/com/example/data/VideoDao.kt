package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM converted_videos ORDER BY timestamp DESC")
    fun getAllVideos(): Flow<List<ConvertedVideo>>

    @Query("SELECT * FROM converted_videos WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteVideos(): Flow<List<ConvertedVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: ConvertedVideo): Long

    @Update
    suspend fun updateVideo(video: ConvertedVideo)

    @Delete
    suspend fun deleteVideo(video: ConvertedVideo)

    @Query("DELETE FROM converted_videos WHERE id = :id")
    suspend fun deleteById(id: Int)
}
