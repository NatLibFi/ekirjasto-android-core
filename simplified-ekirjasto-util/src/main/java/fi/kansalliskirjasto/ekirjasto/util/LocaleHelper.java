package fi.kansalliskirjasto.ekirjasto.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

import java.util.Locale;

public class LocaleHelper {

  private static final String SELECTED_LANGUAGE = "Locale.Helper.Selected.Language";

  // the method is used to set the language at runtime, set the language currently
  // in Locale
  public static Context onAttach(Context context) {
    String lang = getPersistedData(context, Locale.getDefault().getLanguage());
    return setLocale(context, lang);
  }

  // the method is used to set the language at runtime, set the language given as an argument
  public static Context onAttach(Context context, String defaultLanguage) {
    String lang = getPersistedData(context, defaultLanguage);
    return setLocale(context, lang);
  }

  // return the current set language
  public static String getLanguage(Context context) {
    return getPersistedData(context, Locale.getDefault().getLanguage());
  }

  // place the chosen language as the locale, method is based on version
  public static Context setLocale(Context context, String language) {
    persist(context, language);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return updateResources(context, language);
    }

    return updateResourcesLegacy(context, language);
  }

  // Returns the data currently set as the language
  private static String getPersistedData(Context context, String defaultLanguage) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    return preferences.getString(SELECTED_LANGUAGE, defaultLanguage);
  }

  // add the given language to persistent memory, where it remains until set again or the
  // app cache is cleared
  private static void persist(Context context, String language) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = preferences.edit();

    editor.putString(SELECTED_LANGUAGE, language);
    editor.apply();
  }

  // the method is used update the language of application by creating a
  // Locale object setting it as the default, and setting it into resources
  @TargetApi(Build.VERSION_CODES.N)
  private static Context updateResources(Context context, String language) {
    Locale locale = new Locale(language);
    Locale.setDefault(locale);

    Configuration configuration = context.getResources().getConfiguration();
    configuration.setLocale(locale);
    configuration.setLayoutDirection(locale);

    return context.createConfigurationContext(configuration);
  }

  // update the language on older devices by creating
  // Locale object setting it as the default, and setting it into resources
  @SuppressWarnings("deprecation")
  private static Context updateResourcesLegacy(Context context, String language) {
    Locale locale = new Locale(language);
    Locale.setDefault(locale);

    Resources resources = context.getResources();

    Configuration configuration = resources.getConfiguration();
    configuration.locale = locale;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      configuration.setLayoutDirection(locale);
    }

    resources.updateConfiguration(configuration, resources.getDisplayMetrics());

    return context;
  }
}
