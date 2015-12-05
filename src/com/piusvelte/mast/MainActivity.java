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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.common.images.WebImage;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.widgets.MiniController;
import com.piusvelte.eidos.Eidos;
import com.piusvelte.mast.utils.MediaUrlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LoaderCallbacks<List<Medium>>, MediaListFragment.Listener,
        ViewPager.OnPageChangeListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int FRAGMENT_MEDIA_ROOT = 0;
    private static final String CONTENT_TYPE = "video/mp4";
    private static final String PREFERENCE_KEY_HOST = "host";

    private String mMediaHost = null;
    private List<Medium> mMedia = new ArrayList<Medium>();
    private List<Integer> mDirIdx = new ArrayList<Integer>();
    private DirPagerAdapter mDirPagerAdapter;
    private ViewPager mViewPager;
    private VideoCastManager mCastManager;
    private MiniController mMiniController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VideoCastManager.checkGooglePlayServices(this);
        setContentView(R.layout.activity_main);

        initCastManager();
        setupMiniController();

        mCastManager.reconnectSessionIfPossible();

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the app.
        mDirPagerAdapter = new DirPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mDirPagerAdapter);
        mViewPager.addOnPageChangeListener(this);

        mMediaHost = PreferenceManager.getDefaultSharedPreferences(this).getString(PREFERENCE_KEY_HOST, null);

        if (mMediaHost != null) {
            getSupportLoaderManager().initLoader(0, null, this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        mCastManager.addMediaRouterButton(menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCastManager.incrementUiCounter();

        if (mMediaHost == null) {
            startActivityForResult(new Intent(this, SettingsActivity.class)
                    .putExtra(SettingsActivity.EXTRA_HOST, mMediaHost), 0);
        }
    }

    private void initCastManager() {
        mCastManager = VideoCastManager.getInstance();
    }

    private void setupMiniController() {
        mMiniController = (MiniController) findViewById(R.id.miniController);
        mCastManager.addMiniController(mMiniController);
    }

    @Override
    protected void onActivityResult(int resultCode, int resultType, Intent data) {
        resultCode = resultType >> 16;

        if ((resultCode == RESULT_OK) && (data != null)) {
            String resultHost = data
                    .getStringExtra(SettingsActivity.EXTRA_HOST);

            if (mMediaHost == null && resultHost != null) {
                mMediaHost = resultHost;
                storeHost();
                reloadMedia();
            } else if (mMediaHost != null && !mMediaHost.equals(resultHost)) {
                mMediaHost = resultHost;

                if (resultHost == null) {
                    // TODO stop playing
                }

                storeHost();
                reloadMedia();
            }
        }
    }

    @Override
    protected void onPause() {
        mCastManager.decrementUiCounter();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mCastManager.removeMiniController(mMiniController);
        super.onDestroy();
    }

    private Medium getMediumAt(int position) {
        if (position == 0) {
            return null;
        } else if (position < mDirIdx.size()) {
            int i = 1;
            Medium m = mMedia.get(mDirIdx.get(i));

            while (i < position) {
                i++;
                m = m.getMediumAt(mDirIdx.get(i));
            }

            return m;
        } else {
            return null;
        }
    }

    @Override
    public List<Medium> getMediaAt(int dirPosition) {
        if (dirPosition == 0) {
            return mMedia;
        } else {
            Medium m = getMediumAt(dirPosition);
            if (m != null) {
                return m.getDir();
            } else {
                return new ArrayList<Medium>();
            }
        }
    }

    @Override
    public Loader<List<Medium>> onCreateLoader(int arg0, Bundle arg1) {
        if (arg0 == 0) {
            return new MediaLoader(this, mMediaHost);
        } else {
            return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<List<Medium>> arg0, List<Medium> media) {
        mMedia = media;
        mDirIdx.clear();
        mDirIdx.add(0);
        mDirPagerAdapter.notifyDataSetChanged();
        mViewPager.setCurrentItem(FRAGMENT_MEDIA_ROOT);
        Fragment mediaListFragment = getSupportFragmentManager().findFragmentByTag(getFragmentTag(FRAGMENT_MEDIA_ROOT));
        if (mediaListFragment instanceof MediaListFragment) ((MediaListFragment) mediaListFragment).onMediaLoaded(mMedia);
    }

    @Override
    public void onLoaderReset(Loader<List<Medium>> arg0) {
        // NO-OP
    }

    private String getFragmentTag(int position) {
        return "android:switcher:" + R.id.pager + ":" + position;
    }

    @Override
    public void openDir(int parent, int child) {
        int currSize = mDirIdx.size() - 1;

        while (currSize > parent) {
            mDirIdx.remove(currSize);
            currSize--;
        }

        mDirIdx.add(child);
        mDirPagerAdapter.notifyDataSetChanged();
        parent++;
        mViewPager.setCurrentItem(parent, true);
        MediaListFragment mediaListFragment = (MediaListFragment) getSupportFragmentManager()
                .findFragmentByTag(getFragmentTag(parent));
        mediaListFragment.onMediaLoaded(getMediaAt(parent));
    }

    @Override
    public void openMedium(int parent, int child) {
        Medium medium = getMediaAt(parent).get(child);
        String title = medium.getTitle();
        String videoUrl = MediaUrlUtils.getVideoUrl(mMediaHost, medium);
        Uri imageUrl = MediaUrlUtils.getCoverUri(mMediaHost, medium);
        WebImage image = new WebImage(imageUrl);

        MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        metadata.putString(MediaMetadata.KEY_TITLE, title);
        metadata.putString(MediaMetadata.KEY_SUBTITLE, title);
        metadata.putString(MediaMetadata.KEY_STUDIO, title);
        // notification
        metadata.addImage(image);
        // lockscreen
        metadata.addImage(image);

        MediaInfo mediaInfo = new MediaInfo.Builder(videoUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(CONTENT_TYPE)
                .setMetadata(metadata)
                .build();

        mCastManager.startVideoCastControllerActivity(this, mediaInfo, 0, true);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mCastManager.onDispatchVolumeKeyEvent(event, mCastManager.getVolumeStep())) {
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_refresh) {
            if (mMediaHost != null) {
                Loader<List<Medium>> loader = getSupportLoaderManager()
                        .initLoader(0, null, this);
                if (loader != null) {
                    loader.forceLoad();
                }
            }

            return true;
        } else if (itemId == android.R.id.home) {
            int tabIdx = mViewPager.getCurrentItem();

            if (tabIdx > 0) {
                mViewPager.setCurrentItem(--tabIdx);
            }

            return true;
        } else if (itemId == R.id.action_settings) {
            startActivityForResult(
                    new Intent(this, SettingsActivity.class).putExtra(
                            SettingsActivity.EXTRA_HOST, mMediaHost), 0
            );

            return true;
        } else if (itemId == R.id.action_about) {
            AboutDialogFragment.newInstance().show(getSupportFragmentManager(), "dialog:about");
        } else if (itemId == android.R.id.home) {
            pageUpDirectory();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public String getHost() {
        return mMediaHost;
    }

    private void reloadMedia() {
        mMedia = new ArrayList<Medium>();
        mDirIdx.clear();
        mDirIdx.add(0);
        mDirPagerAdapter.notifyDataSetChanged();
        MediaLoader loader = (MediaLoader) getSupportLoaderManager()
                .initLoader(0, null, this);

        if (loader != null) {
            loader.loadHost(mMediaHost);
        }
    }

    private void storeHost() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();

        if (mMediaHost != null) {
            editor.putString(PREFERENCE_KEY_HOST, mMediaHost);
        } else {
            editor.remove(PREFERENCE_KEY_HOST);
        }

        editor.commit();
        Eidos.requestBackup(this);
    }

    private boolean isPagerAtRoot() {
        return mViewPager.getCurrentItem() == 0;
    }

    private boolean pageUpDirectory() {
        if (!isPagerAtRoot()) {
            mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1, true);
            return true;
        }

        return false;
    }

    @Override
    public void onBackPressed() {
        // go up a directory if possible
        if (!pageUpDirectory()) {
            super.onBackPressed();
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // NO-OP
    }

    @Override
    public void onPageSelected(int position) {
        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            boolean isSubFolder = !isPagerAtRoot();
            actionBar.setDisplayHomeAsUpEnabled(isSubFolder);
            actionBar.setHomeButtonEnabled(isSubFolder);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // NO-OP
    }

    public class DirPagerAdapter extends FragmentPagerAdapter {

        public DirPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return MediaListFragment.newInstance(position);
        }

        @Override
        public int getCount() {
            return mDirIdx.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();

            if (position == FRAGMENT_MEDIA_ROOT) {
                return mMediaHost.toUpperCase(l);
            } else {
                Medium medium = getMediumAt(position);
                if (medium != null) {
                    return medium.getTitle().toUpperCase(l);
                } else {
                    return "";
                }
            }
        }
    }
}
