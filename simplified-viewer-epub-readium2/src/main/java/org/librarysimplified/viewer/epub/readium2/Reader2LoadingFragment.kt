package org.librarysimplified.viewer.epub.readium2

import androidx.fragment.app.Fragment

/**
 * A trivial fragment displayed while the SR2 controller is being created. In SR2 6.x the
 * controller is constructed asynchronously, so a placeholder is shown until the
 * [org.librarysimplified.r2.views.SR2ReaderModel] publishes that the controller has become
 * available.
 */

class Reader2LoadingFragment : Fragment(R.layout.reader2_loading)
