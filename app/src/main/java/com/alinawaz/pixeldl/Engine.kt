package com.alinawaz.pixeldl

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class QualityOption(
    val label: String,
    val note: String,
    val format: String,
    val audioOnly: Boolean
)

val QUALITY_OPTIONS = listOf(
    QualityOption("BEST", "up to 1080p", "bv*[height<=1080]+ba/b[height<=1080]/b", false),
    QualityOption("720P", "HD", "bv*[height<=720]+ba/b[height<=720]/b", false),
    QualityOption("480P", "SD", "bv*[height<=480]+ba/b[height<=480]/b", false),
    QualityOption("360P", "small file", "bv*[height<=360]+ba/b[height<=360]/b", false),
    QualityOption("AUDIO", "MP3 only", "ba/b", true)
)

data class VideoInfo(
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val durationSec: Int,
    val site: String?
)

object Engine {

    @Volatile
    var ready: Boolean = false
        private set

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        YoutubeDL.getInstance().init(context)
        ready = true
    }

    suspend fun update(context: Context): String = withContext(Dispatchers.IO) {
        val status = YoutubeDL.getInstance().updateYoutubeDL(context)
        status.toString()
    }

    suspend fun fetchInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        val req = YoutubeDLRequest(url)
        req.addOption("--dump-single-json")
        req.addOption("--no-playlist")
        req.addOption("--no-warnings")
        val resp = YoutubeDL.getInstance().execute(req)
        val json = JSONObject(resp.out)
        VideoInfo(
            title = json.optString("title", "Unknown title"),
            thumbnail = json.optString("thumbnail").ifBlank { null },
            uploader = json.optString("uploader").ifBlank { null },
            durationSec = json.optInt("duration", 0),
            site = json.optString("extractor_key").ifBlank { null }
        )
    }

    suspend fun download(
        context: Context,
        url: String,
        option: QualityOption,
        onProgress: (Float, Long, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val outDir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
        val before = outDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val req = YoutubeDLRequest(url)
        req.addOption("-f", option.format)
        req.addOption("-o", File(outDir, "%(title)s [%(id)s].%(ext)s").absolutePath)
        req.addOption("--no-playlist")
        req.addOption("--no-mtime")
        if (option.audioOnly) {
            req.addOption("-x")
            req.addOption("--audio-format", "mp3")
        } else {
            req.addOption("--merge-output-format", "mp4")
        }
        YoutubeDL.getInstance().execute(req, "pixeldl") { progress, eta, line ->
            onProgress(progress, eta, line)
        }
        val newFiles = (outDir.listFiles()?.toList() ?: emptyList())
            .filter { it.name !in before && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
        newFiles.maxByOrNull { it.length() }
            ?: throw IllegalStateException("Download finished but no file was found")
    }

    /** Copies the finished file into the phone's Movies/PixelDL or Music/PixelDL so any player can see it. */
    fun saveToGallery(context: Context, src: File, isAudio: Boolean): Uri {
        val mime = when {
            isAudio -> "audio/mpeg"
            src.extension.lowercase() == "webm" -> "video/webm"
            src.extension.lowercase() == "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val collection = if (isAudio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/PixelDL"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Could not save to the gallery")
            val stream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not write the file")
            stream.use { out -> src.inputStream().use { input -> input.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            src.delete()
            return uri
        } else {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(
                    if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
                ),
                "PixelDL"
            ).apply { mkdirs() }
            val dest = File(publicDir, src.name)
            src.copyTo(dest, overwrite = true)
            src.delete()
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
            return FileProvider.getUriForFile(context, "com.alinawaz.pixeldl.fileprovider", dest)
        }
    }
}
