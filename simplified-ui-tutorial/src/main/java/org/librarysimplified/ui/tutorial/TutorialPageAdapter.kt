package org.librarysimplified.ui.tutorial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class TutorialPageAdapter(descriptions :Array<String>) : RecyclerView.Adapter<TutorialPageAdapter.TutorialPageViewHolder>() {

  private val images = arrayOf(
    R.drawable.intro_1,
    R.drawable.intro_2,
    R.drawable.intro_3,
    R.drawable.intro_4
  )

  private val imDescriptions = descriptions

  override fun getItemCount(): Int {
    return this.images.size
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TutorialPageViewHolder {
    return TutorialPageViewHolder(
      LayoutInflater.from(parent.context).inflate(
        R.layout.view_tutorial_page, parent, false
      )
    )
  }

  override fun onBindViewHolder(holder: TutorialPageViewHolder, position: Int) {
    holder.bind(images[position], imDescriptions[position])
  }

  inner class TutorialPageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(imageResource: Int, description: String) {
      (itemView as ImageView).setImageResource(imageResource)
      (itemView as ImageView).contentDescription = description
    }
  }
}
