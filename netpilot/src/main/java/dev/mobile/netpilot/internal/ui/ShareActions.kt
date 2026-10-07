package dev.mobile.netpilot.internal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.mobile.netpilot.R
import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.export.HarFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A dedicated subclass so the host app can declare its own FileProvider without a merge conflict. */
internal class NetPilotFileProvider : FileProvider()

/** Clipboard and share-sheet helpers used by the inspector screens. */
internal object ShareActions {
    /** Upper bound for "Export as HAR"; bodies can be up to 500 KB each. */
    const val HAR_EXPORT_LIMIT = 100

    private const val TAG = "NetPilot"

    /** Must match the path in res/xml/netpilot_file_paths.xml. */
    private const val EXPORT_DIRECTORY = "netpilot"
    private const val AUTHORITY_SUFFIX = ".netpilot.fileprovider"
    private const val PLAIN_TEXT = "text/plain"
    private const val HAR_MIME_TYPE = "application/json"

    /** Share intents travel through Binder (~1 MB limit), so longer text goes out as a file. */
    private const val MAX_INLINE_SHARE_CHARS = 100_000

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.netpilot_copied, Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun shareText(context: Context, subject: String, text: String, fileName: String) {
        if (text.length > MAX_INLINE_SHARE_CHARS) {
            shareFile(context, fileName, PLAIN_TEXT) { text }
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(PLAIN_TEXT)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null))
    }

    suspend fun shareHar(context: Context, transactions: List<HttpTransaction>) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        shareFile(context, "netpilot-$timestamp.har", HAR_MIME_TYPE) { HarFormatter.format(transactions) }
    }

    private suspend fun shareFile(context: Context, fileName: String, mimeType: String, content: () -> String) {
        val file = try {
            withContext(Dispatchers.IO) { writeExport(context, fileName, content()) }
        } catch (e: IOException) {
            Log.w(TAG, "Could not write export $fileName", e)
            Toast.makeText(context, R.string.netpilot_export_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(fileName, uri) }
        context.startActivity(Intent.createChooser(send, null))
    }

    /** Keeps only the latest export so the cache does not grow. */
    private fun writeExport(context: Context, fileName: String, content: String): File {
        val directory = File(context.cacheDir, EXPORT_DIRECTORY)
        directory.deleteRecursively()
        if (!directory.mkdirs()) throw IOException("Cannot create ${directory.path}")
        return File(directory, fileName).apply { writeText(content) }
    }
}
