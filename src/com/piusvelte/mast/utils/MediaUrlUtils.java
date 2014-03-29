package com.piusvelte.mast.utils;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.piusvelte.mast.Medium;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by bemmanuel on 3/29/14.
 */
public class MediaUrlUtils {

    private static final String TAG = "MediaHostUtils";
    private static final String PROTOCOL_REGEX = "^(https?)://.*$";
    private static final String DEFAULT_PROTOCOL = "http://";
    private static final String PATH_SEPARATOR = "/";

    public static String getHostWithProtocol(String host) {
        if (!host.matches(PROTOCOL_REGEX)) {
            return DEFAULT_PROTOCOL + host;
        }

        return host;
    }

    public static String getVideoUrl(String host, Medium medium) {
        return getEncodedUrl(host + PATH_SEPARATOR + medium.getFile());
    }

    public static String getCoverUrl(String host, Medium medium) {
        return getEncodedUrl(host + PATH_SEPARATOR + medium.getImg());
    }

    public static Uri getCoverUri(String host, Medium medium) {
        String url = getCoverUrl(host, medium);
        if (!TextUtils.isEmpty(url)) return Uri.parse(url);

        return null;
    }

    private static String getEncodedUrl(String url) {
        URL encodedUrl = getUrl(url);
        if (encodedUrl != null) return encodedUrl.toString();

        return null;
    }

    public static URL getUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        } else {
            url = getHostWithProtocol(url.replaceAll(" ", "%20"));

            try {
                return new URL(url);
            } catch (MalformedURLException e) {
                Log.e(TAG, "Bad url " + url, e);
            }
        }

        return null;
    }
}
