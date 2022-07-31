// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Read and parse res/raw/changelog.xml.
 */
// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
public class ChangelogParser {
    private static final String TAG = ChangelogParser.class.getSimpleName();

    private static final String TAG_CHANGELOG = "changelog";
    private static final String TAG_RELEASE = "release";
    private static final String TAG_TITLE = "title";
    private static final String TAG_NEW = "new";
    private static final String TAG_IMPROVE = "improve";
    private static final String TAG_FIX = "fix";
    private static final String TAG_NOTE = "note";

    private static final String ATTR_RELEASE_TYPE = "type";
    private static final String ATTR_RELEASE_VERSION = "version";
    private static final String ATTR_RELEASE_CODE = "code";
    private static final String ATTR_RELEASE_DATE = "date";

    private static final String ATTR_TYPE = "type";

    private static final String ATTR_TITLE = "title";
    private static final String ATTR_BULLET = "bullet";
    private static final String ATTR_SUBTEXT = "subtext";

    private static final List<String> CHANGE_LOG_TAGS = new ArrayList<String>() {{
        add(TAG_TITLE);
        add(TAG_NEW);
        add(TAG_IMPROVE);
        add(TAG_FIX);
        add(TAG_NOTE);
    }};

    protected Context mContext;

    @RawRes
    private final int mChangeLogFileResourceId;
    private final long mStartVersion;

    /**
     * Create a new instance for a context and for a custom changelog file.
     * <p>
     * You have to use file in res/raw folder.
     *
     * @param context                 current Context
     * @param changeLogFileResourceId reference for a custom xml file
     */
    public ChangelogParser(@NonNull Context context, @RawRes int changeLogFileResourceId) {
        this(context, changeLogFileResourceId, 0);
    }

    /**
     * Create a new instance for a context and for a custom changelog file.
     * <p>
     * You have to use file in res/raw folder.
     *
     * @param context                 current Context
     * @param changeLogFileResourceId reference for a custom xml file
     */
    public ChangelogParser(@NonNull Context context, @RawRes int changeLogFileResourceId, long startVersion) {
        mContext = context;
        mChangeLogFileResourceId = changeLogFileResourceId;
        mStartVersion = startVersion;
    }

    /**
     * Read and parse res/raw/changelog.xml
     *
     * @return {@link Changelog} obj with all data
     * @throws IOException            if changelog.xml is not found
     * @throws XmlPullParserException if there are errors during parsing
     */
    @NonNull
    public Changelog parse() throws IOException, XmlPullParserException {
        try (InputStream is = mContext.getResources().openRawResource(mChangeLogFileResourceId)) {
            Changelog changelog = new Changelog();
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(is, "UTF-8");
            parser.nextTag();
            // Read changelog tag
            readChangelogTag(parser, changelog);
            return changelog;
        }
    }


    /**
     * Parse changelog node
     */
    protected void readChangelogTag(@NonNull XmlPullParser parser, @NonNull Changelog changeLog) throws IOException, XmlPullParserException {
        // changelog is the root
        if (parser.getDepth() != 1) {
            Log.e(TAG, String.format(Locale.ROOT, "Invalid depth %d, expecting depth 1.", parser.getDepth()));
            return;
        }
        parser.require(XmlPullParser.START_TAG, null, TAG_CHANGELOG);

        // Read attributes
        String showBullet = parser.getAttributeValue(null, ATTR_BULLET);
        changeLog.setBulletedList("true".equals(showBullet));

        // Parse nested nodes
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG || parser.getDepth() != 2) {
                continue;
            }
            if (TAG_RELEASE.equals(parser.getName())) {
                // Parse new, improve, fix, note
                readReleaseTag(parser, changeLog);
            } else {
                Log.w(TAG, String.format(Locale.ROOT, "Unknown tag (%s) at depth 2." + parser.getName()));
            }
        }
    }

    /**
     * Parse changeLogVersion node
     */
    private void readReleaseTag(@NonNull XmlPullParser parser, @NonNull Changelog changeLog) throws IOException, XmlPullParserException {
        // Ensure release tag
        if (parser.getDepth() != 2) {
            Log.e(TAG, String.format(Locale.ROOT, "Invalid depth %d, expecting depth 2.", parser.getDepth()));
            return;
        }
        parser.require(XmlPullParser.START_TAG, null, TAG_RELEASE);

        // Read attributes
        String versionName = Objects.requireNonNull(parser.getAttributeValue(null, ATTR_RELEASE_VERSION));
        String versionCodeStr = Objects.requireNonNull(parser.getAttributeValue(null, ATTR_RELEASE_CODE));
        long versionCode = 0;
        try {
            versionCode = Integer.parseInt(versionCodeStr);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Error while parsing versionCode.");
        }
        String releaseDate = Objects.requireNonNull(parser.getAttributeValue(null, ATTR_RELEASE_DATE));
        String releaseType = Objects.requireNonNull(parser.getAttributeValue(null, ATTR_RELEASE_TYPE));

        // Skip parsing this node if versionCode < startVersionCode
        if (versionCode < mStartVersion) {
            while (parser.next() != XmlPullParser.END_TAG) {
                // Continue parsing until end is reached
            }
            return;
        }

        // Set release meta
        changeLog.addItem(new ChangelogHeader(versionName, versionCode, releaseType, releaseDate));

        // Parse nested nodes
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG || parser.getDepth() != 3) {
                continue;
            }
            if (CHANGE_LOG_TAGS.contains(parser.getName())) {
                readChangelogItemTags(parser, changeLog);
            } else {
                Log.w(TAG, String.format(Locale.ROOT, "Unknown tag (%s) at depth 3." + parser.getName()));
            }
        }
    }

    /**
     * Parse changeLogText node
     */
    private void readChangelogItemTags(@NonNull XmlPullParser parser, @NonNull Changelog changeLog) throws XmlPullParserException, IOException {
        if (parser.getDepth() != 3) {
            Log.e(TAG, String.format(Locale.ROOT, "Invalid depth %d, expecting depth 3.", parser.getDepth()));
            return;
        }

        String tag = parser.getName();

        // Read attributes
        String changeTextType = parser.getAttributeValue(null, ATTR_TYPE);
        String title = parser.getAttributeValue(null, ATTR_TITLE);
        String showBullet = parser.getAttributeValue(null, ATTR_BULLET);
        String subtext = parser.getAttributeValue(null, ATTR_SUBTEXT);

        // Read text
        String changeText = null;
        if (parser.next() == XmlPullParser.TEXT) {
            changeText = parser.getText();
            parser.nextTag();
        }

        // Set type
        int type;
        switch (tag) {
            default:
            case TAG_NOTE:
                type = ChangelogItem.NOTE;
                break;
            case TAG_TITLE:
                type = ChangelogItem.TITLE;
                break;
            case TAG_NEW:
                type = ChangelogItem.NEW;
                break;
            case TAG_IMPROVE:
                type = ChangelogItem.IMPROVE;
                break;
            case TAG_FIX:
                type = ChangelogItem.FIX;
                break;
        }

        ChangelogItem changelogItem = changeText == null ? new ChangelogItem(type) : new ChangelogItem(changeText, type);
        changelogItem.setChangeTitle(title);
        changelogItem.setBulletedList("true".equals(showBullet) || changeLog.isBulletedList());
        changelogItem.setChangeTextType(getChangeTextType(changeTextType));
        changelogItem.setSubtext("true".equals(subtext));
        changeLog.addItem(changelogItem);
    }

    @ChangelogItem.ChangeTextType
    private static int getChangeTextType(@Nullable String rawText) {
        if (rawText == null) {
            return ChangelogItem.TEXT_MEDIUM;
        }
        switch (rawText) {
            default:
            case "medium":
                return ChangelogItem.TEXT_MEDIUM;
            case "large":
                return ChangelogItem.TEXT_LARGE;
            case "small":
                return ChangelogItem.TEXT_SMALL;
        }
    }
}