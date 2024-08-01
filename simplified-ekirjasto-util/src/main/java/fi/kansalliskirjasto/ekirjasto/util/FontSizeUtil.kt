package fi.kansalliskirjasto.ekirjasto.util

import android.content.Context
import android.content.SharedPreferences

class FontSizeUtil (private val sharedPreferences: SharedPreferences) {

  constructor(context: Context) : this(
    context.getSharedPreferences("APP_PREFERENCES", Context.MODE_PRIVATE)
  )

  private val KEY_TEXT_SIZE = "text_size"

  /**
   * Returns the font size currently set in SharedPreferences.
   * Returns the default if nothing is set.
   */
  fun getFontSize(): Float {
    return sharedPreferences.getFloat(KEY_TEXT_SIZE, 1.0f)
  }

  /**
   * Set the given font size to SharedPreferences
   */
  fun setFontSize(size: Float) {
    sharedPreferences.edit().putFloat(KEY_TEXT_SIZE, size).apply()

  }

}
