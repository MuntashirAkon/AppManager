// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package org.apache.commons.compress.archivers.tar;

import android.os.RemoteException;
import android.system.ErrnoException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.EntryStreamOffsets;
import org.apache.commons.compress.archivers.zip.ZipEncoding;
import org.apache.commons.compress.utils.ArchiveUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.io.ExtendedFile;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.UidGidPair;

/**
 * This class represents an entry in a Tar archive. It consists
 * of the entry's header, as well as the entry's File. Entries
 * can be instantiated in one of three ways, depending on how
 * they are to be used.
 * <p>
 * TarEntries that are created from the header bytes read from
 * an archive are instantiated with the {@link TarArchiveEntry#TarArchiveEntry(byte[])}
 * constructor. These entries will be used when extracting from
 * or listing the contents of an archive. These entries have their
 * header filled in using the header bytes. They also set the File
 * to null, since they reference an archive entry not a file.
 * <p>
 * TarEntries that are created from Files that are to be written
 * into an archive are instantiated with the {@link TarArchiveEntry#TarArchiveEntry(File)}
 * constructor.
 * These entries have their header filled in using the File's information.
 * They also keep a reference to the File for convenience when writing entries.
 * <p>
 * Finally, TarEntries can be constructed from nothing but a name.
 * This allows the programmer to construct the entry by hand, for
 * instance when only an InputStream is available for writing to
 * the archive, and the header information is constructed from
 * other information. In this case the header fields are set to
 * defaults and the File is set to null.
 *
 * <p>
 * The C structure for a Tar Entry's header is:
 * <pre>
 * struct header {
 * char name[100];     // TarConstants.NAMELEN    - offset   0
 * char mode[8];       // TarConstants.MODELEN    - offset 100
 * char uid[8];        // TarConstants.UIDLEN     - offset 108
 * char gid[8];        // TarConstants.GIDLEN     - offset 116
 * char size[12];      // TarConstants.SIZELEN    - offset 124
 * char mtime[12];     // TarConstants.MODTIMELEN - offset 136
 * char chksum[8];     // TarConstants.CHKSUMLEN  - offset 148
 * char linkflag[1];   //                         - offset 156
 * char linkname[100]; // TarConstants.NAMELEN    - offset 157
 * The following fields are only present in new-style POSIX tar archives:
 * char magic[6];      // TarConstants.MAGICLEN   - offset 257
 * char version[2];    // TarConstants.VERSIONLEN - offset 263
 * char uname[32];     // TarConstants.UNAMELEN   - offset 265
 * char gname[32];     // TarConstants.GNAMELEN   - offset 297
 * char devmajor[8];   // TarConstants.DEVLEN     - offset 329
 * char devminor[8];   // TarConstants.DEVLEN     - offset 337
 * char prefix[155];   // TarConstants.PREFIXLEN  - offset 345
 * // Used if "name" field is not long enough to hold the path
 * char pad[12];       // NULs                    - offset 500
 * } header;
 * All unused bytes are set to null.
 * New-style GNU tar files are slightly different from the above.
 * For values of size larger than 077777777777L (11 7s)
 * or uid and gid larger than 07777777L (7 7s)
 * the sign bit of the first byte is set, and the rest of the
 * field is the binary representation of the number.
 * See TarUtils.parseOctalOrBinary.
 * </pre>
 *
 * <p>
 * The C structure for a old GNU Tar Entry's header is:
 * <pre>
 * struct oldgnu_header {
 * char unused_pad1[345]; // TarConstants.PAD1LEN_GNU       - offset 0
 * char atime[12];        // TarConstants.ATIMELEN_GNU      - offset 345
 * char ctime[12];        // TarConstants.CTIMELEN_GNU      - offset 357
 * char offset[12];       // TarConstants.OFFSETLEN_GNU     - offset 369
 * char longnames[4];     // TarConstants.LONGNAMESLEN_GNU  - offset 381
 * char unused_pad2;      // TarConstants.PAD2LEN_GNU       - offset 385
 * struct sparse sp[4];   // TarConstants.SPARSELEN_GNU     - offset 386
 * char isextended;       // TarConstants.ISEXTENDEDLEN_GNU - offset 482
 * char realsize[12];     // TarConstants.REALSIZELEN_GNU   - offset 483
 * char unused_pad[17];   // TarConstants.PAD3LEN_GNU       - offset 495
 * };
 * </pre>
 * Whereas, "struct sparse" is:
 * <pre>
 * struct sparse {
 * char offset[12];   // offset 0
 * char numbytes[12]; // offset 12
 * };
 * </pre>
 *
 * <p>
 * The C structure for a xstar (Jörg Schilling star) Tar Entry's header is:
 * <pre>
 * struct star_header {
 *  char name[100];		// offset   0
 *  char mode[8];		// offset 100
 *  char uid[8];		// offset 108
 *  char gid[8];		// offset 116
 *  char size[12];		// offset 124
 *  char mtime[12];		// offset 136
 *  char chksum[8];		// offset 148
 *  char typeflag;		// offset 156
 *  char linkname[100];		// offset 157
 *  char magic[6];		// offset 257
 *  char version[2];		// offset 263
 *  char uname[32];		// offset 265
 *  char gname[32];		// offset 297
 *  char devmajor[8];		// offset 329
 *  char devminor[8];		// offset 337
 *  char prefix[131];		// offset 345
 *  char atime[12];             // offset 476
 *  char ctime[12];             // offset 488
 *  char mfill[8];              // offset 500
 *  char xmagic[4];             // offset 508  "tar"
 * };
 * </pre>
 * <p>which is identical to new-style POSIX up to the first 130 bytes of the prefix.</p>
 *
 * @NotThreadSafe
 */
// Copyright 2008 Torsten Curdt
public class TarArchiveEntry implements ArchiveEntry, TarConstants, EntryStreamOffsets {
    private static final TarArchiveEntry[] EMPTY_TAR_ARCHIVE_ENTRY_ARRAY = new TarArchiveEntry[0];

    /**
     * Value used to indicate unknown mode, user/groupids, device numbers and modTime when parsing a file in lenient
     * mode an the archive contains illegal fields.
     *
     * @since 1.19
     */
    public static final long UNKNOWN = -1L;

    /**
     * The entry's name.
     */
    private String name = "";

    /**
     * Whether to allow leading slashes or drive names inside the name
     */
    private final boolean preserveAbsolutePath;

    /**
     * The entry's permission mode.
     */
    private int mode;

    /**
     * The entry's user id.
     */
    private long userId = 0;

    /**
     * The entry's group id.
     */
    private long groupId = 0;

    /**
     * The entry's size.
     */
    private long size = 0;

    /**
     * The entry's modification time.
     */
    private long modTime;

    /**
     * If the header checksum is reasonably correct.
     */
    private boolean checkSumOK;

    /**
     * The entry's link flag.
     */
    private byte linkFlag;

    /**
     * The entry's link name.
     */
    private String linkName = "";

    /**
     * The entry's magic tag.
     */
    private String magic = MAGIC_POSIX;
    /**
     * The version of the format
     */
    private String version = VERSION_POSIX;

    /**
     * The entry's user name.
     */
    private String userName;

    /**
     * The entry's group name.
     */
    private String groupName = "";

    /**
     * The entry's major device number.
     */
    private int devMajor = 0;

    /**
     * The entry's minor device number.
     */
    private int devMinor = 0;

    /**
     * The sparse headers in tar
     */
    private List<TarArchiveStructSparse> sparseHeaders;

    /**
     * If an extension sparse header follows.
     */
    private boolean isExtended;

    /**
     * The entry's real size in case of a sparse file.
     */
    private long realSize;

    /**
     * is this entry a GNU sparse entry using one of the PAX formats?
     */
    private boolean paxGNUSparse;

    /**
     * is this entry a GNU sparse entry using 1.X PAX formats?
     * the sparse headers of 1.x PAX Format is stored in file data block
     */
    private boolean paxGNU1XSparse = false;

    /**
     * is this entry a star sparse entry using the PAX header?
     */
    private boolean starSparse;

    /**
     * The entry's file reference
     */
    @Nullable
    private final File file;

    /**
     * The entry's file reference
     */
    @Nullable
    private final Path path;

    /**
     * Extra, user supplied pax headers
     */
    private final Map<String, String> extraPaxHeaders = new HashMap<>();

    /**
     * Maximum length of a user's name in the tar file
     */
    public static final int MAX_NAMELEN = 31;

    /**
     * Default permissions bits for directories
     */
    public static final int DEFAULT_DIR_MODE = 040755;

    /**
     * Default permissions bits for files
     */
    public static final int DEFAULT_FILE_MODE = 0100644;

    /**
     * Convert millis to seconds
     */
    public static final int MILLIS_PER_SECOND = 1000;

    private long dataOffset = EntryStreamOffsets.OFFSET_UNKNOWN;

    /**
     * Construct an empty entry and prepares the header values.
     */
    private TarArchiveEntry(final boolean preserveAbsolutePath) {
        String user = System.getProperty("user.name", "");

        if (user.length() > MAX_NAMELEN) {
            user = user.substring(0, MAX_NAMELEN);
        }

        this.userName = user;
        this.file = null;
        this.path = null;
        this.preserveAbsolutePath = preserveAbsolutePath;
    }

    /**
     * Construct an entry with only a name. This allows the programmer
     * to construct the entry's header "by hand". File is set to null.
     *
     * <p>The entry's name will be the value of the {@code name}
     * argument with all file separators replaced by forward slashes
     * and leading slashes as well as Windows drive letters stripped.</p>
     *
     * @param name the entry name
     */
    public TarArchiveEntry(final String name) {
        this(name, false);
    }

    /**
     * Construct an entry with only a name. This allows the programmer
     * to construct the entry's header "by hand". File is set to null.
     *
     * <p>The entry's name will be the value of the {@code name}
     * argument with all file separators replaced by forward slashes.
     * Leading slashes and Windows drive letters are stripped if
     * {@code preserveAbsolutePath} is {@code false}.</p>
     *
     * @param name                 the entry name
     * @param preserveAbsolutePath whether to allow leading slashes
     *                             or drive letters in the name.
     * @since 1.1
     */
    public TarArchiveEntry(String name, final boolean preserveAbsolutePath) {
        this(preserveAbsolutePath);

        name = normalizeFileName(name, preserveAbsolutePath);
        final boolean isDir = name.endsWith("/");

        this.name = name;
        this.mode = isDir ? DEFAULT_DIR_MODE : DEFAULT_FILE_MODE;
        this.linkFlag = isDir ? LF_DIR : LF_NORMAL;
        this.modTime = System.currentTimeMillis() / MILLIS_PER_SECOND;
        this.userName = "";
    }

    /**
     * Construct an entry with a name and a link flag.
     *
     * <p>The entry's name will be the value of the {@code name}
     * argument with all file separators replaced by forward slashes
     * and leading slashes as well as Windows drive letters
     * stripped.</p>
     *
     * @param name     the entry name
     * @param linkFlag the entry link flag.
     */
    public TarArchiveEntry(final String name, final byte linkFlag) {
        this(name, linkFlag, false);
    }

    /**
     * Construct an entry with a name and a link flag.
     *
     * <p>The entry's name will be the value of the {@code name}
     * argument with all file separators replaced by forward slashes.
     * Leading slashes and Windows drive letters are stripped if
     * {@code preserveAbsolutePath} is {@code false}.</p>
     *
     * @param name                 the entry name
     * @param linkFlag             the entry link flag.
     * @param preserveAbsolutePath whether to allow leading slashes
     *                             or drive letters in the name.
     * @since 1.5
     */
    public TarArchiveEntry(final String name, final byte linkFlag, final boolean preserveAbsolutePath) {
        this(name, preserveAbsolutePath);
        this.linkFlag = linkFlag;
        if (linkFlag == LF_GNUTYPE_LONGNAME) {
            magic = MAGIC_GNU;
            version = VERSION_GNU_SPACE;
        }
    }

    /**
     * Construct an entry for a file. File is set to file, and the
     * header is constructed from information from the file.
     * The name is set from the normalized file path.
     *
     * <p>The entry's name will be the value of the {@code file}'s
     * path with all file separators replaced by forward slashes and
     * leading slashes as well as Windows drive letters stripped. The
     * name will end in a slash if the {@code file} represents a
     * directory.</p>
     *
     * @param file The file that the entry represents.
     */
    public TarArchiveEntry(final File file) {
        this(file, file.getPath());
    }

    /**
     * Construct an entry for a file. File is set to file, and the
     * header is constructed from information from the file.
     *
     * <p>The entry's name will be the value of the {@code fileName}
     * argument with all file separators replaced by forward slashes
     * and leading slashes as well as Windows drive letters stripped.
     * The name will end in a slash if the {@code file} represents a
     * directory.</p>
     *
     * @param file     The file that the entry represents.
     * @param fileName the name to be used for the entry.
     */
    public TarArchiveEntry(final File file, final String fileName) {
        this(Paths.get(file), fileName);
    }

    /**
     * Construct an entry for a file. File is set to file, and the
     * header is constructed from information from the file.
     *
     * <p>The entry's name will be the value of the {@code fileName}
     * argument with all file separators replaced by forward slashes
     * and leading slashes as well as Windows drive letters stripped.
     * The name will end in a slash if the {@code file} represents a
     * directory.</p>
     *
     * @param file     The file that the entry represents.
     * @param fileName the name to be used for the entry.
     */
    public TarArchiveEntry(@NonNull final Path file, final String fileName) {
        final String normalizedName = normalizeFileName(fileName, false);
        this.path = file;
        this.file = null;
        try {
            readFileMode(file, normalizedName);
        } catch (final IOException | ErrnoException | RemoteException e) {
            if (!file.isDirectory()) {
                this.size = file.length();
            }
            this.modTime = file.lastModified() / MILLIS_PER_SECOND;
        }
        this.userName = "";
        preserveAbsolutePath = false;
    }

    private void readFileMode(@NonNull final Path file, final String normalizedName)
            throws IOException, ErrnoException, RemoteException {
        if (file.isDirectory()) {
            this.linkFlag = LF_DIR;
            final int nameLength = normalizedName.length();
            if (nameLength == 0 || normalizedName.charAt(nameLength - 1) != '/') {
                this.name = normalizedName + "/";
            } else {
                this.name = normalizedName;
            }
        } else {
            this.linkFlag = LF_NORMAL;
            this.name = normalizedName;
            this.size = file.length();
        }
        // Setup file attributes
        ExtendedFile f = file.getFile();
        if (f != null) {
            this.mode = f.getMode();
            UidGidPair p = f.getUidGid();
            this.userId = p.uid;
            this.groupId = p.gid;
        }
        this.modTime = file.lastModified() / MILLIS_PER_SECOND;
    }

    /**
     * Construct an entry from an archive's header bytes. File is set
     * to null.
     *
     * @param headerBuf The header bytes from a tar archive entry.
     * @throws IllegalArgumentException if any of the numeric fields have an invalid format
     */
    public TarArchiveEntry(final byte[] headerBuf) {
        this(false);
        parseTarHeader(headerBuf);
    }

    /**
     * Construct an entry from an archive's header bytes. File is set
     * to null.
     *
     * @param headerBuf The header bytes from a tar archive entry.
     * @param encoding  encoding to use for file names
     * @throws IllegalArgumentException if any of the numeric fields have an invalid format
     * @throws IOException              on error
     * @since 1.4
     */
    public TarArchiveEntry(final byte[] headerBuf, final ZipEncoding encoding)
            throws IOException {
        this(headerBuf, encoding, false);
    }

    /**
     * Construct an entry from an archive's header bytes. File is set
     * to null.
     *
     * @param headerBuf The header bytes from a tar archive entry.
     * @param encoding  encoding to use for file names
     * @param lenient   when set to true illegal values for group/userid, mode, device numbers and timestamp will be
     *                  ignored and the fields set to {@link #UNKNOWN}. When set to false such illegal fields cause an exception instead.
     * @throws IllegalArgumentException if any of the numeric fields have an invalid format
     * @throws IOException              on error
     * @since 1.19
     */
    public TarArchiveEntry(final byte[] headerBuf, final ZipEncoding encoding, final boolean lenient)
            throws IOException {
        this(false);
        parseTarHeader(headerBuf, encoding, false, lenient);
    }

    /**
     * Construct an entry from an archive's header bytes for random access tar. File is set to null.
     *
     * @param headerBuf  the header bytes from a tar archive entry.
     * @param encoding   encoding to use for file names.
     * @param lenient    when set to true illegal values for group/userid, mode, device numbers and timestamp will be
     *                   ignored and the fields set to {@link #UNKNOWN}. When set to false such illegal fields cause an exception instead.
     * @param dataOffset position of the entry data in the random access file.
     * @throws IllegalArgumentException if any of the numeric fields have an invalid format.
     * @throws IOException              on error.
     * @since 1.21
     */
    public TarArchiveEntry(final byte[] headerBuf, final ZipEncoding encoding, final boolean lenient,
                           final long dataOffset) throws IOException {
        this(headerBuf, encoding, lenient);
        setDataOffset(dataOffset);
    }

    /**
     * Determine if the two entries are equal. Equality is determined
     * by the header names being equal.
     *
     * @param it Entry to be checked for equality.
     * @return True if the entries are equal.
     */
    public boolean equals(final TarArchiveEntry it) {
        return it != null && getName().equals(it.getName());
    }

    /**
     * Determine if the two entries are equal. Equality is determined
     * by the header names being equal.
     *
     * @param it Entry to be checked for equality.
     * @return True if the entries are equal.
     */
    @Override
    public boolean equals(final Object it) {
        if (it == null || getClass() != it.getClass()) {
            return false;
        }
        return equals((TarArchiveEntry) it);
    }

    /**
     * Hashcodes are based on entry names.
     *
     * @return the entry hashcode
     */
    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    /**
     * Determine if the given entry is a descendant of this entry.
     * Descendancy is determined by the name of the descendant
     * starting with this entry's name.
     *
     * @param desc Entry to be checked as a descendent of this.
     * @return True if entry is a descendant of this.
     */
    public boolean isDescendent(final TarArchiveEntry desc) {
        return desc.getName().startsWith(getName());
    }

    /**
     * Get this entry's name.
     *
     * <p>This method returns the raw name as it is stored inside of the archive.</p>
     *
     * @return This entry's name.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Set this entry's name.
     *
     * @param name This entry's new name.
     */
    public void setName(final String name) {
        this.name = normalizeFileName(name, this.preserveAbsolutePath);
    }

    /**
     * Set the mode for this entry
     *
     * @param mode the mode for this entry
     */
    public void setMode(final int mode) {
        this.mode = mode;
    }

    /**
     * Get this entry's link name.
     *
     * @return This entry's link name.
     */
    public String getLinkName() {
        return linkName;
    }

    /**
     * Set this entry's link name.
     *
     * @param link the link name to use.
     *
     * @since 1.1
     */
    public void setLinkName(final String link) {
        this.linkName = link;
    }

    /**
     * Get this entry's user id. On Android, it's always less than {@link Integer#MAX_VALUE}.
     *
     * @return This entry's user id.
     */
    public int getUserId() {
        return (int) userId;
    }

    /**
     * Set this entry's user id.
     *
     * @param userId This entry's new user id.
     */
    public void setUserId(final int userId) {
        setUserId((long) userId);
    }

    /**
     * Get this entry's user id.
     *
     * @return This entry's user id.
     * @since 1.10
     */
    public long getLongUserId() {
        return userId;
    }

    /**
     * Set this entry's user id.
     *
     * @param userId This entry's new user id.
     * @since 1.10
     */
    public void setUserId(final long userId) {
        this.userId = userId;
    }

    /**
     * Get this entry's group id. On Android, it's always less than {@link Integer#MAX_VALUE}.
     *
     * @return This entry's group id.
     */
    public int getGroupId() {
        return (int) groupId;
    }

    /**
     * Set this entry's group id.
     *
     * @param groupId This entry's new group id.
     */
    public void setGroupId(final int groupId) {
        setGroupId((long) groupId);
    }

    /**
     * Get this entry's group id.
     *
     * @return This entry's group id.
     * @since 1.10
     */
    public long getLongGroupId() {
        return groupId;
    }

    /**
     * Set this entry's group id.
     *
     * @param groupId This entry's new group id.
     * @since 1.10
     */
    public void setGroupId(final long groupId) {
        this.groupId = groupId;
    }

    /**
     * Get this entry's user name.
     *
     * @return This entry's user name.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Set this entry's user name.
     *
     * @param userName This entry's new user name.
     */
    public void setUserName(final String userName) {
        this.userName = userName;
    }

    /**
     * Get this entry's group name.
     *
     * @return This entry's group name.
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Set this entry's group name.
     *
     * @param groupName This entry's new group name.
     */
    public void setGroupName(final String groupName) {
        this.groupName = groupName;
    }

    /**
     * Convenience method to set this entry's group and user ids.
     *
     * @param userId  This entry's new user id.
     * @param groupId This entry's new group id.
     */
    public void setIds(final int userId, final int groupId) {
        setUserId(userId);
        setGroupId(groupId);
    }

    /**
     * Convenience method to set this entry's group and user names.
     *
     * @param userName  This entry's new user name.
     * @param groupName This entry's new group name.
     */
    public void setNames(final String userName, final String groupName) {
        setUserName(userName);
        setGroupName(groupName);
    }

    /**
     * Set this entry's modification time. The parameter passed
     * to this method is in "Java time".
     *
     * @param time This entry's new modification time.
     */
    public void setModTime(final long time) {
        modTime = time / MILLIS_PER_SECOND;
    }

    /**
     * Set this entry's modification time.
     *
     * @param time This entry's new modification time.
     */
    public void setModTime(final Date time) {
        modTime = time.getTime() / MILLIS_PER_SECOND;
    }

    /**
     * Get this entry's modification time.
     *
     * @return This entry's modification time.
     */
    public Date getModTime() {
        return new Date(modTime * MILLIS_PER_SECOND);
    }

    @Override
    public Date getLastModifiedDate() {
        return getModTime();
    }

    /**
     * Get this entry's checksum status.
     *
     * @return if the header checksum is reasonably correct
     * @see TarUtils#verifyCheckSum(byte[])
     * @since 1.5
     */
    public boolean isCheckSumOK() {
        return checkSumOK;
    }

    /**
     * Get this entry's file.
     *
     * <p>This method is only useful for entries created from a {@code
     * File} or {@code Path} but not for entries read from an archive.</p>
     *
     * @return this entry's file or null if the entry was not created from a file.
     */
    @Nullable
    public File getFile() {
        return file;
    }

    /**
     * Get this entry's path.
     *
     * <p>This method is only useful for entries created from a {@link
     * File} or {@link Path} but not for entries read from an archive.</p>
     *
     * @return this entry's file or null if the entry was not created from a file.
     */
    @Nullable
    public Path getPath() {
        return path;
    }

    /**
     * Get this entry's mode.
     *
     * @return This entry's mode.
     */
    public int getMode() {
        return mode;
    }

    /**
     * Get this entry's file size.
     *
     * @return This entry's file size.
     */
    @Override
    public long getSize() {
        return size;
    }

    /**
     * Set this entry's sparse headers
     *
     * @param sparseHeaders The new sparse headers
     * @since 1.20
     */
    public void setSparseHeaders(final List<TarArchiveStructSparse> sparseHeaders) {
        this.sparseHeaders = sparseHeaders;
    }

    /**
     * Get this entry's sparse headers
     *
     * @return This entry's sparse headers
     * @since 1.20
     */
    public List<TarArchiveStructSparse> getSparseHeaders() {
        return sparseHeaders;
    }

    /**
     * Get if this entry is a sparse file with 1.X PAX Format or not
     *
     * @return True if this entry is a sparse file with 1.X PAX Format
     * @since 1.20
     */
    public boolean isPaxGNU1XSparse() {
        return paxGNU1XSparse;
    }

    /**
     * Set this entry's file size.
     *
     * @param size This entry's new file size.
     * @throws IllegalArgumentException if the size is &lt; 0.
     */
    public void setSize(final long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Size is out of range: " + size);
        }
        this.size = size;
    }

    /**
     * Get this entry's major device number.
     *
     * @return This entry's major device number.
     * @since 1.4
     */
    public int getDevMajor() {
        return devMajor;
    }

    /**
     * Set this entry's major device number.
     *
     * @param devNo This entry's major device number.
     * @throws IllegalArgumentException if the devNo is &lt; 0.
     * @since 1.4
     */
    public void setDevMajor(final int devNo) {
        if (devNo < 0) {
            throw new IllegalArgumentException("Major device number is out of " + "range: " + devNo);
        }
        this.devMajor = devNo;
    }

    /**
     * Get this entry's minor device number.
     *
     * @return This entry's minor device number.
     * @since 1.4
     */
    public int getDevMinor() {
        return devMinor;
    }

    /**
     * Set this entry's minor device number.
     *
     * @param devNo This entry's minor device number.
     * @throws IllegalArgumentException if the devNo is &lt; 0.
     * @since 1.4
     */
    public void setDevMinor(final int devNo) {
        if (devNo < 0) {
            throw new IllegalArgumentException("Minor device number is out of " + "range: " + devNo);
        }
        this.devMinor = devNo;
    }

    /**
     * Indicates in case of an oldgnu sparse file if an extension
     * sparse header follows.
     *
     * @return true if an extension oldgnu sparse header follows.
     */
    public boolean isExtended() {
        return isExtended;
    }

    /**
     * Get this entry's real file size in case of a sparse file.
     * <p>If the file is not a sparse file, return size instead of realSize.</p>
     *
     * @return This entry's real file size, if the file is not a sparse file, return size instead of realSize.
     */
    public long getRealSize() {
        if (!isSparse()) {
            return size;
        }
        return realSize;
    }

    /**
     * Indicate if this entry is a GNU sparse block.
     *
     * @return true if this is a sparse extension provided by GNU tar
     */
    public boolean isGNUSparse() {
        return isOldGNUSparse() || isPaxGNUSparse();
    }

    /**
     * Indicate if this entry is a GNU or star sparse block using the
     * oldgnu format.
     *
     * @return true if this is a sparse extension provided by GNU tar or star
     * @since 1.11
     */
    public boolean isOldGNUSparse() {
        return linkFlag == LF_GNUTYPE_SPARSE;
    }

    /**
     * Indicate if this entry is a GNU sparse block using one of the
     * PAX formats.
     *
     * @return true if this is a sparse extension provided by GNU tar
     * @since 1.11
     */
    public boolean isPaxGNUSparse() {
        return paxGNUSparse;
    }

    /**
     * Indicate if this entry is a star sparse block using PAX headers.
     *
     * @return true if this is a sparse extension provided by star
     * @since 1.11
     */
    public boolean isStarSparse() {
        return starSparse;
    }

    /**
     * Indicate if this entry is a GNU long linkname block
     *
     * @return true if this is a long name extension provided by GNU tar
     */
    public boolean isGNULongLinkEntry() {
        return linkFlag == LF_GNUTYPE_LONGLINK;
    }

    /**
     * Indicate if this entry is a GNU long name block
     *
     * @return true if this is a long name extension provided by GNU tar
     */
    public boolean isGNULongNameEntry() {
        return linkFlag == LF_GNUTYPE_LONGNAME;
    }

    /**
     * Check if this is a Pax header.
     *
     * @return {@code true} if this is a Pax header.
     * @since 1.1
     */
    public boolean isPaxHeader() {
        return linkFlag == LF_PAX_EXTENDED_HEADER_LC
                || linkFlag == LF_PAX_EXTENDED_HEADER_UC;
    }

    /**
     * Check if this is a Pax header.
     *
     * @return {@code true} if this is a Pax header.
     * @since 1.1
     */
    public boolean isGlobalPaxHeader() {
        return linkFlag == LF_PAX_GLOBAL_EXTENDED_HEADER;
    }

    /**
     * Return whether or not this entry represents a directory.
     *
     * @return True if this entry is a directory.
     */
    @Override
    public boolean isDirectory() {
        if (file != null) {
            return file.isDirectory();
        }

        if (path != null) {
            return path.isDirectory();
        }

        if (linkFlag == LF_DIR) {
            return true;
        }

        return !isPaxHeader() && !isGlobalPaxHeader() && getName().endsWith("/");
    }

    /**
     * Check if this is a "normal file"
     *
     * @return whether this is a "normal file"
     * @since 1.2
     */
    public boolean isFile() {
        if (file != null) {
            return file.isFile();
        }
        if (path != null) {
            return path.isFile();
        }
        if (linkFlag == LF_OLDNORM || linkFlag == LF_NORMAL) {
            return true;
        }
        return !getName().endsWith("/");
    }

    /**
     * Check if this is a symbolic link entry.
     *
     * @return whether this is a symbolic link
     * @since 1.2
     */
    public boolean isSymbolicLink() {
        return linkFlag == LF_SYMLINK;
    }

    /**
     * Check if this is a link entry.
     *
     * @return whether this is a link entry
     * @since 1.2
     */
    public boolean isLink() {
        return linkFlag == LF_LINK;
    }

    /**
     * Check if this is a character device entry.
     *
     * @return whether this is a character device
     * @since 1.2
     */
    public boolean isCharacterDevice() {
        return linkFlag == LF_CHR;
    }

    /**
     * Check if this is a block device entry.
     *
     * @return whether this is a block device
     * @since 1.2
     */
    public boolean isBlockDevice() {
        return linkFlag == LF_BLK;
    }

    /**
     * Check if this is a FIFO (pipe) entry.
     *
     * @return whether this is a FIFO entry
     * @since 1.2
     */
    public boolean isFIFO() {
        return linkFlag == LF_FIFO;
    }

    /**
     * Check whether this is a sparse entry.
     *
     * @return whether this is a sparse entry
     * @since 1.11
     */
    public boolean isSparse() {
        return isGNUSparse() || isStarSparse();
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.21
     */
    @Override
    public long getDataOffset() {
        return dataOffset;
    }

    /**
     * Set the offset of the data for the tar entry.
     *
     * @param dataOffset the position of the data in the tar.
     * @since 1.21
     */
    public void setDataOffset(final long dataOffset) {
        if (dataOffset < 0) {
            throw new IllegalArgumentException("The offset can not be smaller than 0");
        }
        this.dataOffset = dataOffset;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.21
     */
    @Override
    public boolean isStreamContiguous() {
        return true;
    }

    /**
     * get extra PAX Headers
     *
     * @return read-only map containing any extra PAX Headers
     * @since 1.15
     */
    public Map<String, String> getExtraPaxHeaders() {
        return Collections.unmodifiableMap(extraPaxHeaders);
    }

    /**
     * clear all extra PAX headers.
     *
     * @since 1.15
     */
    public void clearExtraPaxHeaders() {
        extraPaxHeaders.clear();
    }

    /**
     * add a PAX header to this entry. If the header corresponds to an existing field in the entry,
     * that field will be set; otherwise the header will be added to the extraPaxHeaders Map
     *
     * @param name  The full name of the header to set.
     * @param value value of header.
     * @since 1.15
     */
    public void addPaxHeader(final String name, final String value) {
        processPaxHeader(name, value);
    }

    /**
     * get named extra PAX header
     * @param name The full name of an extended PAX header to retrieve
     * @return The value of the header, if any.
     * @since 1.15
     */
    public String getExtraPaxHeader(final String name) {
        return extraPaxHeaders.get(name);
    }

    /**
     * Update the entry using a map of pax headers.
     * @param headers
     * @since 1.15
     */
    void updateEntryFromPaxHeaders(final Map<String, String> headers) {
        for (final Map.Entry<String, String> ent : headers.entrySet()) {
            final String key = ent.getKey();
            final String val = ent.getValue();
            processPaxHeader(key, val, headers);
        }
    }

    /**
     * process one pax header, using the entries extraPaxHeaders map as source for extra headers
     * used when handling entries for sparse files.
     *
     * @since 1.15
     */
    private void processPaxHeader(final String key, final String val) {
        processPaxHeader(key, val, extraPaxHeaders);
    }

    /**
     * Process one pax header, using the supplied map as source for extra headers to be used when handling
     * entries for sparse files
     *
     * @param key     the header name.
     * @param val     the header value.
     * @param headers map of headers used for dealing with sparse file.
     * @throws NumberFormatException if encountered errors when parsing the numbers
     * @since 1.15
     */
    private void processPaxHeader(final String key, final String val, final Map<String, String> headers) {
        /*
         * The following headers are defined for Pax.
         * atime, ctime, charset: cannot use these without changing TarArchiveEntry fields
         * mtime
         * comment
         * gid, gname
         * linkpath
         * size
         * uid,uname
         * SCHILY.devminor, SCHILY.devmajor: don't have setters/getters for those
         *
         * GNU sparse files use additional members, we use
         * GNU.sparse.size to detect the 0.0 and 0.1 versions and
         * GNU.sparse.realsize for 1.0.
         *
         * star files use additional members of which we use
         * SCHILY.filetype in order to detect star sparse files.
         *
         * If called from addExtraPaxHeader, these additional headers must be already present .
         */
        switch (key) {
            case "path":
                setName(val);
                break;
            case "linkpath":
                setLinkName(val);
                break;
            case "gid":
                setGroupId(Long.parseLong(val));
                break;
            case "gname":
                setGroupName(val);
                break;
            case "uid":
                setUserId(Long.parseLong(val));
                break;
            case "uname":
                setUserName(val);
                break;
            case "size":
                setSize(Long.parseLong(val));
                break;
            case "mtime":
                setModTime((long) (Double.parseDouble(val) * 1000));
                break;
            case "SCHILY.devminor":
                setDevMinor(Integer.parseInt(val));
                break;
            case "SCHILY.devmajor":
                setDevMajor(Integer.parseInt(val));
                break;
            case "GNU.sparse.size":
                fillGNUSparse0xData(headers);
                break;
            case "GNU.sparse.realsize":
                fillGNUSparse1xData(headers);
                break;
            case "SCHILY.filetype":
                if ("sparse".equals(val)) {
                    fillStarSparseData(headers);
                }
                break;
            default:
                extraPaxHeaders.put(key, val);
        }
    }


    /**
     * If this entry represents a file, and the file is a directory, return
     * an array of TarEntries for this entry's children.
     *
     * <p>This method is only useful for entries created from a {@code
     * File} or {@code Path} but not for entries read from an archive.</p>
     *
     * @return An array of TarEntry's for this entry's children.
     */
    public TarArchiveEntry[] getDirectoryEntries() {
        if ((file == null && path == null) || !isDirectory()) {
            return EMPTY_TAR_ARCHIVE_ENTRY_ARRAY;
        }
        if (file != null) {
            File[] dirStream = file.listFiles();
            if (dirStream == null) {
                return EMPTY_TAR_ARCHIVE_ENTRY_ARRAY;
            }
            final List<TarArchiveEntry> entries = new ArrayList<>();
            for (File f : dirStream) {
                entries.add(new TarArchiveEntry(f));
            }
            return entries.toArray(EMPTY_TAR_ARCHIVE_ENTRY_ARRAY);
        } else {  // path != null
            Path[] dirStream = path.listFiles();
            final List<TarArchiveEntry> entries = new ArrayList<>();
            for (Path f : dirStream) {
                entries.add(new TarArchiveEntry(f, this.name + File.separatorChar + f.getName()));
            }
            return entries.toArray(EMPTY_TAR_ARCHIVE_ENTRY_ARRAY);
        }
    }

    /**
     * Write an entry's header information to a header buffer.
     *
     * <p>This method does not use the star/GNU tar/BSD tar extensions.</p>
     *
     * @param outbuf The tar entry header buffer to fill in.
     */
    public void writeEntryHeader(final byte[] outbuf) {
        try {
            writeEntryHeader(outbuf, TarUtils.DEFAULT_ENCODING, false);
        } catch (final IOException ex) { // NOSONAR
            try {
                writeEntryHeader(outbuf, TarUtils.FALLBACK_ENCODING, false);
            } catch (final IOException ex2) {
                // impossible
                throw new RuntimeException(ex2); //NOSONAR
            }
        }
    }

    /**
     * Write an entry's header information to a header buffer.
     *
     * @param outbuf   The tar entry header buffer to fill in.
     * @param encoding encoding to use when writing the file name.
     * @param starMode whether to use the star/GNU tar/BSD tar
     *                 extension for numeric fields if their value doesn't fit in the
     *                 maximum size of standard tar archives
     * @throws IOException on error
     * @since 1.4
     */
    public void writeEntryHeader(final byte[] outbuf, final ZipEncoding encoding,
                                 final boolean starMode) throws IOException {
        int offset = 0;

        offset = TarUtils.formatNameBytes(name, outbuf, offset, NAMELEN,
                encoding);
        offset = writeEntryHeaderField(mode, outbuf, offset, MODELEN, starMode);
        offset = writeEntryHeaderField(userId, outbuf, offset, UIDLEN,
                starMode);
        offset = writeEntryHeaderField(groupId, outbuf, offset, GIDLEN,
                starMode);
        offset = writeEntryHeaderField(size, outbuf, offset, SIZELEN, starMode);
        offset = writeEntryHeaderField(modTime, outbuf, offset, MODTIMELEN,
                starMode);

        final int csOffset = offset;

        for (int c = 0; c < CHKSUMLEN; ++c) {
            outbuf[offset++] = (byte) ' ';
        }

        outbuf[offset++] = linkFlag;
        offset = TarUtils.formatNameBytes(linkName, outbuf, offset, NAMELEN,
                encoding);
        offset = TarUtils.formatNameBytes(magic, outbuf, offset, MAGICLEN);
        offset = TarUtils.formatNameBytes(version, outbuf, offset, VERSIONLEN);
        offset = TarUtils.formatNameBytes(userName, outbuf, offset, UNAMELEN,
                encoding);
        offset = TarUtils.formatNameBytes(groupName, outbuf, offset, GNAMELEN,
                encoding);
        offset = writeEntryHeaderField(devMajor, outbuf, offset, DEVLEN,
                starMode);
        offset = writeEntryHeaderField(devMinor, outbuf, offset, DEVLEN,
                starMode);

        while (offset < outbuf.length) {
            outbuf[offset++] = 0;
        }

        final long chk = TarUtils.computeCheckSum(outbuf);

        TarUtils.formatCheckSumOctalBytes(chk, outbuf, csOffset, CHKSUMLEN);
    }

    private int writeEntryHeaderField(final long value, final byte[] outbuf, final int offset,
                                      final int length, final boolean starMode) {
        if (!starMode && (value < 0
                || value >= 1L << 3 * (length - 1))) {
            // value doesn't fit into field when written as octal
            // number, will be written to PAX header or causes an
            // error
            return TarUtils.formatLongOctalBytes(0, outbuf, offset, length);
        }
        return TarUtils.formatLongOctalOrBinaryBytes(value, outbuf, offset,
                length);
    }

    /**
     * Parse an entry's header information from a header buffer.
     *
     * @param header The tar entry header buffer to get information from.
     * @throws IllegalArgumentException if any of the numeric fields have an invalid format
     */
    public void parseTarHeader(final byte[] header) {
        try {
            parseTarHeader(header, TarUtils.DEFAULT_ENCODING);
        } catch (final IOException ex) { // NOSONAR
            try {
                parseTarHeader(header, TarUtils.DEFAULT_ENCODING, true, false);
            } catch (final IOException ex2) {
                // not really possible
                throw new RuntimeException(ex2); //NOSONAR
            }
        }
    }

    /**
     * Parse an entry's header information from a header buffer.
     *
     * @param header   The tar entry header buffer to get information from.
     * @param encoding encoding to use for file names
     * @throws IllegalArgumentException if any of the numeric fields have an invalid format
     * @throws IOException              on error
     * @since 1.4
     */
    public void parseTarHeader(final byte[] header, final ZipEncoding encoding)
            throws IOException {
        parseTarHeader(header, encoding, false, false);
    }

    private void parseTarHeader(final byte[] header, final ZipEncoding encoding,
                                final boolean oldStyle, final boolean lenient)
            throws IOException {
        int offset = 0;

        name = oldStyle ? TarUtils.parseName(header, offset, NAMELEN)
                : TarUtils.parseName(header, offset, NAMELEN, encoding);
        offset += NAMELEN;
        mode = (int) parseOctalOrBinary(header, offset, MODELEN, lenient);
        offset += MODELEN;
        userId = (int) parseOctalOrBinary(header, offset, UIDLEN, lenient);
        offset += UIDLEN;
        groupId = (int) parseOctalOrBinary(header, offset, GIDLEN, lenient);
        offset += GIDLEN;
        size = TarUtils.parseOctalOrBinary(header, offset, SIZELEN);
        if (size < 0) {
            throw new IOException("broken archive, entry with negative size");
        }
        offset += SIZELEN;
        modTime = parseOctalOrBinary(header, offset, MODTIMELEN, lenient);
        offset += MODTIMELEN;
        checkSumOK = TarUtils.verifyCheckSum(header);
        offset += CHKSUMLEN;
        linkFlag = header[offset++];
        linkName = oldStyle ? TarUtils.parseName(header, offset, NAMELEN)
                : TarUtils.parseName(header, offset, NAMELEN, encoding);
        offset += NAMELEN;
        magic = TarUtils.parseName(header, offset, MAGICLEN);
        offset += MAGICLEN;
        version = TarUtils.parseName(header, offset, VERSIONLEN);
        offset += VERSIONLEN;
        userName = oldStyle ? TarUtils.parseName(header, offset, UNAMELEN)
                : TarUtils.parseName(header, offset, UNAMELEN, encoding);
        offset += UNAMELEN;
        groupName = oldStyle ? TarUtils.parseName(header, offset, GNAMELEN)
                : TarUtils.parseName(header, offset, GNAMELEN, encoding);
        offset += GNAMELEN;
        if (linkFlag == LF_CHR || linkFlag == LF_BLK) {
            devMajor = (int) parseOctalOrBinary(header, offset, DEVLEN, lenient);
            offset += DEVLEN;
            devMinor = (int) parseOctalOrBinary(header, offset, DEVLEN, lenient);
            offset += DEVLEN;
        } else {
            offset += 2 * DEVLEN;
        }

        final int type = evaluateType(header);
        switch (type) {
            case FORMAT_OLDGNU: {
                offset += ATIMELEN_GNU;
                offset += CTIMELEN_GNU;
                offset += OFFSETLEN_GNU;
                offset += LONGNAMESLEN_GNU;
                offset += PAD2LEN_GNU;
                sparseHeaders = new ArrayList<>();
                for (int i = 0; i < SPARSE_HEADERS_IN_OLDGNU_HEADER; i++) {
                    final TarArchiveStructSparse sparseHeader = TarUtils.parseSparse(header,
                            offset + i * (SPARSE_OFFSET_LEN + SPARSE_NUMBYTES_LEN));

                    // some sparse headers are empty, we need to skip these sparse headers
                    if (sparseHeader.getOffset() > 0 || sparseHeader.getNumbytes() > 0) {
                        sparseHeaders.add(sparseHeader);
                    }
                }
                offset += SPARSELEN_GNU;
                isExtended = TarUtils.parseBoolean(header, offset);
                offset += ISEXTENDEDLEN_GNU;
                realSize = TarUtils.parseOctal(header, offset, REALSIZELEN_GNU);
                offset += REALSIZELEN_GNU; // NOSONAR - assignment as documentation
                break;
            }
            case FORMAT_XSTAR: {
                final String xstarPrefix = oldStyle
                        ? TarUtils.parseName(header, offset, PREFIXLEN_XSTAR)
                        : TarUtils.parseName(header, offset, PREFIXLEN_XSTAR, encoding);
                if (!xstarPrefix.isEmpty()) {
                    name = xstarPrefix + "/" + name;
                }
                break;
            }
            case FORMAT_POSIX:
            default: {
                final String prefix = oldStyle
                        ? TarUtils.parseName(header, offset, PREFIXLEN)
                        : TarUtils.parseName(header, offset, PREFIXLEN, encoding);
                // SunOS tar -E does not add / to directory names, so fix
                // up to be consistent
                if (isDirectory() && !name.endsWith("/")) {
                    name = name + "/";
                }
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
            }
        }
    }

    private long parseOctalOrBinary(final byte[] header, final int offset, final int length, final boolean lenient) {
        if (lenient) {
            try {
                return TarUtils.parseOctalOrBinary(header, offset, length);
            } catch (final IllegalArgumentException ex) { //NOSONAR
                return UNKNOWN;
            }
        }
        return TarUtils.parseOctalOrBinary(header, offset, length);
    }

    /**
     * Strips Windows' drive letter as well as any leading slashes,
     * turns path separators into forward slahes.
     */
    private static String normalizeFileName(String fileName, final boolean preserveAbsolutePath) {
        fileName = fileName.replace(File.separatorChar, '/');

        // No absolute pathnames
        // Windows (and Posix?) paths can start with "\\NetworkDrive\",
        // so we loop on starting /'s.
        while (!preserveAbsolutePath && fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }
        return fileName;
    }

    /**
     * Evaluate an entry's header format from a header buffer.
     *
     * @param header The tar entry header buffer to evaluate the format for.
     * @return format type
     */
    private int evaluateType(final byte[] header) {
        if (ArchiveUtils.matchAsciiBuffer(MAGIC_GNU, header, MAGIC_OFFSET, MAGICLEN)) {
            return FORMAT_OLDGNU;
        }
        if (ArchiveUtils.matchAsciiBuffer(MAGIC_POSIX, header, MAGIC_OFFSET, MAGICLEN)) {
            if (ArchiveUtils.matchAsciiBuffer(MAGIC_XSTAR, header, XSTAR_MAGIC_OFFSET,
                    XSTAR_MAGIC_LEN)) {
                return FORMAT_XSTAR;
            }
            return FORMAT_POSIX;
        }
        return 0;
    }

    void fillGNUSparse0xData(final Map<String, String> headers) {
        paxGNUSparse = true;
        realSize = Integer.parseInt(headers.get("GNU.sparse.size"));
        if (headers.containsKey("GNU.sparse.name")) {
            // version 0.1
            name = headers.get("GNU.sparse.name");
        }
    }

    void fillGNUSparse1xData(final Map<String, String> headers) {
        paxGNUSparse = true;
        paxGNU1XSparse = true;
        realSize = Integer.parseInt(headers.get("GNU.sparse.realsize"));
        name = headers.get("GNU.sparse.name");
    }

    void fillStarSparseData(final Map<String, String> headers) {
        starSparse = true;
        if (headers.containsKey("SCHILY.realsize")) {
            realSize = Long.parseLong(headers.get("SCHILY.realsize"));
        }
    }
}
