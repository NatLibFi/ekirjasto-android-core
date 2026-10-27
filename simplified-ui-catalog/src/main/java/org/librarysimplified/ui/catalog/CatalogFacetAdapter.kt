package org.librarysimplified.ui.catalog

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import org.nypl.simplified.feeds.api.FeedFacet

class CatalogFacetAdapter  (context: Context,
                            private val title: String,
                            private val choices: List<FeedFacet>,
                            private var selectedIndex: Int
) : ArrayAdapter<FeedFacet>(context, 0, choices) {

  fun setSelectedIndex(index: Int) {
    this.selectedIndex = index
    this.notifyDataSetChanged()
  }

  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val view = (convertView as? RadioButton) ?: RadioButton(this.context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    }
    val choice = choices[position]

    view.text = choice.title
    view.isChecked = position == selectedIndex
    view.contentDescription = context.getString(
      R.string.catalogAccessibilityFacet,
      choice.title,
      title
    )
    return view
  }
}
