package com.alinawaz.pixeldl

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val sharedUrlState = mutableStateOf<String?>(null)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUrlState.value = extractUrl(intent)
        setContent { PixelDLApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractUrl(intent)?.let { sharedUrlState.value = it }
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            return Regex("https?://\\S+").find(text)?.value
        }
        return null
    }
}

@Composable
fun PixelDLApp() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var engineReady by remember { mutableStateOf(Engine.ready) }
    var engineMsg by remember { mutableStateOf("STARTING ENGINE...") }

    LaunchedEffect(Unit) {
        try {
            Engine.init(context.applicationContext)
            engineReady = true
            engineMsg = "ENGINE READY"
        } catch (e: Exception) {
            engineMsg = "ENGINE ERROR"
        }
        launch {
            try {
                Engine.update(context.applicationContext)
            } catch (e: Exception) {
                // An update failing (e.g. no internet yet) is not fatal.
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Px.Bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Px.PanelDark)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(22.dp).background(Px.Green).border(3.dp, Px.Ink),
                contentAlignment = Alignment.Center
            ) {
                Text("▼", fontSize = 9.sp, color = Px.Ink)
            }
            Spacer(Modifier.width(10.dp))
            Text("PIXELDL", fontFamily = PixelHead, fontSize = 15.sp, color = Px.Yellow)
            Spacer(Modifier.weight(1f))
            Text(
                engineMsg,
                fontFamily = PixelBody,
                fontSize = 14.sp,
                color = if (engineReady) Px.Green else Px.Yellow
            )
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Px.Ink))

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> GetScreen(engineReady)
                1 -> FilesScreen()
                else -> SetupScreen()
            }
        }

        Box(Modifier.fillMaxWidth().height(3.dp).background(Px.Ink))
        Row(
            Modifier.fillMaxWidth().background(Px.PanelDark).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PixelButton(
                "GET", Modifier.weight(1f),
                color = if (tab == 0) Px.Green else Px.Panel,
                textColor = if (tab == 0) Px.Ink else Px.Paper
            ) { tab = 0 }
            PixelButton(
                "FILES", Modifier.weight(1f),
                color = if (tab == 1) Px.Green else Px.Panel,
                textColor = if (tab == 1) Px.Ink else Px.Paper
            ) { tab = 1 }
            PixelButton(
                "SETUP", Modifier.weight(1f),
                color = if (tab == 2) Px.Green else Px.Panel,
                textColor = if (tab == 2) Px.Ink else Px.Paper
            ) { tab = 2 }
        }
    }
}

@Composable
fun GetScreen(engineReady: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var url by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var selected by remember { mutableIntStateOf(loadDefaultQuality(context)) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    val shared = sharedUrlState.value
    LaunchedEffect(shared) {
        if (!shared.isNullOrBlank()) {
            url = shared
            sharedUrlState.value = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("PASTE A VIDEO LINK", fontFamily = PixelHead, fontSize = 10.sp, color = Px.Paper)

        PixelPanel(color = Px.PanelDark, modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = url,
                onValueChange = { url = it },
                textStyle = TextStyle(fontFamily = PixelBody, fontSize = 20.sp, color = Px.Paper),
                cursorBrush = SolidColor(Px.Yellow),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (url.isBlank()) {
                        Text("https://...", fontFamily = PixelBody, fontSize = 20.sp, color = Px.Grey)
                    }
                    inner()
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton("PASTE", Modifier.weight(1f), color = Px.Blue) {
                clipboard.getText()?.text?.let { text ->
                    url = Regex("https?://\\S+").find(text)?.value ?: text
                }
            }
            PixelButton(
                "FETCH", Modifier.weight(1f),
                enabled = engineReady && url.isNotBlank() && !busy
            ) {
                busy = true
                error = null
                savedMsg = null
                info = null
                progress = 0f
                status = "READING LINK..."
                scope.launch {
                    try {
                        info = Engine.fetchInfo(url.trim())
                        status = "PICK A QUALITY, THEN PRESS DOWNLOAD"
                    } catch (e: Exception) {
                        error = cleanError(e)
                        status = ""
                    }
                    busy = false
                }
            }
        }

        info?.let { v ->
            PixelPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Thumb(v.thumbnail, Modifier.fillMaxWidth().height(160.dp))
                    Text(v.title, fontFamily = PixelBody, fontSize = 21.sp, color = Px.Paper)
                    val meta = listOfNotNull(
                        v.uploader,
                        v.site,
                        if (v.durationSec > 0) formatDuration(v.durationSec) else null
                    ).joinToString("  -  ")
                    if (meta.isNotBlank()) {
                        Text(meta, fontFamily = PixelBody, fontSize = 16.sp, color = Px.Grey)
                    }
                }
            }

            Text("QUALITY", fontFamily = PixelHead, fontSize = 10.sp, color = Px.Paper)
            QUALITY_OPTIONS.forEachIndexed { i, opt ->
                QualityRow(opt, selected == i) { selected = i }
            }

            PixelButton(
                "▼ DOWNLOAD", Modifier.fillMaxWidth(),
                enabled = engineReady && !busy
            ) {
                val opt = QUALITY_OPTIONS[selected]
                busy = true
                error = null
                savedMsg = null
                progress = 0f
                status = "STARTING..."
                scope.launch {
                    try {
                        val file = Engine.download(context.applicationContext, url.trim(), opt) { p, _, line ->
                            if (p >= 0) progress = p / 100f
                            if (line.isNotBlank()) status = line.take(90)
                        }
                        val name = file.name
                        val size = file.length()
                        val uri = Engine.saveToGallery(context.applicationContext, file, opt.audioOnly)
                        Store.add(
                            context.applicationContext,
                            SavedDownload(
                                title = v.title,
                                uri = uri.toString(),
                                fileName = name,
                                sizeBytes = size,
                                dateMs = System.currentTimeMillis(),
                                sourceUrl = url.trim(),
                                isAudio = opt.audioOnly
                            )
                        )
                        progress = 1f
                        status = ""
                        savedMsg = "SAVED! Find it in " +
                            (if (opt.audioOnly) "Music > PixelDL" else "Movies > PixelDL") +
                            ", or open it from the FILES tab."
                    } catch (e: Exception) {
                        error = cleanError(e)
                        status = ""
                    }
                    busy = false
                }
            }
        }

        if (busy) {
            PixelProgress(progress, Modifier.fillMaxWidth())
        }
        if (status.isNotBlank()) {
            Text(status, fontFamily = PixelBody, fontSize = 16.sp, color = Px.Yellow)
        }
        error?.let {
            PixelPanel(color = Px.DarkRed, modifier = Modifier.fillMaxWidth()) {
                Text("ERROR: $it", fontFamily = PixelBody, fontSize = 17.sp, color = Px.Paper)
            }
        }
        savedMsg?.let {
            PixelPanel(color = Px.DarkGreen, modifier = Modifier.fillMaxWidth()) {
                Text(it, fontFamily = PixelBody, fontSize = 17.sp, color = Px.Paper)
            }
        }

        Spacer(Modifier.height(2.dp))
        Text(
            "YouTube - Instagram - Facebook - TikTok - X - 1000+ sites\nNo ads. No tracking. Files stay on your phone.",
            fontFamily = PixelBody, fontSize = 16.sp, color = Px.Grey
        )
    }
}

@Composable
fun QualityRow(opt: QualityOption, isSelected: Boolean, onClick: () -> Unit) {
    PixelPanel(
        color = if (isSelected) Px.DarkGreen else Px.Panel,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(if (isSelected) Px.Green else Px.PanelDark)
                    .border(2.dp, Px.Ink)
            )
            Spacer(Modifier.width(10.dp))
            Text(opt.label, fontFamily = PixelHead, fontSize = 10.sp, color = Px.Paper)
            Spacer(Modifier.weight(1f))
            Text(opt.note, fontFamily = PixelBody, fontSize = 16.sp, color = Px.Grey)
        }
    }
}

@Composable
fun FilesScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(Store.load(context)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("YOUR DOWNLOADS", fontFamily = PixelHead, fontSize = 10.sp, color = Px.Paper)
        if (items.isEmpty()) {
            PixelPanel(Modifier.fillMaxWidth()) {
                Text(
                    "Nothing here yet. Download a video and it will show up here.",
                    fontFamily = PixelBody, fontSize = 18.sp, color = Px.Grey
                )
            }
        }
        items.forEach { item ->
            PixelPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, fontFamily = PixelBody, fontSize = 20.sp, color = Px.Paper)
                    Text(
                        "${formatDate(item.dateMs)}  -  ${formatSize(item.sizeBytes)}  -  ${if (item.isAudio) "AUDIO" else "VIDEO"}",
                        fontFamily = PixelBody, fontSize = 15.sp, color = Px.Grey
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelButton("OPEN", Modifier.weight(1f), color = Px.Blue) {
                            openFile(context, item)
                        }
                        PixelButton("DEL", Modifier.weight(1f), color = Px.Red) {
                            deleteFile(context, item)
                            Store.remove(context, item)
                            items = Store.load(context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableIntStateOf(loadDefaultQuality(context)) }
    var updateMsg by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("DEFAULT QUALITY", fontFamily = PixelHead, fontSize = 10.sp, color = Px.Paper)
        QUALITY_OPTIONS.forEachIndexed { i, opt ->
            QualityRow(opt, selected == i) {
                selected = i
                saveDefaultQuality(context, i)
            }
        }

        Text("DOWNLOAD ENGINE", fontFamily = PixelHead, fontSize = 10.sp, color = Px.Paper)
        PixelButton(
            "UPDATE ENGINE", Modifier.fillMaxWidth(),
            enabled = !updating && Engine.ready
        ) {
            updating = true
            updateMsg = "CHECKING FOR UPDATES..."
            scope.launch {
                updateMsg = try {
                    "ENGINE: " + Engine.update(context.applicationContext)
                } catch (e: Exception) {
                    "UPDATE FAILED: " + (e.message ?: "error").take(80)
                }
                updating = false
            }
        }
        if (updateMsg.isNotBlank()) {
            Text(updateMsg, fontFamily = PixelBody, fontSize = 16.sp, color = Px.Yellow)
        }

        PixelPanel(Modifier.fillMaxWidth()) {
            Text(
                "PixelDL v1.0\nEngine: yt-dlp (open source)\nVideos save to Movies > PixelDL\nAudio saves to Music > PixelDL\nNo ads - no tracking - no account",
                fontFamily = PixelBody, fontSize = 17.sp, color = Px.Paper
            )
        }
    }
}

@Composable
fun Thumb(url: String?, modifier: Modifier = Modifier) {
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url != null) {
            bmp = withContext(Dispatchers.IO) {
                try {
                    URL(url).openStream().use { input ->
                        BitmapFactory.decodeStream(input)
                    }?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    Box(
        modifier.background(Px.PanelDark).border(3.dp, Px.Ink),
        contentAlignment = Alignment.Center
    ) {
        val image = bmp
        if (image != null) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("NO IMAGE", fontFamily = PixelHead, fontSize = 8.sp, color = Px.Grey)
        }
    }
}

fun openFile(context: Context, item: SavedDownload) {
    try {
        val uri = Uri.parse(item.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (item.isAudio) "audio/*" else "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // The file may have been deleted from the gallery already.
    }
}

fun deleteFile(context: Context, item: SavedDownload) {
    try {
        val uri = Uri.parse(item.uri)
        if (uri.scheme == "content") {
            context.contentResolver.delete(uri, null, null)
        } else {
            uri.path?.let { File(it).delete() }
        }
    } catch (e: Exception) {
        // Already gone - the list entry is removed anyway.
    }
}

fun loadDefaultQuality(context: Context): Int =
    context.getSharedPreferences("pixeldl", Context.MODE_PRIVATE).getInt("default_quality", 0)

fun saveDefaultQuality(context: Context, value: Int) {
    context.getSharedPreferences("pixeldl", Context.MODE_PRIVATE)
        .edit().putInt("default_quality", value).apply()
}

fun formatDuration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

fun formatSize(bytes: Long): String =
    if (bytes <= 0) "?" else "%.1f MB".format(bytes / 1024.0 / 1024.0)

fun formatDate(ms: Long): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(ms))

fun cleanError(e: Exception): String {
    val msg = e.message ?: "Something went wrong"
    val line = msg.lines().firstOrNull { it.contains("ERROR", ignoreCase = true) }
    return (line ?: msg).take(160)
}
