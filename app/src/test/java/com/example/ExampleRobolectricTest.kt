package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FakeStandby", appName)
  }

  @Test
  fun `verify preferences default values`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.PreferencesManager.getInstance(context)
    val config = prefs.config.value
    assertEquals(true, config.fourFingerWake)
    assertEquals(true, config.powerKeyWake)
    assertEquals(true, config.oledPureBlack)
    assertEquals(false, config.timerEnabled)
    assertEquals(120, config.timerMinutes)
  }
}
