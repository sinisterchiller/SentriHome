package com.example.esp32pairingapp.clips

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.esp32pairingapp.network.ApiConfig
import com.example.esp32pairingapp.network.CloudBackendPrefs
import com.example.esp32pairingapp.network.EspHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Model for events/clips from cloud backend (e.g. Google Drive).
 */
data class VideoClip(
    val id: String,
    val filename: String,
    val timestamp: String,
    val deviceId: String,
    val thumbnailUrl: String?,
    val videoUrl: String?
)

/**
 * Load clips from cloud backend GET /api/events. Requires auth token (user must be logged in).
 * Returns list of VideoClip; URLs include ?token= so thumbnails and Play work.
 */
suspend fun loadClipsFromCloud(httpClient: EspHttpClient, authToken: String?): List<VideoClip> {
    if (authToken.isNullOrBlank()) return emptyList()
    return withContext(Dispatchers.IO) {
        val response = httpClient.get(ApiConfig.getEventsUrl(), null, authToken)
        Log.d("SavedClips", "Events response: ${response.take(500)}")
        val eventsArray: JSONArray = runCatching {
            if (response.trimStart().startsWith("[")) {
                JSONArray(response)
            } else {
                val json = JSONObject(response)
                when {
                    json.has("events") -> json.optJSONArray("events") ?: JSONArray()
                    json.has("clips") -> json.optJSONArray("clips") ?: JSONArray()
                    else -> JSONArray()
                }
            }
        }.getOrElse {
            Log.e("SavedClips", "Failed to parse events", it)
            JSONArray()
        }
        val tokenSuffix = "?token=${java.net.URLEncoder.encode(authToken, "UTF-8")}"
        (0 until eventsArray.length()).map { i ->
            val eventObj = eventsArray.getJSONObject(i)
            val id = eventObj.optString("_id", eventObj.optString("id", ""))
            val filename = eventObj.optString("filename", "event_$i.mp4")
            val timestamp = eventObj.optString("createdAt", eventObj.optString("timestamp", ""))
            val deviceId = eventObj.optString("deviceId", "unknown")
            VideoClip(
                id = id,
                filename = filename,
                timestamp = timestamp,
                deviceId = deviceId,
                thumbnailUrl = if (id.isNotBlank()) ApiConfig.getClipThumbnailUrl(id) + tokenSuffix else null,
                videoUrl = if (id.isNotBlank()) ApiConfig.getClipStreamUrl(id) + tokenSuffix else null
            )
        }
    }
}

private fun fetchBitmap(url: String): Bitmap? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        instanceFollowRedirects = true
    }
    return try {
        if (connection.responseCode in 200..299) {
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } else null
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun RemoteThumbnail(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        failed = false
        bitmap = null
        bitmap = try {
            withContext(Dispatchers.IO) { fetchBitmap(url) }
        } catch (_: Exception) {
            failed = true
            null
        }
    }

    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier
            )
        }
        failed -> {
            Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No thumbnail", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        else -> {
            Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * Shared content: "Load Clips from Drive" button and list of clips with thumbnails and Play.
 * Only loads data when user is logged in (has cloud auth token).
 */
@Composable
fun SavedClipsContent(
    httpClient: EspHttpClient,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
    showTitle: Boolean = true
) {
    var clips by remember { mutableStateOf<List<VideoClip>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val authToken = CloudBackendPrefs.getAuthToken(context)
    val isLoggedIn = !authToken.isNullOrBlank()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (showTitle) {
                Text(
                    "Recorded Clips (Google Drive)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
            }

            if (!isLoggedIn) {
                Text(
                    "Sign in with Google Drive to view your clips.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        onError("")
                        try {
                            clips = loadClipsFromCloud(httpClient, CloudBackendPrefs.getAuthToken(context))
                            onError("✅ Loaded ${clips.size} events")
                        } catch (e: Exception) {
                            onError("❌ Failed to load clips: ${e.message}")
                            Log.e("SavedClips", "Load error", e)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading && isLoggedIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Loading..." else "🔄 Load Clips from Drive")
            }

            if (clips.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                clips.take(20).forEach { clip ->
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val thumb = clip.thumbnailUrl
                        if (thumb != null) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 1.dp,
                                modifier = Modifier.size(width = 120.dp, height = 80.dp)
                            ) {
                                RemoteThumbnail(
                                    url = thumb,
                                    modifier = Modifier.fillMaxSize(),
                                    contentDescription = "Thumbnail for ${clip.filename}"
                                )
                            }
                        } else {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(width = 120.dp, height = 80.dp)
                            ) {}
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(clip.filename, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Device: ${clip.deviceId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            if (clip.timestamp.isNotBlank()) {
                                Text(
                                    clip.timestamp.take(19),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    val url = clip.videoUrl
                                    if (url.isNullOrBlank()) {
                                        onError("❌ No video URL for this event")
                                        return@Button
                                    }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                                enabled = !clip.videoUrl.isNullOrBlank()
                            ) {
                                Text("▶️ Play")
                            }
                        }
                    }
                }
                if (clips.size > 20) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Showing 20 of ${clips.size}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Full-screen "Watch saved clips" screen with back button.
 */
@Composable
fun SavedClipsScreen(
    httpClient: EspHttpClient,
    onBack: () -> Unit
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) { Text("← Back") }
            Text("Watch saved clips", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(80.dp))
        }
        Spacer(Modifier.height(16.dp))
        SavedClipsContent(
            httpClient = httpClient,
            onError = { errorMessage = it.ifBlank { null } },
            showTitle = false
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        errorMessage!!.startsWith("✅") -> MaterialTheme.colorScheme.primaryContainer
                        errorMessage!!.startsWith("❌") -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
