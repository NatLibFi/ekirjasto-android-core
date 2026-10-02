package org.librarysimplified.main

import android.content.Context
import android.content.pm.PackageManager
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPNetworkAccess
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.librarysimplified.http.vanilla.LSHTTPProblemReportParsers
import org.librarysimplified.http.vanilla.extensions.LSHTTPInterceptorFactoryType
import java.util.ServiceLoader
import java.util.concurrent.TimeUnit

object MainHTTP {

  fun create(
    context: Context
  ): LSHTTPClientType {
    val (name, version) =
      try {
        val pkgManager = context.packageManager
        val pkgInfo = pkgManager.getPackageInfo(context.packageName, 0)
        Pair(pkgInfo.packageName, BuildConfig.SIMPLIFIED_VERSION)
      } catch (e: PackageManager.NameNotFoundException) {
        Pair("Unavailable", "0.0.0")
      }

    val configuration =
      LSHTTPClientConfiguration(
        applicationName = name,
        applicationVersion = version,
        // The global timeout accommodates long-running LCP audiobook downloads. Per-request
        // timeout support should be used when palace.http provides it.
        timeout = Pair(15L, TimeUnit.MINUTES),
        // palace.http 2.x requires an explicit network-access policy; the default permits all
        // access, preserving the previous (ungated) behaviour.
        networkAccess = LSHTTPNetworkAccess
      )

    // Add Accept-Language interceptor to the list of auto-discovered interceptors
    val interceptors = ServiceLoader.load(LSHTTPInterceptorFactoryType::class.java).toList()
      .plus(CustomHTTPInterceptors.AcceptLanguageFactory())

    return LSHTTPClients(LSHTTPProblemReportParsers(), interceptors).create(context, configuration)
  }
}
