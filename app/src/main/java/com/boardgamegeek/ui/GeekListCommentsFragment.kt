package com.boardgamegeek.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import com.boardgamegeek.R
import com.boardgamegeek.extensions.fadeIn
import com.boardgamegeek.extensions.fadeOut
import com.boardgamegeek.model.GeekListComment
import com.boardgamegeek.ui.adapter.GeekListCommentsRecyclerViewAdapter
import kotlinx.android.synthetic.main.fragment_geeklist_comments.*
import org.jetbrains.anko.support.v4.withArguments
import java.util.*

class GeekListCommentsFragment : Fragment() {
    private val adapter: GeekListCommentsRecyclerViewAdapter by lazy {
        GeekListCommentsRecyclerViewAdapter()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_geeklist_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
        recyclerView.adapter = adapter

        val comments: List<GeekListComment> = arguments?.getParcelableArrayList(KEY_COMMENTS)
                ?: emptyList()

        adapter.comments = comments
        if (comments.isEmpty()) {
            emptyView.fadeIn()
            recyclerView.fadeOut()
        } else {
            emptyView.fadeOut()
            recyclerView.fadeIn()
        }
        progressView.hide()
    }

    companion object {
        private const val KEY_COMMENTS = "GEEK_LIST_COMMENTS"

        fun newInstance(comments: ArrayList<GeekListComment>?): GeekListCommentsFragment {
            return GeekListCommentsFragment().withArguments(
                    KEY_COMMENTS to comments
            )
        }
    }
}
