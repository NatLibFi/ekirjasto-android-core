package fi.kansalliskirjasto.ekirjasto.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import java.util.Locale;

/**
 * Util class for handling in-app language changes
 */
public class LocaleHelper {

  private static final String SELECTED_LANGUAGE = "selected_language";

  /**
   * Function is used to set the language at runtime, sets the language currently
   * in cache
   * @param context The base context
   * @return Context
   */
  public static Context onAttach(Context context) {
    String lang = getPersistedData(context, Locale.getDefault().getLanguage());
    return setLocale(context, lang);
  }

  /**
   * Function used to set given language at runtime, set the language given as an argument
   * if no other value has been given before, otherwise return the currently set language
   * @param context The base context
   * @param defaultLanguage Language to use if no language in cache
   * @return Context
   */
  public static Context onAttach(Context context, String defaultLanguage) {
    String lang = getPersistedData(context, defaultLanguage);
    return setLocale(context, lang);
  }

  /**
   * Place chosen language as locale
   * @param context The base context
   * @param language Language as string
   * @return Context
   */
  //
  public static Context setLocale(Context context, String language) {
    persist(context, language);
    return updateResources(context, language);
  }

  //

  /**
   * Return language in cache, if not set return default language
   * @param context The base context
   * @param defaultLanguage Language tag
   * @return String
   */
  private static String getPersistedData(Context context, String defaultLanguage) {
    SharedPreferences preferences = context.getSharedPreferences("APP_PREFERENCES", Context.MODE_PRIVATE);
    return preferences.getString(SELECTED_LANGUAGE, defaultLanguage);
  }



  /**
   * Add given language to cache, where it remains until set again or the
   * app cache is cleared
   * @param context The base context
   * @param language Language tag
   */
  private static void persist(Context context, String language) {
    SharedPreferences preferences = context.getSharedPreferences("APP_PREFERENCES", Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = preferences.edit();

    editor.putString(SELECTED_LANGUAGE, language);
    editor.apply();
  }



  /**
   * Function used to update the language of application by creating a
   * Locale object setting it as the default, and setting it into resources.
   * Returns base context as we restart app to apply changes, and thus
   * the base context is sufficient, using createConfigurationContext breaks dark mode.
   * @param context The base context
   * @param language Language tag
   * @return Context
   */
  private static Context updateResources(Context context, String language) {
    Locale locale = new Locale(language);
    Locale.setDefault(locale);

    Configuration configuration = context.getResources().getConfiguration();
    configuration.setLocale(locale);
    configuration.setLayoutDirection(locale);

    return context;
  }

  /**
   * Return current language, returns the language in locale if not set
   * @param context The base context
   * @return String language tag
   */
  public static String getLanguage(Context context) {
    return getPersistedData(context, Locale.getDefault().getLanguage());
  }
}
