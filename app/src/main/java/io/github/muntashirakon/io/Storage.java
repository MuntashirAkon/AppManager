// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.documentfile.provider.ProxyDocumentFile;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Provide an interface to {@link File} and {@link DocumentFile} with basic functionalities.
 */
public class Storage {
    public static final String TAG = Storage.class.getSimpleName();

    private final Context context;
    @NonNull
    private final DocumentFile documentFile;

    public Storage(@NonNull Context context, @NonNull File fileLocation) {
        this.context = context;
        this.documentFile = new ProxyDocumentFile(fileLocation);
    }

    public Storage(@NonNull Context context, @NonNull DocumentFile documentFile) {
        this.context = context;
        this.documentFile = documentFile;
    }

    public Storage(@NonNull Context context, @NonNull Uri uri) throws FileNotFoundException {
        this.context = context;
        DocumentFile documentFile;
        switch (uri.getScheme()) {
            case ContentResolver.SCHEME_CONTENT:
                documentFile = DocumentFile.fromTreeUri(context, uri);
                break;
            case ContentResolver.SCHEME_FILE:
                documentFile = new ProxyDocumentFile(new ProxyFile(uri.getPath()));
                break;
            default:
                throw new IllegalArgumentException("Unsupported uri " + uri);
        }
        if (documentFile == null) throw new FileNotFoundException("Uri " + uri + " does not belong to any document.");
        this.documentFile = documentFile;
    }

    public Storage(@NonNull Context context, @NonNull UriPermission uriPermission) throws FileNotFoundException {
        this.context = context;
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, uriPermission.getUri());
        if (documentFile == null)
            throw new FileNotFoundException("Uri " + uriPermission + " does not belong to any document.");
        this.documentFile = documentFile;
    }

    public String getName() {
        return documentFile.getName();
    }

    public Uri getUri() {
        return documentFile.getUri();
    }

    public String getType() {
        return documentFile.getType();
    }

    @CheckResult
    public long length() {
        return documentFile.length();
    }

    @CheckResult
    public boolean createNewFile() {
        try (OutputStream ignore = context.getContentResolver().openOutputStream(documentFile.getUri())) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @CheckResult
    public boolean delete() {
        return documentFile.delete();
    }

    @Nullable
    public Storage getParentFile() {
        DocumentFile file = documentFile.getParentFile();
        return file == null ? null : new Storage(context, file);
    }

    @CheckResult
    public boolean exists() {
        return documentFile.exists();
    }

    @CheckResult
    public boolean isDirectory() {
        return documentFile.isDirectory();
    }

    @CheckResult
    public boolean isFile() {
        return documentFile.isFile();
    }

    @CheckResult
    public boolean isVirtual() {
        return documentFile.isVirtual();
    }

    public boolean mkdir() {
        return true;
    }

    public boolean mkdirs() {
        return true;
    }

    public boolean renameTo(@NonNull String name) {
        // TODO: 9/2/21 Unreliable
        return documentFile.renameTo(name);
    }

    public long lastModified() {
        return documentFile.lastModified();
    }

    @NonNull
    public Uri[] list() {
        DocumentFile[] children = listDocumentFiles();
        Uri[] files = new Uri[children.length];
        for (int i = 0; i < children.length; ++i) {
            files[i] = children[i].getUri();
        }
        return files;
    }

    @NonNull
    public Uri[] list(@Nullable FilenameFilter filter) {
        Uri[] names = list();
        if (filter == null) {
            return names;
        }
        List<Uri> v = new ArrayList<>();
        File f = new File(documentFile.getUri().getPath());
        for (Uri name : names) {
            if (filter.accept(f, name.getLastPathSegment())) {
                v.add(name);
            }
        }
        return v.toArray(new Uri[0]);
    }

    @NonNull
    public Storage[] listFiles() {
        DocumentFile[] ss = listDocumentFiles();
        int n = ss.length;
        Storage[] fs = new Storage[n];
        for (int i = 0; i < n; i++) {
            fs[i] = new Storage(context, ss[i]);
        }
        return fs;
    }

    @NonNull
    public Storage[] listFiles(@Nullable FileFilter filter) {
        DocumentFile[] ss = listDocumentFiles();
        ArrayList<Storage> files = new ArrayList<>();
        for (DocumentFile s : ss) {
            if ((filter == null) || filter.accept(new File(s.getUri().getPath())))
                files.add(new Storage(context, s));
        }
        return files.toArray(new Storage[0]);
    }

    @NonNull
    public Storage[] listFiles(@Nullable FilenameFilter filter) {
        DocumentFile[] ss = listDocumentFiles();
        ArrayList<Storage> files = new ArrayList<>();
        File f = new File(documentFile.getUri().getPath());
        for (DocumentFile s : ss) {
            if ((filter == null) || filter.accept(f, s.getName()))
                files.add(new Storage(context, s));
        }
        return files.toArray(new Storage[0]);
    }

    public boolean canRead() {
        return documentFile.canRead();
    }

    public boolean canWrite() {
        return documentFile.canWrite();
    }

    @Nullable
    public ParcelFileDescriptor openFileDescriptor(@NonNull String mode) throws FileNotFoundException {
        // TODO: 4/7/21 Support for proxy files
        return context.getContentResolver().openFileDescriptor(documentFile.getUri(), mode);
    }

    @Nullable
    public OutputStream openOutputStream(@NonNull String mode) throws FileNotFoundException {
        // TODO: 4/7/21 Support for proxy files
        return context.getContentResolver().openOutputStream(documentFile.getUri(), mode);
    }

    @Nullable
    public InputStream openInputStream() throws FileNotFoundException {
        // TODO: 4/7/21 Support for proxy files
        return context.getContentResolver().openInputStream(documentFile.getUri());
    }

    @NonNull
    private DocumentFile[] listDocumentFiles() {
        return documentFile.listFiles();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Storage)) return false;
        Storage storage = (Storage) o;
        return documentFile.getUri().equals(storage.documentFile.getUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentFile.getUri());
    }
}
