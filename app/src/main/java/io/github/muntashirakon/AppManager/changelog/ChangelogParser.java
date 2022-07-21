// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read and parse res/raw/changelog.xml.
 * Example:
 *
 * <pre>
 *    ChangelogParser parse = new ChangelogParser(this);
 *    Changelog log = parse.parse();
 * </pre>
 * <p>
 * If you want to use a custom xml file, you can use:
 * <pre>
 *    ChangelogParser parse = new ChangelogParser(this,R.raw.mycustomfile);
 *    Changelog log = parse.parse();
 * </pre>
 * <p>
 * It is a example for changelog.xml
 * <pre>
 *  <?xml version="1.0" encoding="utf-8"?>
 *       <changelog bulletedList=false>
 *            <changelogversion versionName="1.2" changeDate="20/01/2013">
 *                 <changelogtext>new feature to share data</changelogtext>
 *                 <changelogtext>performance improvement</changelogtext>
 *            </changelogversion>
 *            <changelogversion versionName="1.1" changeDate="13/01/2013">
 *                 <changelogtext>issue on wifi connection</changelogtext>*
 *            </changelogversion>
 *       </changelog>
 * </pre>
 */
// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
public class ChangelogParser {
    private static final String TAG = ChangelogParser.class.getSimpleName();

    private static final String TAG_CHANGELOG = "changelog";
    private static final String TAG_RELEASE = "release";
    private static final String TAG_NEW = "new";
    private static final String TAG_IMPROVE = "improve";
    private static final String TAG_FIX = "fix";
    private static final String TAG_NOTE = "note";

    private static final String ATTR_RELEASE_TYPE = "type";
    private static final String ATTR_RELEASE_VERSION = "version";
    private static final String ATTR_RELEASE_CODE = "code";
    private static final String ATTR_RELEASE_DATE = "date";

    private static final String ATTR_TITLE = "title";
    private static final String ATTR_BULLET = "bullet";

    private static final List<String> CHANGE_LOG_TAGS = new ArrayList<String>() {{
        add(TAG_NEW);
        add(TAG_IMPROVE);
        add(TAG_FIX);
        add(TAG_NOTE);
    }};

    protected Context mContext;

    @RawRes
    private final int mChangeLogFileResourceId;

    /**
     * Create a new instance for a context and for a custom changelog file.
     * <p>
     * You have to use file in res/raw folder.
     *
     * @param context                 current Context
     * @param changeLogFileResourceId reference for a custom xml file
     */
    public ChangelogParser(@NonNull Context context, @RawRes int changeLogFileResourceId) {
        this.mContext = context;
        this.mChangeLogFileResourceId = changeLogFileResourceId;
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
            parser.setInput(is, null);
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
        parser.require(XmlPullParser.START_TAG, null, TAG_CHANGELOG);

        // Read attributes
        String showBullet = parser.getAttributeValue(null, ATTR_BULLET);
        changeLog.setBulletedList("true".equals(showBullet));

        // Parse nested nodes
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (TAG_RELEASE.equals(parser.getName())) {
                // Parse new, improve, fix, note
                readReleaseTag(parser, changeLog);
            } else {
                Log.w(TAG, "Unknown tag: " + parser.getName());
            }
        }
    }

    /**
     * Parse changeLogVersion node
     */
    private void readReleaseTag(@NonNull XmlPullParser parser, @NonNull Changelog changeLog) throws IOException, XmlPullParserException {
        // Ensure release tag
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

        // Set release meta
        changeLog.addItem(new ChangelogHeader(versionName, versionCode, releaseType, releaseDate));

        // Parse nested nodes
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (CHANGE_LOG_TAGS.contains(parser.getName())) {
                readChangelogItemTags(parser, changeLog);
            } else {
                Log.w(TAG, "Unknown tag: " + parser.getName());
            }
        }
    }

    /**
     * Parse changeLogText node
     */
    private void readChangelogItemTags(@NonNull XmlPullParser parser, @NonNull Changelog changeLog) throws XmlPullParserException, IOException {
        String tag = parser.getName();

        // Read attributes
        String title = parser.getAttributeValue(null, ATTR_TITLE);
        String showBullet = parser.getAttributeValue(null, ATTR_BULLET);

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
        changeLog.addItem(changelogItem);
    }
}