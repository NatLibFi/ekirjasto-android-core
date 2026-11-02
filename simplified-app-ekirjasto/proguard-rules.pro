-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# These libraries contain optional JVM-only integrations that are referenced by
# shared artifacts but are not used by the Android application. Keep R8 from
# treating those optional paths as required classes.
-dontwarn bali.**
-dontwarn com.google.android.gms.auth.api.**
-dontwarn com.io7m.jequality.annotations.**
-dontwarn com.io7m.junreachable.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn java.beans.**
-dontwarn javax.lang.model.**
-dontwarn kotlinx.parcelize.**
-dontwarn kotlinx.serialization.**
-dontwarn net.java.truecommons.annotations.**
-dontwarn net.jcip.annotations.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.joda.convert.**
-dontwarn org.librarysimplified.audiobook.persistence.**
-dontwarn org.objectweb.asm.**
-dontwarn org.openjsse.**
