package com.alinawaz.pixeldl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedDownload(
    val title: String,
    val uri: String,
    val fileName: String,
    val sizeBytes: Long,
    val dateMs: Long,
    val sourceUrl: String,
    val isAudio: Boolean
)

/** Keeps the list of finished downloads in a small JSON file on the phone. */
object Store {

    private fun storeFile(context: Context) = File(context.filesDir, "downloads.json")

    fun load(context: Context): MutableList<SavedDownload> {
        val f = storeFile(context)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            val list = mutableListOf<SavedDownload>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    SavedDownload(
                        title = o.optString("title"),
                        uri = o.optString("uri"),
                        fileName = o.optString("fileName"),
                        sizeBytes = o.optLong("sizeBytes"),
                        dateMs = o.optLong("dateMs"),
                        sourceUrl = o.optString("sourceUrl"),
                        isAudio = o.optBoolean("isAudio")
                    )
                )
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun add(context: Context, item: SavedDownload) {
        val list = load(context)
        list.add(0, item)
        save(context, list)
    }

    fun remove(context: Context, item: SavedDownload) {
        val list = load(context)
        list.removeAll { it.uri == item.uri && it.dateMs == item.dateMs }
        save(context, list)
    }

    private fun save(context: Context, list: List<SavedDownload>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("title", item.title)
                    put("uri", item.uri)
                    put("fileName", item.fileName)
                    put("sizeBytes", item.sizeBytes)
                    put("dateMs", item.dateMs)
                    put("sourceUrl", item.sourceUrl)
                    put("isAudio", item.isAudio)
                }
            )
        }
        storeFile(context).writeText(arr.toString())
    }
}
