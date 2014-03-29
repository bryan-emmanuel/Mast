/*
 * Mast - Cast Web Media Player
 * Copyright (C) 2013 Bryan Emmanuel
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Bryan Emmanuel piusvelte@gmail.com
 */
package com.piusvelte.mast;

import java.util.ArrayList;
import java.util.List;

import com.piusvelte.mast.utils.MediaUrlUtils;
import com.squareup.picasso.Picasso;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class MediaListFragment extends ListFragment {

    private static final String TAG = MediaListFragment.class.getSimpleName();
    public static final String EXTRA_DIR_POSITION = "com.piusvelte.webcaster.EXTRA_DIR_POSITION";

    int dirPosition = 0;
    MediaListAdapter adapter;
    private Listener callback;

    interface Listener {

        List<Medium> getMediaAt(int dirPosition);

        void openDir(int parent, int child);

        void openMedium(int parent, int child);

        String getHost();
    }

    public static MediaListFragment newInstance(int dirPosition) {
        MediaListFragment fragment = new MediaListFragment();
        Bundle args = new Bundle();
        args.putInt(EXTRA_DIR_POSITION, dirPosition);
        fragment.setArguments(args);
        return fragment;
    }

    public void onMediaLoaded(List<Medium> media) {
        adapter.clear();
        adapter.addAll(media);
        adapter.notifyDataSetChanged();
    }

    class MediaListAdapter extends ArrayAdapter<Medium> {

        public MediaListAdapter(Context context, int textViewResourceId,
                List<Medium> rowMedium) {
            super(context, textViewResourceId, rowMedium);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            MediaViewHolder viewHolder;

            if (view == null) {
                view = (View) (LayoutInflater.from(parent.getContext())).inflate(
                        R.layout.media_list_item, null);
                viewHolder = new MediaViewHolder();
                viewHolder.imgCover = (ImageView) view.findViewById(R.id.cover);
                viewHolder.tvFile = (TextView) view.findViewById(R.id.title);
                viewHolder.forward = view.findViewById(R.id.forward);
                view.setTag(viewHolder);
            } else {
                viewHolder = (MediaViewHolder) view.getTag();
            }

            Medium medium = adapter.getItem(position);

            if (!TextUtils.isEmpty(medium.getImg())) {
                Picasso.with(getContext())
                        .load(MediaUrlUtils.getCoverUrl(callback.getHost(), medium))
                        .resizeDimen(R.dimen.cover_width,
                                R.dimen.cover_height)
                        .placeholder(android.R.drawable.ic_menu_rotate)
                        .error(android.R.drawable.ic_menu_close_clear_cancel)
                        .into(viewHolder.imgCover);
            } else {
                Picasso.with(getContext())
                        .load(android.R.drawable.ic_menu_close_clear_cancel)
                        .into(viewHolder.imgCover);
            }

            viewHolder.tvFile.setText(medium.getTitle());
            viewHolder.forward.setVisibility(medium.getDir().size() > 0 ? View.VISIBLE : View.GONE);

            return view;
        }
    }

    static class MediaViewHolder {
        ImageView imgCover;
        TextView tvFile;
        View forward;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            callback = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View rootView = inflater.inflate(R.layout.media_list, container, false);
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        adapter = new MediaListAdapter(getActivity(), R.layout.media_list_item,
                new ArrayList<Medium>());
        setListAdapter(adapter);
        Bundle extras = getArguments();

        if (extras != null) {
            dirPosition = extras.getInt(EXTRA_DIR_POSITION, 0);
        }
    }

    @Override
    public void onListItemClick(ListView list, View view, int position, long id) {
        super.onListItemClick(list, view, position, id);

        if (callback != null) {
            Medium medium = adapter.getItem(position);

            if (medium.getDir().size() > 0) {
                callback.openDir(dirPosition, position);
            } else if (medium.getFile() != null) {
                callback.openMedium(dirPosition, position);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (adapter.isEmpty() && (callback != null)) {
            onMediaLoaded(callback.getMediaAt(dirPosition));
        }
    }
}
