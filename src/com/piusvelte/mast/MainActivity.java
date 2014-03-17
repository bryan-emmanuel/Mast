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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;

public class MainActivity extends ActionBarActivity implements
        LoaderCallbacks<List<Medium>>, MediaListFragment.Listener, OnClickListener, OnSeekBarChangeListener {

    protected static final double MAX_VOLUME_LEVEL = 20;
    private static final String TAG = "MainActivity";
    private static final double VOLUME_INCREMENT = 0.05;
    private static final int FRAGMENT_MEDIA_ROOT = 0;
    private static final String URL_FORMAT = "http://%s/%s";
    private static final String CONTENT_TYPE = "video/mp4";

    private String mMediaHost = null;
    private List<Medium> mMedia = new ArrayList<Medium>();
    private List<Integer> mDirIdx = new ArrayList<Integer>();
    private DirPagerAdapter mDirPagerAdapter;
    private ViewPager mViewPager;
    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private MediaRouter.Callback mMediaRouterCallback;
    private CastDevice mCastDevice;
    private Button mBtnPlay;
    private SeekBar mSeekBar;
    private Object mIsSeekingLock = new Object();
    private boolean mIsSeeking = false;
    private Cast.Listener mCastListener;
    private GoogleApiClient.ConnectionCallbacks mConnectionCallbacks;
    private ConnectionFailedListener mConnectionFailedListener;
    private GoogleApiClient mApiClient;
    private RemoteMediaPlayer mRemoteMediaPlayer;
    private boolean mWaitingForReconnect = false;
    private boolean mApplicationStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the app.
        mDirPagerAdapter = new DirPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mDirPagerAdapter);

        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(this);
        mMediaHost = sp.getString(getString(R.string.preference_host), null);

        if (mMediaHost != null) {
            startDiscovery();
        }

        mSeekBar = (SeekBar) findViewById(R.id.seek);
        mSeekBar.setOnSeekBarChangeListener(this);

        mBtnPlay = (Button) findViewById(R.id.btn_play);
        mBtnPlay.setOnClickListener(this);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        if (mMediaHost != null) {
            getSupportLoaderManager().initLoader(0, null, this);
        }
    }

    private void startDiscovery() {
        String appId = getString(R.string.app_id);
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(appId))
                .build();
        mMediaRouterCallback = new WebCasterMediaRouterCallback();
    }

    private void startAPIConnection() {
        try {
            mCastListener = new Cast.Listener() {

                @Override
                public void onApplicationDisconnected(int errorCode) {
                    tearDown();
                }

                @Override
                public void onApplicationStatusChanged() {
                    if (mApiClient != null) {
                        Log.d(TAG, "onApplicationStatusChanged: "
                                + Cast.CastApi.getApplicationStatus(mApiClient));
                    }
                }

                @Override
                public void onVolumeChanged() {
                    if (mApiClient != null) {
                        Log.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(mApiClient));
                    }
                }
            };
            // Connect to Google Play services
            mConnectionCallbacks = new ConnectionCallbacks();
            mConnectionFailedListener = new ConnectionFailedListener();
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                    .builder(mCastDevice, mCastListener);
            mApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(mConnectionCallbacks)
                    .addOnConnectionFailedListener(mConnectionFailedListener)
                    .build();

            mApiClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed launchReceiver", e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem mediaRouteItem = menu.findItem(R.id.action_mediaroute);

        if (mMediaRouteSelector != null) {
            MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat
                    .getActionProvider(mediaRouteItem);
            // Set the MediaRouteActionProvider selector for device discovery.
            mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
            mediaRouteItem.setVisible(true);
            mediaRouteItem.setEnabled(true);
        } else {
            mediaRouteItem.setVisible(false);
            mediaRouteItem.setEnabled(false);
        }

        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mMediaRouter != null) {
            mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mMediaHost == null) {
            startActivityForResult(
                    new Intent(this, SettingsActivity.class).putExtra(
                            SettingsActivity.EXTRA_HOST, mMediaHost), 0
            );
        } else {
            mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
        }
    }

    @Override
    protected void onActivityResult(int resultCode, int resultType, Intent data) {
        resultCode = resultType >> 16;

        if ((resultCode == RESULT_OK) && (data != null)) {
            String resultHost = data
                    .getStringExtra(SettingsActivity.EXTRA_HOST);

            if (mMediaHost == null && resultHost != null) {
                mMediaHost = resultHost;
                startDiscovery();
                invalidateOptionsMenu();
            } else if (mMediaHost != null && !mMediaHost.equals(resultHost)) {
                mMediaHost = resultHost;
                if (resultHost == null) {
                    tearDown();
                }

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
        }
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            mMediaRouter.removeCallback(mMediaRouterCallback);
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(getString(R.string.preference_host), mMediaHost)
                    .commit();
        }

        super.onPause();
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
        MediaListFragment mediaListFragment = (MediaListFragment) getSupportFragmentManager()
                .findFragmentByTag(getFragmentTag(FRAGMENT_MEDIA_ROOT));
        mediaListFragment.onMediaLoaded(mMedia);
    }

    @Override
    public void onLoaderReset(Loader<List<Medium>> arg0) {
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
        Medium m = getMediaAt(parent).get(child);

        if (mRemoteMediaPlayer != null) {
            String title = m.getFile().substring(m.getFile().lastIndexOf(File.separator) + 1);
            String url = String.format(URL_FORMAT, mMediaHost,
                    m.getFile());

            MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
            mediaMetadata.putString(MediaMetadata.KEY_TITLE, title);
            MediaInfo mediaInfo = new MediaInfo.Builder(url)
                    .setContentType(CONTENT_TYPE)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setMetadata(mediaMetadata)
                    .build();

            try {
                mRemoteMediaPlayer.load(mApiClient, mediaInfo, true)
                        .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                            @Override
                            public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                                if (!result.getStatus().isSuccess()) {
                                    // TODO no play success
                                }
                            }
                        });
            } catch (IllegalStateException e) {
                Log.e(TAG, "Problem occurred with media during loading", e);
            } catch (Exception e) {
                Log.e(TAG, "Problem opening media during loading", e);
            }
        } else {
            // TODO no player error
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_play) {
            if (mRemoteMediaPlayer != null) {
                String text = ((Button) v).getText().toString();
                if (text.startsWith(getString(R.string.play))) {
                    try {
                        mRemoteMediaPlayer.play(mApiClient);
                        mBtnPlay.setText(R.string.pause);
                        Toast.makeText(this, getString(R.string.play), Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        Log.e(TAG, "failed to play/resume", e);
                    }
                } else {
                    try {
                        mRemoteMediaPlayer.pause(mApiClient);
                        mBtnPlay.setText(R.string.play);
                        Toast.makeText(this, getString(R.string.pause), Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        Log.e(TAG, "failed to pause", e);
                    }
                }
            }
        } else {
            Toast.makeText(this, getString(R.string.select_cast_device),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public String getTitle(int dirPosition) {
        return mDirPagerAdapter.getPageTitle(dirPosition).toString();
    }

    private void setVolume(Double volume) {
        try {
            Cast.CastApi.setVolume(mApiClient, volume);
        } catch (Exception e) {
            Log.e(TAG, "unable to set volume", e);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mRemoteMediaPlayer != null) {
                        double currentVolume = Cast.CastApi.getVolume(mApiClient);
                        if (currentVolume < 1.0) setVolume(Math.min(currentVolume + VOLUME_INCREMENT, 1.0));
                    } else {
                        Log.e(TAG, "dispatchKeyEvent - volume up");
                    }
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mRemoteMediaPlayer != null) {
                        double currentVolume = Cast.CastApi.getVolume(mApiClient);
                        if (currentVolume > 0.0) setVolume(Math.max(currentVolume - VOLUME_INCREMENT, 0.0));
                    } else {
                        Log.e(TAG, "dispatchKeyEvent - volume down");
                    }
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
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
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
        synchronized (mIsSeekingLock) {
            mIsSeeking = true;
        }

        if (mRemoteMediaPlayer != null) {
            try {
                mRemoteMediaPlayer.pause(mApiClient);
            } catch (IOException e) {
                Log.e(TAG, "failed to pause", e);
            }
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        synchronized (mIsSeekingLock) {
            mIsSeeking = false;
        }

        if (mRemoteMediaPlayer != null) {
            mRemoteMediaPlayer.seek(mApiClient, seekBar.getProgress());
        }
    }

    @Override
    public String getHost() {
        return mMediaHost;
    }

    private void setMediaPlayerCallbacks() {
        try {
            Cast.CastApi.setMessageReceivedCallbacks(mApiClient,
                    mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer);
        } catch (IOException e) {
            Log.e(TAG, "Exception while creating media channel", e);
        }
    }

    private void createMediaPlayer() {
        mRemoteMediaPlayer = new RemoteMediaPlayer();
        mRemoteMediaPlayer.setOnStatusUpdatedListener(new PlayerStatusListener());
        mRemoteMediaPlayer.setOnMetadataUpdatedListener(new PlayerMetaDataListener());
    }

    private void startMediaPlayer() {
        mRemoteMediaPlayer
                .requestStatus(mApiClient)
                .setResultCallback(
                        new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                            @Override
                            public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                                if (!result.getStatus().isSuccess()) {
                                    // TODO error occurred
                                }
                            }
                        }
                );
    }

    /**
     * Tear down the connection to the receiver
     */
    private void tearDown() {
        if (mApiClient != null) {
            if (mApplicationStarted) {
                if (mApiClient.isConnected()) {
                    try {
                        Cast.CastApi.stopApplication(mApiClient);

                        if (mRemoteMediaPlayer != null) {
                            Cast.CastApi.removeMessageReceivedCallbacks(mApiClient, mRemoteMediaPlayer.getNamespace());
                            mRemoteMediaPlayer = null;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception while removing channel", e);
                    }

                    mApiClient.disconnect();
                }

                mApplicationStarted = false;
            }

            mApiClient = null;
        }

        mCastDevice = null;
        mWaitingForReconnect = false;
    }

    private class PlayerStatusListener implements RemoteMediaPlayer.OnStatusUpdatedListener {

        @Override
        public void onStatusUpdated() {
            MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();
            if (mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING) {
                MediaInfo mediaInfo = mRemoteMediaPlayer.getMediaInfo();

                if (mediaInfo != null) {
                    mSeekBar.setMax((int) mediaInfo.getStreamDuration());
                }
            }
        }
    }

    private class PlayerMetaDataListener implements RemoteMediaPlayer.OnMetadataUpdatedListener {

        @Override
        public void onMetadataUpdated() {
            // TODO
            MediaInfo mediaInfo = mRemoteMediaPlayer.getMediaInfo();
            MediaMetadata metadata = mediaInfo.getMetadata();
        }
    }

    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {
        @Override
        public void onConnected(Bundle connectionHint) {
            if (mApiClient == null) {
                return;
            }

            try {
                if (mWaitingForReconnect) {
                    mWaitingForReconnect = false;

                    // Check if the receiver app is still running
                    if (connectionHint != null && connectionHint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
                        tearDown();
                    } else {
                        setMediaPlayerCallbacks();
                    }
                } else {
                    createMediaPlayer();
                    setMediaPlayerCallbacks();
                    startMediaPlayer();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch application", e);
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            mWaitingForReconnect = true;
        }
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {
        @Override
        public void onConnectionFailed(ConnectionResult result) {
            tearDown();
        }
    }

    private class WebCasterMediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteSelected(MediaRouter router, RouteInfo info) {
            mCastDevice = CastDevice.getFromBundle(info.getExtras());
            startAPIConnection();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, RouteInfo info) {
            tearDown();
            mCastDevice = null;
        }
    }

    public class DirPagerAdapter extends FragmentPagerAdapter {

        public DirPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment = new MediaListFragment();
            Bundle args = new Bundle();
            args.putInt(MediaListFragment.EXTRA_DIR_POSITION, position);
            fragment.setArguments(args);
            return fragment;
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
                Medium m = getMediumAt(position);
                if (m != null) {
                    return m.getFile().substring(m.getFile().lastIndexOf(File.separator) + 1).toUpperCase(l);
                } else {
                    return "";
                }
            }
        }
    }

    class CastStatusThread extends Thread {

        @Override
        public void run() {
            while (mRemoteMediaPlayer != null) {
                try {
                    synchronized (mIsSeekingLock) {
                        if (!mIsSeeking) {
                            final MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();
                            if (mediaStatus != null) {
                                final int position = (int) mediaStatus.getStreamPosition();
                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mSeekBar.getProgress() != position) {
                                            mSeekBar.setProgress(position);
                                        }
                                    }
                                });
                            }
                        }
                    }
                    Thread.sleep(1500);
                } catch (Exception e) {
                    Log.e(TAG, "Thread interrupted: ", e);
                }
            }
        }
    }
}
