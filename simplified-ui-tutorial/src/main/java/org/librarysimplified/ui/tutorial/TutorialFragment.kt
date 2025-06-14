package org.librarysimplified.ui.tutorial

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.librarysimplified.services.api.Services
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.ui.screen.ScreenSizeInformationType

class TutorialFragment : Fragment(R.layout.fragment_tutorial) {

  private val listener: FragmentListenerType<TutorialEvent> by fragmentListeners()

  private val services =
    Services.serviceDirectory()

  private lateinit var tutorialViewPager: ViewPager2
  private lateinit var tutorialTabLayout: TabLayout
  private lateinit var skipButton: ImageView
  private lateinit var screenSizeInformation: ScreenSizeInformationType

  private var isSwipingLastPage = false

  private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
      if (!::tutorialViewPager.isInitialized || tutorialViewPager.adapter == null) {
        return
      }

      // check if the user is in the last page
      if (position == tutorialViewPager.adapter!!.itemCount - 1 && positionOffset == 0f) {
        // if the user is trying to swipe in the last page, we close the tutorial
        if (isSwipingLastPage) {
          closeTutorial()

          // when the user lands in the last page, this flag will be set to true so the tutorial can
          // be closed if the user tries to swipe
        } else {
          isSwipingLastPage = true
        }
      } else {
        isSwipingLastPage = false
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.tutorialViewPager =
      view.findViewById(R.id.tutorial_view_pager)
    this.tutorialTabLayout =
      view.findViewById(R.id.tutorial_tab_layout)
    this.skipButton =
      view.findViewById(R.id.skip_button)
    this.screenSizeInformation =
      services.requireService(ScreenSizeInformationType::class.java)

    ViewCompat.setOnApplyWindowInsetsListener(skipButton) { view, insets ->
      val insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      // Apply the insets as a margin to the view
      view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = insets.top
        leftMargin = insets.left
        rightMargin = insets.right
      }
      // Return CONSUMED as we don't want the window insets to keep passing
      // down to descendant views.
      WindowInsetsCompat.CONSUMED
    }
    setupUI()
  }

  override fun onDestroyView() {
    tutorialViewPager.unregisterOnPageChangeCallback(this.pageChangeCallback)
    super.onDestroyView()
  }

  private fun setupUI() {
    val descriptions = arrayOf(
      getString(R.string.contentDescription1),
      getString(R.string.contentDescription2),
      getString(R.string.contentDescription3),
      getString(R.string.contentDescription4))

    tutorialViewPager.adapter = TutorialPageAdapter(descriptions)

    tutorialViewPager.registerOnPageChangeCallback(pageChangeCallback)

    TabLayoutMediator(tutorialTabLayout, tutorialViewPager) { tab, position ->
      tab.contentDescription = getString(R.string.contentTabDescription)
    }.attach()

    adjustTabLayoutTabMargins()

    this.skipButton.contentDescription = getString(R.string.close_tutorial)
    this.skipButton.setOnClickListener {
      closeTutorial()
    }
  }

  private fun adjustTabLayoutTabMargins() {
    // set the tab margins programmatically, since it's the only way of making them not be right next
    // to each other
    val tabs = tutorialTabLayout.getChildAt(0) as ViewGroup

    for (i in 0 until tabs.childCount) {
      val tab = tabs.getChildAt(i)
      val layoutParams = tab.layoutParams as LinearLayout.LayoutParams

      val margin = this.screenSizeInformation.dpToPixels(4).toInt()

      when {
        i == 0 -> {
          layoutParams.marginEnd = margin
        }
        i + 1 == tabs.childCount -> {
          layoutParams.marginStart = margin
        }
        else -> {
          layoutParams.marginEnd = margin
          layoutParams.marginStart = margin
        }
      }

      tab.layoutParams = layoutParams
      tutorialTabLayout.requestLayout()
    }
  }

  private fun closeTutorial() {
    this.listener.post(TutorialEvent.TutorialCompleted)
  }
}
