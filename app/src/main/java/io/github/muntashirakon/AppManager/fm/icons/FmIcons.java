// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;

// Mostly taken from: https://github.com/zhanghai/MaterialFiles/blob/43a22b31d59a59e44a05a972269a3bd9cd0c9b8b/app/src/main/java/me/zhanghai/android/files/file/MimeTypeIcon.kt
// Others are freelanced.
final class FmIcons {
    private static final int DRAWABLE_APK = R.drawable.ic_android;
    private static final int DRAWABLE_ARCHIVE = R.drawable.ic_archive;
    private static final int DRAWABLE_AUDIO = R.drawable.ic_audio_file;
    private static final int DRAWABLE_CALENDAR = R.drawable.ic_calendar_month;
    private static final int DRAWABLE_CERTIFICATE = R.drawable.ic_shield_key;
    private static final int DRAWABLE_CODE = R.drawable.ic_code;
    private static final int DRAWABLE_CONTACT = R.drawable.ic_contact_page;
    private static final int DRAWABLE_DATABASE = R.drawable.ic_database;
    private static final int DRAWABLE_DIRECTORY = R.drawable.ic_folder;
    private static final int DRAWABLE_DOCUMENT = R.drawable.ic_file;
    private static final int DRAWABLE_EBOOK = R.drawable.ic_book;
    private static final int DRAWABLE_EMAIL = R.drawable.ic_email;
    private static final int DRAWABLE_FONT = R.drawable.ic_font_download;
    private static final int DRAWABLE_GENERIC = R.drawable.ic_file;
    private static final int DRAWABLE_IMAGE = R.drawable.ic_image;
    private static final int DRAWABLE_PDF = R.drawable.ic_pdf_file;
    private static final int DRAWABLE_PRESENTATION = R.drawable.ic_presentation;
    private static final int DRAWABLE_SPREADSHEET = R.drawable.ic_table;
    private static final int DRAWABLE_TEXT = R.drawable.ic_file_document;
    private static final int DRAWABLE_VIDEO = R.drawable.ic_video_file;
    private static final int DRAWABLE_WORD = DRAWABLE_DOCUMENT;
    private static final int DRAWABLE_EXCEL = DRAWABLE_SPREADSHEET;
    private static final int DRAWABLE_POWERPOINT = DRAWABLE_PRESENTATION;

    private static final Map<String, Integer> sMimeTypeToIconMap = new HashMap<String, Integer>() {
        {
            put("application/vnd.android.package-archive", DRAWABLE_APK);
            put("application/vnd.apkm", DRAWABLE_APK);
            put("application/x-apks", DRAWABLE_APK);
            put("application/xapk-package-archive", DRAWABLE_APK);
            put("application/gzip", DRAWABLE_ARCHIVE);
            // Not in IANA list, but Mozilla and Wikipedia say so.
            put("application/java-archive", DRAWABLE_ARCHIVE);
            put("application/mac-binhex40", DRAWABLE_ARCHIVE);
            // Not in IANA list, but AOSP MimeUtils says so.
            put("application/rar", DRAWABLE_ARCHIVE);
            put("application/zip", DRAWABLE_ARCHIVE);
            put("application/vnd.debian.binary-package", DRAWABLE_ARCHIVE);
            put("application/vnd.ms-cab-compressed", DRAWABLE_ARCHIVE);
            put("application/vnd.rar", DRAWABLE_ARCHIVE);
            put("application/x-7z-compressed", DRAWABLE_ARCHIVE);
            put("application/x-apple-diskimage", DRAWABLE_ARCHIVE);
            put("application/x-bzip", DRAWABLE_ARCHIVE);
            put("application/x-bzip2", DRAWABLE_ARCHIVE);
            put("application/x-compress", DRAWABLE_ARCHIVE);
            put("application/x-cpio", DRAWABLE_ARCHIVE);
            put("application/x-deb", DRAWABLE_ARCHIVE);
            put("application/x-debian-package", DRAWABLE_ARCHIVE);
            put("application/x-gtar", DRAWABLE_ARCHIVE);
            put("application/x-gtar-compressed", DRAWABLE_ARCHIVE);
            put("application/x-gzip", DRAWABLE_ARCHIVE);
            put("application/x-iso9660-image", DRAWABLE_ARCHIVE);
            put("application/x-java-archive", DRAWABLE_ARCHIVE);
            put("application/x-lha", DRAWABLE_ARCHIVE);
            put("application/x-lzh", DRAWABLE_ARCHIVE);
            put("application/x-lzma", DRAWABLE_ARCHIVE);
            put("application/x-lzx", DRAWABLE_ARCHIVE);
            put("application/x-rar", DRAWABLE_ARCHIVE);
            put("application/x-rar-compressed", DRAWABLE_ARCHIVE);
            put("application/x-stuffit", DRAWABLE_ARCHIVE);
            put("application/x-tar", DRAWABLE_ARCHIVE);
            put("application/x-webarchive", DRAWABLE_ARCHIVE);
            put("application/x-webarchive-xml", DRAWABLE_ARCHIVE);
            put("application/x-xz", DRAWABLE_ARCHIVE);
            put("application/ogg", DRAWABLE_AUDIO);
            put("application/x-flac", DRAWABLE_AUDIO);
            put("text/calendar", DRAWABLE_CALENDAR);
            put("text/x-vcalendar", DRAWABLE_CALENDAR);
            put("application/pem-certificate-chain", DRAWABLE_CERTIFICATE);
            put("application/pgp", DRAWABLE_CERTIFICATE);
            put("application/pgp-encrypted", DRAWABLE_CERTIFICATE);
            put("application/pgp-keys", DRAWABLE_CERTIFICATE);
            put("application/pgp-signature", DRAWABLE_CERTIFICATE);
            put("application/pkcs8", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs12", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs7-certificates", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs7-certreqresp", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs7-crl", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs7-mime", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs7-signature", DRAWABLE_CERTIFICATE);
            put("application/x-x509-ca-cert", DRAWABLE_CERTIFICATE);
            put("application/x-x509-server-cert", DRAWABLE_CERTIFICATE);
            put("application/x-x509-user-cert", DRAWABLE_CERTIFICATE);
            put("application/ecmascript", DRAWABLE_CODE);
            put("application/javascript", DRAWABLE_CODE);
            put("application/json", DRAWABLE_CODE);
            put("application/toml", DRAWABLE_CODE);
            put("application/typescript", DRAWABLE_CODE);
            put("application/xml", DRAWABLE_CODE);
            put("application/x-csh", DRAWABLE_CODE);
            put("application/x-ecmascript", DRAWABLE_CODE);
            put("application/x-javascript", DRAWABLE_CODE);
            put("application/x-latex", DRAWABLE_CODE);
            put("application/x-perl", DRAWABLE_CODE);
            put("application/x-plist", DRAWABLE_CODE);
            put("application/x-python", DRAWABLE_CODE);
            put("application/x-ruby", DRAWABLE_CODE);
            put("application/x-sh", DRAWABLE_CODE);
            put("application/x-shellscript", DRAWABLE_CODE);
            put("application/x-smali", DRAWABLE_CODE);
            put("application/x-texinfo", DRAWABLE_CODE);
            put("application/x-yaml", DRAWABLE_CODE);
            put("text/css", DRAWABLE_CODE);
            put("text/html", DRAWABLE_CODE);
            put("text/ecmascript", DRAWABLE_CODE);
            put("text/javascript", DRAWABLE_CODE);
            put("text/jscript", DRAWABLE_CODE);
            put("text/livescript", DRAWABLE_CODE);
            put("text/xml", DRAWABLE_CODE);
            put("text/x-asm", DRAWABLE_CODE);
            put("text/x-c++hdr", DRAWABLE_CODE);
            put("text/x-c++src", DRAWABLE_CODE);
            put("text/x-chdr", DRAWABLE_CODE);
            put("text/x-csh", DRAWABLE_CODE);
            put("text/x-csharp", DRAWABLE_CODE);
            put("text/x-csrc", DRAWABLE_CODE);
            put("text/x-dsrc", DRAWABLE_CODE);
            put("text/x-ecmascript", DRAWABLE_CODE);
            put("text/x-haskell", DRAWABLE_CODE);
            put("text/x-java", DRAWABLE_CODE);
            put("text/x-java-source", DRAWABLE_CODE);
            put("text/x-javascript", DRAWABLE_CODE);
            put("text/x-kotlin", DRAWABLE_CODE);
            put("text/x-literate-haskell", DRAWABLE_CODE);
            put("text/x-lua", DRAWABLE_CODE);
            put("text/x-pascal", DRAWABLE_CODE);
            put("text/x-perl", DRAWABLE_CODE);
            put("text/x-php", DRAWABLE_CODE);
            put("text/x-python", DRAWABLE_CODE);
            put("text/x-ruby", DRAWABLE_CODE);
            put("text/x-shellscript", DRAWABLE_CODE);
            put("text/x-tcl", DRAWABLE_CODE);
            put("text/x-tex", DRAWABLE_CODE);
            put("text/x-yaml", DRAWABLE_CODE);
            put("text/vcard", DRAWABLE_CONTACT);
            put("text/x-vcard", DRAWABLE_CONTACT);
            put("application/vnd.sqlite3", DRAWABLE_DATABASE);
            put("application/x-sqlite3", DRAWABLE_DATABASE);
            put("inode/directory", DRAWABLE_DIRECTORY);
            put(DocumentsContract.Document.MIME_TYPE_DIR, DRAWABLE_DIRECTORY);
            put("resource/folder", DRAWABLE_DIRECTORY);
            put("application/rtf", DRAWABLE_DOCUMENT);
            put("application/vnd.oasis.opendocument.text", DRAWABLE_DOCUMENT);
            put("application/vnd.oasis.opendocument.text-master", DRAWABLE_DOCUMENT);
            put("application/vnd.oasis.opendocument.text-template", DRAWABLE_DOCUMENT);
            put("application/vnd.oasis.opendocument.text-web", DRAWABLE_DOCUMENT);
            put("application/vnd.stardivision.writer", DRAWABLE_DOCUMENT);
            put("application/vnd.stardivision.writer-global", DRAWABLE_DOCUMENT);
            put("application/vnd.sun.xml.writer", DRAWABLE_DOCUMENT);
            put("application/vnd.sun.xml.writer.global", DRAWABLE_DOCUMENT);
            put("application/vnd.sun.xml.writer.template", DRAWABLE_DOCUMENT);
            put("application/x-abiword", DRAWABLE_DOCUMENT);
            put("application/x-kword", DRAWABLE_DOCUMENT);
            put("text/rtf", DRAWABLE_DOCUMENT);
            put("application/epub+zip", DRAWABLE_EBOOK);
            put("application/vnd.amazon.ebook", DRAWABLE_EBOOK);
            put("application/x-cbr", DRAWABLE_EBOOK);
            put("application/x-cbz", DRAWABLE_EBOOK);
            put("application/x-ibooks+zip", DRAWABLE_EBOOK);
            put("application/x-mobipocket-ebook", DRAWABLE_EBOOK);
            put("application/vnd.ms-outlook", DRAWABLE_EMAIL);
            put("message/rfc822", DRAWABLE_EMAIL);
            put("application/font-cff", DRAWABLE_FONT);
            put("application/font-off", DRAWABLE_FONT);
            put("application/font-sfnt", DRAWABLE_FONT);
            put("application/font-ttf", DRAWABLE_FONT);
            put("application/font-woff", DRAWABLE_FONT);
            put("application/vnd.ms-fontobject", DRAWABLE_FONT);
            put("application/vnd.ms-opentype", DRAWABLE_FONT);
            put("application/x-font", DRAWABLE_FONT);
            put("application/x-font-otf", DRAWABLE_FONT);
            put("application/x-font-ttf", DRAWABLE_FONT);
            put("application/x-font-woff", DRAWABLE_FONT);
            put("application/vnd.oasis.opendocument.graphics", DRAWABLE_IMAGE);
            put("application/vnd.oasis.opendocument.graphics-template", DRAWABLE_IMAGE);
            put("application/vnd.oasis.opendocument.image", DRAWABLE_IMAGE);
            put("application/vnd.stardivision.draw", DRAWABLE_IMAGE);
            put("application/vnd.sun.xml.draw", DRAWABLE_IMAGE);
            put("application/vnd.sun.xml.draw.template", DRAWABLE_IMAGE);
            put("application/vnd.visio", DRAWABLE_IMAGE);
            put("application/pdf", DRAWABLE_PDF);
            put("application/vnd.oasis.opendocument.presentation", DRAWABLE_PRESENTATION);
            put("application/vnd.oasis.opendocument.presentation-template", DRAWABLE_PRESENTATION);
            put("application/vnd.stardivision.impress", DRAWABLE_PRESENTATION);
            put("application/vnd.sun.xml.impress", DRAWABLE_PRESENTATION);
            put("application/vnd.sun.xml.impress.template", DRAWABLE_PRESENTATION);
            put("application/x-kpresenter", DRAWABLE_PRESENTATION);
            put("application/vnd.oasis.opendocument.spreadsheet", DRAWABLE_SPREADSHEET);
            put("application/vnd.oasis.opendocument.spreadsheet-template", DRAWABLE_SPREADSHEET);
            put("application/vnd.stardivision.calc", DRAWABLE_SPREADSHEET);
            put("application/vnd.sun.xml.calc", DRAWABLE_SPREADSHEET);
            put("application/vnd.sun.xml.calc.template", DRAWABLE_SPREADSHEET);
            put("application/x-kspread", DRAWABLE_SPREADSHEET);
            put("application/x-quicktimeplayer", DRAWABLE_VIDEO);
            put("application/x-shockwave-flash", DRAWABLE_VIDEO);
            put("application/msword", DRAWABLE_WORD);
            put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", DRAWABLE_WORD);
            put("application/vnd.openxmlformats-officedocument.wordprocessingml.template", DRAWABLE_WORD);
            put("application/vnd.ms-excel", DRAWABLE_EXCEL);
            put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", DRAWABLE_EXCEL);
            put("application/vnd.openxmlformats-officedocument.spreadsheetml.template", DRAWABLE_EXCEL);
            put("application/vnd.ms-powerpoint", DRAWABLE_POWERPOINT);
            put("application/vnd.openxmlformats-officedocument.presentationml.presentation", DRAWABLE_POWERPOINT);
            put("application/vnd.openxmlformats-officedocument.presentationml.slideshow", DRAWABLE_POWERPOINT);
            put("application/vnd.openxmlformats-officedocument.presentationml.template", DRAWABLE_POWERPOINT);
        }
    };

    private static final Map<String, Integer> sTypeToIconMap = new HashMap<String, Integer>() {
        {
            put("audio", DRAWABLE_AUDIO);
            put("font", DRAWABLE_FONT);
            put("image", DRAWABLE_IMAGE);
            put("text", DRAWABLE_TEXT);
            put("video", DRAWABLE_VIDEO);
        }
    };

    @DrawableRes
    public static int getDrawableFromType(@Nullable String mimeType) {
        if (mimeType == null) {
            return DRAWABLE_GENERIC;
        }
        Integer drawable = sMimeTypeToIconMap.get(mimeType);
        if (drawable != null) {
            return drawable;
        }
        String firstPart = mimeType.split("/")[0];
        drawable = sTypeToIconMap.get(firstPart);
        return drawable != null ? drawable : DRAWABLE_GENERIC;
    }

    public static boolean isImage(@DrawableRes int drawable) {
        return drawable == DRAWABLE_IMAGE;
    }

    public static boolean isAudio(@DrawableRes int drawable) {
        return drawable == DRAWABLE_AUDIO;
    }

    public static boolean isVideo(@DrawableRes int drawable) {
        return drawable == DRAWABLE_VIDEO;
    }

    public static boolean isMedia(@DrawableRes int drawable) {
        return drawable == DRAWABLE_AUDIO || drawable == DRAWABLE_VIDEO;
    }

    public static boolean isEbook(@DrawableRes int drawable) {
        return drawable == DRAWABLE_EBOOK;
    }

    public static boolean isFont(@DrawableRes int drawable) {
        return drawable == DRAWABLE_FONT;
    }

    public static boolean isPdf(@DrawableRes int drawable) {
        return drawable == DRAWABLE_PDF;
    }

    public static boolean isGeneric(@DrawableRes int drawable) {
        return drawable == DRAWABLE_GENERIC;
    }

    @Nullable
    public static Bitmap generateFontBitmap(@NonNull Path path) {
        String extension = path.getExtension();
        String text = extension != null ? extension.substring(0, Math.min(extension.length(), 4))
                .toUpperCase(Locale.ROOT) : "FONT";
        Pair<File, Boolean> file = getUsableFile(path);
        if (file == null) {
            return null;
        }
        try {
            Typeface typeface = Typeface.createFromFile(file.first);
            return UIUtils.generateBitmapFromText(text, typeface);
        } finally {
            if (file.second) {
                file.first.delete();
            }
        }
    }

    @Nullable
    public static Bitmap generatePdfBitmap(@NonNull Context context, @NonNull Uri uri) {
        PdfRenderer renderer;
        try {
            renderer = new PdfRenderer(FileUtils.getFdFromUri(context, uri, "r"));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        PdfRenderer.Page page;
        try {
            page = renderer.openPage(0);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return null;
        }
        int srcWidth = page.getWidth();
        int srcHeight = page.getHeight();
        if (srcWidth <= 0 || srcHeight <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return bitmap;
    }

    @Nullable
    public static Bitmap generateEpubCover(@NonNull Path path) {
        Pair<File, Boolean> file = getUsableFile(path);
        if (file == null) {
            return null;
        }
        try {
            return EpubCoverGenerator.generateFromFile(file.first);
        } finally {
            if (file.second) {
                file.first.delete();
            }
        }
    }

    @Nullable
    private static Pair<File, Boolean> getUsableFile(@NonNull Path path) {
        File f = path.getFile();
        if (f == null) {
            try {
                return new Pair<>(FileCache.getGlobalFileCache().getCachedFile(path), true);
            } catch (IOException ignore) {
                return null;
            }
        }
        f = new File(f.getPath());
        if (!f.canRead()) {
            try {
                return new Pair<>(FileCache.getGlobalFileCache().getCachedFile(path), true);
            } catch (IOException ignore) {
                return null;
            }
        }
        return new Pair<>(f, false);
    }
}
