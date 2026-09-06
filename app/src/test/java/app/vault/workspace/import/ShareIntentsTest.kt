package app.vault.workspace.import

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShareIntentsTest {
    @Test
    fun extractSendSingle() {
        val uri = Uri.parse("content://com.example/doc.pdf")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/pdf"
        }
        assertEquals(listOf(uri), extractShareUris(intent))
    }

    @Test
    fun extractSendMultiple() {
        val a = Uri.parse("content://com.example/a.jpg")
        val b = Uri.parse("content://com.example/b.jpg")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(a, b))
            type = "image/*"
        }
        assertEquals(listOf(a, b), extractShareUris(intent))
    }

    @Test
    fun emptyForUnrelated() {
        assertTrue(extractShareUris(Intent(Intent.ACTION_VIEW)).isEmpty())
        assertTrue(extractShareUris(null).isEmpty())
    }
}
