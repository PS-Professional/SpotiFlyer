/*
 * Copyright (C)  2020  Shabinder Singh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.shabinder.spotiflyer.ui.spotify

import android.os.Environment
import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import com.shabinder.spotiflyer.database.DatabaseDAO
import com.shabinder.spotiflyer.database.DownloadRecord
import com.shabinder.spotiflyer.models.DownloadStatus
import com.shabinder.spotiflyer.models.TrackDetails
import com.shabinder.spotiflyer.models.spotify.*
import com.shabinder.spotiflyer.networking.SpotifyService
import com.shabinder.spotiflyer.utils.BaseViewModel
import com.shabinder.spotiflyer.utils.Provider
import com.shabinder.spotiflyer.utils.finalOutputDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SpotifyViewModel @ViewModelInject constructor(val databaseDAO: DatabaseDAO) : BaseViewModel(){

    override var folderType:String = ""
    override var subFolder:String = ""

    var spotifyService : SpotifyService? = null

    fun spotifySearch(type:String,link: String){
        when (type) {
            "track" -> {
                uiScope.launch {
                    val trackObject = getTrackDetails(link)
                    folderType = "Tracks"
                    val tempTrackList = mutableListOf<Track>()
                    if(File(finalOutputDir(trackObject?.name!!,folderType,subFolder)).exists()){//Download Already Present!!
                        trackObject.downloaded = DownloadStatus.Downloaded
                    }
                    tempTrackList.add(trackObject)
                    trackList.value = tempTrackList.toTrackDetailsList()
                    title.value = trackObject.name
                    coverUrl.value = trackObject.album!!.images?.get(0)!!.url!!
                    withContext(Dispatchers.IO){
                        databaseDAO.insert(DownloadRecord(
                            type = "Track",
                            name = title.value!!,
                            link = "https://open.spotify.com/$type/$link",
                            coverUrl = coverUrl.value!!,
                            totalFiles = tempTrackList.size,
                            downloaded = trackObject.downloaded == DownloadStatus.Downloaded,
                            directory = finalOutputDir(trackObject.name!!,folderType,subFolder)
                        ))
                    }
                }
            }

            "album" -> {
                uiScope.launch {
                    val albumObject = getAlbumDetails(link)
                    folderType = "Albums"
                    subFolder = albumObject?.name.toString()
                    val tempTrackList = mutableListOf<Track>()
                    albumObject?.tracks?.items?.forEach {
                        if(File(finalOutputDir(it.name!!,folderType,subFolder)).exists()){//Download Already Present!!
                            it.downloaded = DownloadStatus.Downloaded
                        }
                        it.album = Album(images = listOf(Image(url = albumObject.images?.get(0)?.url)))
                        tempTrackList.add(it)
                    }
                    trackList.value = tempTrackList.toTrackDetailsList()
                    title.value = albumObject?.name
                    coverUrl.value = albumObject?.images?.get(0)?.url
                    withContext(Dispatchers.IO){
                        databaseDAO.insert(DownloadRecord(
                            type = "Album",
                            name = title.value!!,
                            link = "https://open.spotify.com/$type/$link",
                            coverUrl = coverUrl.value.toString(),
                            totalFiles = tempTrackList.size,
                            downloaded = File(finalOutputDir(type = folderType,subFolder = subFolder)).listFiles()?.size == tempTrackList.size,
                            directory = finalOutputDir(type = folderType,subFolder = subFolder)
                        ))
                    }
                }
            }

            "playlist" -> {
                uiScope.launch {
                    val playlistObject = getPlaylistDetails(link)
                    folderType = "Playlists"
                    subFolder = playlistObject?.name.toString()
                    val tempTrackList = mutableListOf<Track>()
                    Log.i("Tracks Fetched",playlistObject?.tracks?.items?.size.toString())
                    playlistObject?.tracks?.items?.forEach {
                        it.track?.let {
                                it1 -> if(File(finalOutputDir(it1.name!!,folderType,subFolder)).exists()){//Download Already Present!!
                            it1.downloaded = DownloadStatus.Downloaded
                        }
                            tempTrackList.add(it1)
                        }
                    }
                    var moreTracksAvailable = !playlistObject?.tracks?.next.isNullOrBlank()

                    while(moreTracksAvailable){
                        //Check For More Tracks If available
                        val moreTracks = getPlaylistTrackDetails(link,offset = tempTrackList.size)
                        moreTracks?.items?.forEach{
                            it.track?.let { it1 -> tempTrackList.add(it1) }
                        }
                        moreTracksAvailable = !moreTracks?.next.isNullOrBlank()
                    }
                    Log.i("Total Tracks Fetched",tempTrackList.size.toString())
                    trackList.value = tempTrackList.toTrackDetailsList()
                    title.value = playlistObject?.name
                    coverUrl.value =  playlistObject?.images?.get(0)?.url.toString()
                    withContext(Dispatchers.IO){
                        databaseDAO.insert(DownloadRecord(
                            type = "Playlist",
                            name = title.value.toString(),
                            link = "https://open.spotify.com/$type/$link",
                            coverUrl = coverUrl.value.toString(),
                            totalFiles = tempTrackList.size,
                            downloaded = File(finalOutputDir(type = folderType,subFolder = subFolder)).listFiles()?.size == tempTrackList.size,
                            directory = finalOutputDir(type = folderType,subFolder = subFolder)
                        ))
                    }
                }
            }
            "episode" -> {//TODO
            }
            "show" -> {//TODO
            }
        }
    }

    private fun List<Track>.toTrackDetailsList() = this.map {
        val artistsList = mutableListOf<String>()
        it.artists?.forEach { artist -> artistsList.add(artist!!.name!!) }
        TrackDetails(
            title = it.name.toString(),
            artists = artistsList,
            durationSec = (it.duration_ms/1000).toInt(),
            albumArt = File(
                Environment.getExternalStorageDirectory(),
                Provider.defaultDir +".Images/" + (it.album?.images?.get(0)?.url.toString()).substringAfterLast('/') + ".jpeg"),
            albumName = it.album?.name,
            year = it.album?.release_date,
            comment = "Genres:${it.album?.genres?.joinToString()}",
            trackUrl = it.href,
            downloaded = it.downloaded,
            source = Source.Spotify,
            albumArtURL = it.album?.images?.get(0)?.url.toString()
        )
    }.toMutableList()

    private suspend fun getTrackDetails(trackLink:String): Track?{
        Log.i("Requesting","https://api.spotify.com/v1/tracks/$trackLink")
        return spotifyService?.getTrack(trackLink)?.value
    }
    private suspend fun getAlbumDetails(albumLink:String): Album?{
        Log.i("Requesting","https://api.spotify.com/v1/albums/$albumLink")
        return spotifyService?.getAlbum(albumLink)?.value
    }
    private suspend fun getPlaylistDetails(link:String): Playlist?{
        Log.i("Requesting","https://api.spotify.com/v1/playlists/$link")
        return spotifyService?.getPlaylist(link)?.value
    }
    private suspend fun getPlaylistTrackDetails(link:String,offset:Int = 0,limit:Int = 100): PagingObjectPlaylistTrack?{
        Log.i("Requesting","https://api.spotify.com/v1/playlists/$link/tracks?offset=$offset&limit=$limit")
        return spotifyService?.getPlaylistTracks(link, offset, limit)?.value
    }
}