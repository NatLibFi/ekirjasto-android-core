package org.librarysimplified.ui.catalog

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedGroup
import java.net.URI

/**
 * A `ViewHolder` that represents a single swimlane within the [CatalogFeedWithGroupsAdapter].
 */
class CatalogFeedWithGroupsLaneViewHolder(
  private val parent: View,
  private val coverLoader: BookCoverProviderType,
  private val onFeedSelected: (title: String, uri: URI) -> Unit,
  private val onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit
) : RecyclerView.ViewHolder(parent) {

  private val feedLaneContainer =
    this.parent.findViewById<View>(R.id.feedLaneContainer)
  private val titleContainer =
    this.parent.findViewById<View>(R.id.feedLaneTitleContainer)
  private val title =
    this.parent.findViewById<TextView>(R.id.feedLaneTitle)
  private val more =
    this.parent.findViewById<TextView>(R.id.feedLaneMore)
  private val scrollView =
    this.parent.findViewById<RecyclerView>(R.id.feedLaneCoversScroll)

  init {
    scrollView.apply {
      setHasFixedSize(true)
      layoutManager = LinearLayoutManager(
        this.context, LinearLayoutManager.HORIZONTAL, false
      )
      addItemDecoration(
        SpaceItemDecoration(
          this.resources.getDimensionPixelSize(R.dimen.catalogFeedCoversSpace)
        )
      )
    }
  }

  fun bindTo(group: FeedGroup) {
    //Add audio description for the whole lane, title and books
    this.feedLaneContainer.contentDescription = parent.context.getString(R.string.catalogAccessibilityLane, group.groupTitle)
    this.title.text = group.groupTitle

    //Add audio description for the more button
    this.more.contentDescription = parent.context.getString(R.string.catalogAccessibilityLaneMore, group.groupTitle)
    this.more.setOnClickListener{
      this.onFeedSelected.invoke(group.groupTitle, group.groupURI)
    }
    /*
     * If the group is empty, there isn't much we can do.
     */

    if (group.groupEntries.isEmpty()) {
      this.scrollView.adapter = null
      return
    }

    //Set the group description for the lane as the group title
    this.scrollView.contentDescription = group.groupTitle

    /*
     * Populate our feed with our book covers
     */

    val filtered = group.groupEntries.filterIsInstance<FeedEntry.FeedEntryOPDS>()
    this.scrollView.adapter = CatalogLaneAdapter(
      filtered, coverLoader, onBookSelected
    )
  }

  fun unbind() {
    this.scrollView.adapter = null
  }
}
