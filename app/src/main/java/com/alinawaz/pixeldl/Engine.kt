package com.alinawaz.pixeldl

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.farimarwat.commons.UpdateChannel
import com.farimarwat.commons.YoutubeDLRequest
import com.farimarwat.library.YoutubeDL
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    suspend fun init(context: Context) = suspendCancellableCoroutine { cont ->
        YoutubeDL.init(
            appContext = context.applicationContext,
            withFfmpeg = true,
            withAria2c = false,
            onSuccess = {
                ready = true
                if (cont.isActive) cont.resume(Unit)
            },
            onError = { e ->
                if (cont.isActive) {
                    cont.resumeWithException(
                        RuntimeException(e.message ?: "Engine failed to start", e)
                    )
                }
            }
        )
    }

    suspend fun update(context: Context): String {
        var result: String? = null
        var failure: Throwable? = null
        YoutubeDL.updateYoutubeDL(
            appContext = context.applicationContext,
            updateChannel = UpdateChannel.STABLE,
            onSuccess = { status -> result = status.name },
            onError = { e -> failure = e }
        )
        failure?.let {
            throw RuntimeException(it.message ?: "Update failed", it)
        }
        return result ?: "DONE"
    }

    suspend fun fetchInfo(url: String): VideoInfo = suspendCancellableCoroutine { cont ->
        if (!ready) {
            cont.resumeWithException(
                IllegalStateException("Engine is still starting - wait for ENGINE READY at the top")
            )
            return@suspendCancellableCoroutine
        }
        val req = YoutubeDLRequest(url)
        req.addOption("--no-playlist")
        req.addOption("--no-warnings")
        YoutubeDL.getInfo(
            request = req,
            onSuccess = { info ->
                if (cont.isActive) {
                    cont.resume(
                        VideoInfo(
                            title = info.title?.takeIf { it.isNotBlank() }
                                ?: info.fulltitle?.takeIf { it.isNotBlank() }
                                ?: "Unknown title",
                            thumbnail = info.thumbnail?.takeIf { it.isNotBlank() },
                            uploader = info.uploader?.takeIf { it.isNotBlank() },
                            durationSec = info.duration,
                            site = (info.extractorKey ?: info.extractor)
                                ?.takeIf { it.isNotBlank() }
                        )
                    )
                }
            },
            onError = { e ->
                if (cont.isActive) {
                    cont.resumeWithException(
                        RuntimeException(e.message ?: "Could not read this link", e)
                    )
                }
            }
        )
    }

    suspend fun download(
        context: Context,
        url: String,
        option: QualityOption,
        onProgress: (Float, Long, String) -> Unit
    ): File = suspendCancellableCoroutine { cont ->
        if (!ready) {
            cont.resumeWithException(
                IllegalStateException("Engine is still starting - wait for ENGINE READY at the top")
            )
            return@suspendCancellableCoroutine
        }
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
        YoutubeDL.download(
            request = req,
            pId = "pixeldl",
            progressCallBack = { progress, eta, line -> onProgress(progress, eta, line) },
            onStartProcess = { },
            onEndProcess = { resp ->
                if (cont.isActive) {
                    if (resp.exitCode == 0) {
                        val newFiles = (outDir.listFiles()?.toList() ?: emptyList())
                            .filter {
                                it.name !in before &&
                                    !it.name.endsWith(".part") &&
                                    !it.name.endsWith(".ytdl")
                            }
                        val file = newFiles.maxByOrNull { it.length() }
                        if (file != null) {
                            cont.resume(file)
                        } else {
                            cont.resumeWithException(
                                IllegalStateException("Download finished but no file was found")
                            )
                        }
                    } else {
                        cont.resumeWithException(
                            IllegalStateException(
                                resp.err?.takeIf { it.isNotBlank() } ?: "Download failed"
                            )
                        )
                    }
                }
            },
            onError = { e ->
                if (cont.isActive) {
                    cont.resumeWithException(
                        RuntimeException(e.message ?: "Download failed", e)
                    )
                }
            }
        )
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
