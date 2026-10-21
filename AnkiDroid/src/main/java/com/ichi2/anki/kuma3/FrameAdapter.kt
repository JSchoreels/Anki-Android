// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.kuma3

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/** A deck list adapter whose rows are each a full-width FrameLayout, filled by onBindViewHolder. */
abstract class FrameAdapter : RecyclerView.Adapter<FrameAdapter.Holder>() {
    class Holder(
        val frame: FrameLayout,
    ) : RecyclerView.ViewHolder(frame)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ) = Holder(
        FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        },
    )
}
