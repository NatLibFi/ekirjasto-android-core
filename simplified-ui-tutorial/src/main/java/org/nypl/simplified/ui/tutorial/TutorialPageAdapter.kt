package org.nypl.simplified.ui.tutorial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class TutorialPageAdapter : RecyclerView.Adapter<TutorialPageAdapter.TutorialPageViewHolder>() {

  private val images = arrayOf(
    R.drawable.e_kirjasto_intro1,
    R.drawable.e_kirjasto_intro2,
    R.drawable.e_kirjasto_intro3,
    R.drawable.e_kirjasto_intro4
  )

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
    holder.bind(images[position])
  }

  inner class TutorialPageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(imageResource: Int) {
      (itemView as ImageView).setImageResource(imageResource)
    }
  }
}
