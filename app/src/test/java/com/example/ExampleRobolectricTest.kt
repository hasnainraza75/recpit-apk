package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.ImageDownloadManager
import com.example.util.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context matches Receipt Maker Pro`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("RECEIPT MAKER PRO", appName)
  }

  @Test
  fun `website url string is configured properly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val url = context.getString(R.string.website_url)
    assertEquals("https://receipt-maker-pro.cu.ma", url)
    assertEquals("https://receipt-maker-pro.cu.ma", MainActivity.BASE_URL)
  }

  @Test
  fun `network utils responds without crashing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val available = NetworkUtils.isNetworkAvailable(context)
    assertNotNull(available)
  }

  @Test
  fun `base64 download handles data url without crashing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // 1x1 transparent png in base64
    val sampleDataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    ImageDownloadManager.saveBase64Image(context, sampleDataUrl, "test_receipt.png")
  }
}
