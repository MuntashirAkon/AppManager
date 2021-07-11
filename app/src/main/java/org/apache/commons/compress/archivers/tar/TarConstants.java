// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.archivers.tar;

/**
 * This interface contains all the definitions used in the package.
 *
 * For tar formats (FORMAT_OLDGNU, FORMAT_POSIX, etc.) see GNU tar
 * <I>tar.h</I> type <I>enum archive_format</I>
 */
// Copyright 2008 Torsten Curdt
public interface TarConstants {

    /** Default record size */
    int DEFAULT_RCDSIZE = 512;

    /** Default block size */
    int DEFAULT_BLKSIZE = DEFAULT_RCDSIZE * 20;

    /**
     * GNU format as per before tar 1.12.
     */
    int    FORMAT_OLDGNU = 2;

    /**
     * Pure Posix format.
     */
    int    FORMAT_POSIX = 3;

    /**
     * xstar format used by Jörg Schilling's star.
     */
    int    FORMAT_XSTAR = 4;

    /**
     * The length of the name field in a header buffer.
     */
    int    NAMELEN = 100;

    /**
     * The length of the mode field in a header buffer.
     */
    int    MODELEN = 8;

    /**
     * The length of the user id field in a header buffer.
     */
    int    UIDLEN = 8;

    /**
     * The length of the group id field in a header buffer.
     */
    int    GIDLEN = 8;

    /**
     * The maximum value of gid/uid in a tar archive which can
     * be expressed in octal char notation (that's 7 sevens, octal).
     */
    long    MAXID = 07777777L;

    /**
     * The length of the checksum field in a header buffer.
     */
    int    CHKSUMLEN = 8;

    /**
     * Offset of the checksum field within header record.
     * @since 1.5
     */
    int    CHKSUM_OFFSET = 148;

    /**
     * The length of the size field in a header buffer.
     * Includes the trailing space or NUL.
     */
    int    SIZELEN = 12;

    /**
     * The maximum size of a file in a tar archive
     * which can be expressed in octal char notation (that's 11 sevens, octal).
     */
    long   MAXSIZE = 077777777777L;

    /** Offset of start of magic field within header record */
    int    MAGIC_OFFSET = 257;
    /**
     * The length of the magic field in a header buffer.
     */
    int    MAGICLEN = 6;

    /** Offset of start of magic field within header record */
    int    VERSION_OFFSET = 263;
    /**
     * Previously this was regarded as part of "magic" field, but it is separate.
     */
    int    VERSIONLEN = 2;

    /**
     * The length of the modification time field in a header buffer.
     */
    int    MODTIMELEN = 12;

    /**
     * The length of the user name field in a header buffer.
     */
    int    UNAMELEN = 32;

    /**
     * The length of the group name field in a header buffer.
     */
    int    GNAMELEN = 32;

    /**
     * The length of each of the device fields (major and minor) in a header buffer.
     */
    int    DEVLEN = 8;

    /**
     * Length of the prefix field.
     *
     */
    int    PREFIXLEN = 155;

    /**
     * The length of the access time field in an old GNU header buffer.
     *
     */
    int    ATIMELEN_GNU = 12;

    /**
     * The length of the created time field in an old GNU header buffer.
     *
     */
    int    CTIMELEN_GNU = 12;

    /**
     * The length of the multivolume start offset field in an old GNU header buffer.
     *
     */
    int    OFFSETLEN_GNU = 12;

    /**
     * The length of the long names field in an old GNU header buffer.
     *
     */
    int    LONGNAMESLEN_GNU = 4;

    /**
     * The length of the padding field in an old GNU header buffer.
     *
     */
    int    PAD2LEN_GNU = 1;

    /**
     * The sum of the length of all sparse headers in an old GNU header buffer.
     *
     */
    int    SPARSELEN_GNU = 96;

    /**
     * The length of the is extension field in an old GNU header buffer.
     *
     */
    int    ISEXTENDEDLEN_GNU = 1;

    /**
     * The length of the real size field in an old GNU header buffer.
     *
     */
    int    REALSIZELEN_GNU = 12;

    /**
     * The length of offset in struct sparse
     * @since 1.20
     */
    int    SPARSE_OFFSET_LEN = 12;

    /**
     * The length of numbytes in struct sparse
     * @since 1.20
     */
    int    SPARSE_NUMBYTES_LEN = 12;

    /**
     *  The number of sparse headers in an old GNU header
     * @since 1.20
     */
    int    SPARSE_HEADERS_IN_OLDGNU_HEADER = 4;

    /**
     *  The number of sparse headers in an extension header
     * @since 1.20
     */
    int    SPARSE_HEADERS_IN_EXTENSION_HEADER = 21;

    /**
     * The sum of the length of all sparse headers in a sparse header buffer.
     *
     */
    int    SPARSELEN_GNU_SPARSE = 504;

    /**
     * The length of the is extension field in a sparse header buffer.
     *
     */
    int    ISEXTENDEDLEN_GNU_SPARSE = 1;

    /**
     * LF_ constants represent the "link flag" of an entry, or more commonly,
     * the "entry type". This is the "old way" of indicating a normal file.
     */
    byte   LF_OLDNORM = 0;

    /**
     * Normal file type.
     */
    byte   LF_NORMAL = (byte) '0';

    /**
     * Link file type.
     */
    byte   LF_LINK = (byte) '1';

    /**
     * Symbolic link file type.
     */
    byte   LF_SYMLINK = (byte) '2';

    /**
     * Character device file type.
     */
    byte   LF_CHR = (byte) '3';

    /**
     * Block device file type.
     */
    byte   LF_BLK = (byte) '4';

    /**
     * Directory file type.
     */
    byte   LF_DIR = (byte) '5';

    /**
     * FIFO (pipe) file type.
     */
    byte   LF_FIFO = (byte) '6';

    /**
     * Contiguous file type.
     */
    byte   LF_CONTIG = (byte) '7';

    /**
     * Identifies the *next* file on the tape as having a long linkname.
     */
    byte LF_GNUTYPE_LONGLINK = (byte) 'K';

    /**
     * Identifies the *next* file on the tape as having a long name.
     */
    byte LF_GNUTYPE_LONGNAME = (byte) 'L';

    /**
     * Sparse file type.
     * @since 1.1.1
     */
    byte LF_GNUTYPE_SPARSE = (byte) 'S';

    // See "http://www.opengroup.org/onlinepubs/009695399/utilities/pax.html#tag_04_100_13_02"

    /**
     * Identifies the entry as a Pax extended header.
     * @since 1.1
     */
    byte LF_PAX_EXTENDED_HEADER_LC = (byte) 'x';

    /**
     * Identifies the entry as a Pax extended header (SunOS tar -E).
     *
     * @since 1.1
     */
    byte LF_PAX_EXTENDED_HEADER_UC = (byte) 'X';

    /**
     * Identifies the entry as a Pax global extended header.
     *
     * @since 1.1
     */
    byte LF_PAX_GLOBAL_EXTENDED_HEADER = (byte) 'g';

    /**
     * The magic tag representing a POSIX tar archive.
     */
    String MAGIC_POSIX = "ustar\0";
    String VERSION_POSIX = "00";

    /**
     * The magic tag representing a GNU tar archive.
     */
    String MAGIC_GNU = "ustar ";
    // Appear to be two possible GNU versions
    String VERSION_GNU_SPACE = " \0";
    String VERSION_GNU_ZERO  = "0\0";

    /**
     * The magic tag representing an Ant tar archive.
     *
     * @since 1.1
     */
    String MAGIC_ANT = "ustar\0";

    /**
     * The "version" representing an Ant tar archive.
     *
     * @since 1.1
     */
    // Does not appear to have a version, however Ant does write 8 bytes,
    // so assume the version is 2 nulls
    String VERSION_ANT = "\0\0";

    /**
     * The name of the GNU tar entry which contains a long name.
     */
    String GNU_LONGLINK = "././@LongLink"; // TODO rename as LONGLINK_GNU ?

    /**
     * The magix string used in the last four bytes of the header to
     * identify the xstar format.
     * @since 1.11
     */
    String MAGIC_XSTAR = "tar\0";

    /**
     * Offset inside the header for the xstar magic bytes.
     * @since 1.11
     */
    int XSTAR_MAGIC_OFFSET = 508;

    /**
     * Length of the XSTAR magic.
     * @since 1.11
     */
    int XSTAR_MAGIC_LEN = 4;

    /**
     * Length of the prefix field in xstar archives.
     *
     * @since 1.11
     */
    int PREFIXLEN_XSTAR = 131;

    /**
     * The length of the access time field in a xstar header buffer.
     *
     * @since 1.11
     */
    int ATIMELEN_XSTAR = 12;

    /**
     * The length of the created time field in a xstar header buffer.
     *
     * @since 1.11
     */
    int CTIMELEN_XSTAR = 12;
}