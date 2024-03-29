package org.librarysimplified.viewer.audiobook

import com.google.common.util.concurrent.ListeningExecutorService
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.http.api.LSHTTPAuthorizationType

/**
 * The interface that must be implemented by activities hosting a {@link AudioBookLoadingFragment}.
 */

interface AudioBookLoadingFragmentListenerType {

  /**
   * @return A listening executor service for running background I/O operations
   */

  fun onLoadingFragmentWantsIOExecutor(): ListeningExecutorService

  /**
   * @return `true` if network connectivity is currently available
   */

  fun onLoadingFragmentIsNetworkConnectivityAvailable(): Boolean

  /**
   * @return The parameters that were used to instantiate the audio book player
   */

  fun onLoadingFragmentWantsAudioBookParameters(): AudioBookPlayerParameters

  /**
   * Called when the loading and parsing of the manifest has finished.
   */

  fun onLoadingFragmentLoadingFinished(manifest: PlayerManifest, authorization: LSHTTPAuthorizationType?)

  /**
   * Called when the loading and parsing of the manifest has failed.
   */

  fun onLoadingFragmentLoadingFailed(exception: Exception)
}
