package org.librarysimplified.ui.catalog

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.nypl.simplified.feeds.api.FeedFacet

class CatalogFacetAdapter  (context: Context,
                            private val title: String,
                            private val choices: List<FeedFacet>
) : ArrayAdapter<String>(context, android.R.layout.simple_list_item_single_choice, choices.map { it.title }) {

  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val view = super.getView(position, convertView, parent) as TextView
    val choice = choices[position]

    // Set the content description for accessibility
    view.contentDescription = context.getString(R.string.catalogAccessibilityFacet, choice.title, title)
    return view
  }
}
