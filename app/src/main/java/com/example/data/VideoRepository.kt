package com.example.data

import kotlinx.coroutines.flow.Flow

class VideoRepository(private val videoDao: VideoDao) {
    val allVideos: Flow<List<ConvertedVideo>> = videoDao.getAllVideos()
    val favoriteVideos: Flow<List<ConvertedVideo>> = videoDao.getFavoriteVideos()

    suspend fun insert(video: ConvertedVideo): Long {
        return videoDao.insertVideo(video)
    }

    suspend fun update(video: ConvertedVideo) {
        videoDao.updateVideo(video)
    }

    suspend fun delete(video: ConvertedVideo) {
        videoDao.deleteVideo(video)
    }

    suspend fun deleteById(id: Int) {
        videoDao.deleteById(id)
    }
}
