/*
 * Ven's Aliucord Plugins
 * Copyright (C) 2021 Vendicated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0
*/

package dev.vendicated.aliucordplugins.spotifylistenalong

import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.utils.ReflectUtils
import com.aliucord.utils.RxUtils.getResultBlocking
import com.discord.stores.StoreStream
import com.discord.utilities.platform.Platform
import com.discord.utilities.rest.RestAPI
import com.discord.utilities.spotify.SpotifyApiClient

const val baseUrl = "https://api.spotify.com/v1/me/player"

// The spotify api gives me fucking brain damage i swear to god
// You can either specify album or playlist uris as "context_uri" String or track uris as "uris" array
class SongBody(val uris: List<String>, val position_ms: Int = 0)

object SpotifyApi {
    private val client: SpotifyApiClient by lazy {
        ReflectUtils.getField(StoreStream.getSpotify(), "spotifyApiClient") as SpotifyApiClient
    }

    private var token: String? = null
    private fun getToken(): String? {
        if (token == null) {
            token = RestAPI.AppHeadersProvider.INSTANCE.spotifyToken
                ?: try {
                    val accountId = ReflectUtils.getField(client, "spotifyAccountId")
                    val res = RestAPI.api
                        .getConnectionAccessToken(Platform.SPOTIFY.name.lowercase(), accountId as String)
                        .getResultBlocking()
                    res.second?.let { throw it }
                    res.first!!.accessToken
                } catch (th: Throwable) {
                    null
                }
        }
        return token
    }

    private fun request(endpoint: String, method: String = "PUT", data: Any? = null) {
        Utils.threadPool.execute {
            val token = getToken() ?: run {
                    Utils.showToast(
                        Utils.appContext,
                        "Failed to get Spotify token from Discord. Make sure your spotify is running."
                    )
                    return@execute
                }

            try {
                Http.Request("$baseUrl/$endpoint", method)
                    .setHeader("Authorization", "Bearer $token")
                    .use {
                        val res =
                            if (data != null)
                                it.executeWithJson(data)
                            else
                                it
                                    .setHeader("Content-Type", "application/json")
                                    .execute()

                        res.assertOk()
                    }
            } catch (th: Throwable) {
                if (th is Http.HttpException && th.statusCode == 401) {
                    SpotifyApiClient.`access$refreshSpotifyToken`(client)
                    this.token = null
                } else
                    logger.error(th)
            }
        }
    }

    fun playSong(id: String, position_ms: Int) {
        request("play", "PUT", SongBody(listOf("spotify:track:$id"), position_ms))
    }

    fun pause() {
        request("pause")
    }

    fun resume() {
        request("play")
    }

    fun seek(position_ms: Int) {
        request("seek?position_ms=$position_ms")
    }
}