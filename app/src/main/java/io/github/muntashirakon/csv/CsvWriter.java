// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Copyright 2020 Yong Mook Kim
// Copyright 2024 Muntashir Al-Islam
public class CsvWriter {
    private static final String COMMA = ",";
    private static final String DEFAULT_SEPARATOR = COMMA;
    private static final String DOUBLE_QUOTES = "\"";
    private static final String EMBEDDED_DOUBLE_QUOTES = "\"\"";
    private static final String NEW_LINE_UNIX = "\n";
    private static final String NEW_LINE_WINDOWS = "\r\n";

    private final Writer mWriter;
    private final String mSeparator;

    private boolean mInitialized = false;
    private int mFirstFieldCount = 0;
    private int mCurrentFieldCount = 0;

    public CsvWriter(@NonNull Writer writer) {
        this(writer, DEFAULT_SEPARATOR);
    }

    public CsvWriter(@NonNull Writer writer, @NonNull String separator) {
        mWriter = Objects.requireNonNull(writer);
        mSeparator = Objects.requireNonNull(separator);
    }

    public void addField(@Nullable String field) throws IOException {
        addField(field, false);
    }

    public void addField(@Nullable String field, boolean addQuotes) throws IOException {
        boolean previouslyInitialized = mInitialized;
        ++mCurrentFieldCount;
        checkFieldAvailable();
        if (mCurrentFieldCount > 1) {
            // There are other fields
            mWriter.write(mSeparator);
        } else if (previouslyInitialized) {
            // This is the first field since the last line
            mWriter.append(System.lineSeparator());
        }
        mWriter.append(getFormattedField(field, addQuotes));
    }

    public void addLine() {
        initIfNotAlready();
        checkFieldCountSame();
        mCurrentFieldCount = 0;
    }

    public void addLine(@NonNull String[] line) throws IOException {
        addLine(line, false);
    }

    /**
     * @param addQuotes Whether all fields are to be enclosed in double quotes
     */
    public void addLine(@NonNull String[] line, boolean addQuotes) throws IOException {
        boolean previouslyInitialized = mInitialized;
        mCurrentFieldCount = line.length;
        initIfNotAlready();
        checkFieldCountSame();
        mCurrentFieldCount = 0;
        if (previouslyInitialized) {
            // There were other lines
            mWriter.append(System.lineSeparator());
        }
        mWriter.append(getFormattedLine(line, addQuotes));
    }

    public void addLines(@NonNull Collection<String[]> lines) throws IOException {
        addLines(lines, false);
    }

    /**
     * @param addQuotes Whether all fields are to be enclosed in double quotes
     */
    public void addLines(@NonNull Collection<String[]> lines, boolean addQuotes) throws IOException {
        for (String[] line : lines) {
            addLine(line, addQuotes);
        }
    }

    @NonNull
    private String getFormattedLine(@NonNull String[] line, boolean addQuotes) {
        return Stream.of(line)
                .map(field -> getFormattedField(field, addQuotes))
                .collect(Collectors.joining(mSeparator));
    }

    @NonNull
    private String getFormattedField(@Nullable String field, boolean addQuotes) {
        if (field == null) {
            // For a null field, add null as string
            return addQuotes ? (DOUBLE_QUOTES + "null" + DOUBLE_QUOTES) : "null";
        }
        if (field.contains(COMMA)
                || field.contains(DOUBLE_QUOTES)
                || field.contains(NEW_LINE_UNIX)
                || field.contains(NEW_LINE_WINDOWS)
                || field.contains(mSeparator)) {

            // If the field contains double quotes, replace it with two double quotes \"\"
            String result = field.replace(DOUBLE_QUOTES, EMBEDDED_DOUBLE_QUOTES);

            // Enclose the field in double quotes
            return DOUBLE_QUOTES + result + DOUBLE_QUOTES;
        } else if (addQuotes) {
            // Add quotation even if not needed
            return DOUBLE_QUOTES + field + DOUBLE_QUOTES;
        } else return field;
    }

    private void checkFieldAvailable() {
        if (mInitialized && mCurrentFieldCount > mFirstFieldCount) {
            throw new IndexOutOfBoundsException("CSV fields don't match. Previously added "
                    + mFirstFieldCount + " fields and now " + mCurrentFieldCount + " fields");
        }
    }

    private void checkFieldCountSame() {
        if (mInitialized && mCurrentFieldCount != mFirstFieldCount) {
            throw new IndexOutOfBoundsException("CSV fields don't match. Previously added "
                    + mFirstFieldCount + " fields and now " + mCurrentFieldCount + " fields");
        }
    }

    private void initIfNotAlready() {
        if (!mInitialized) {
            mInitialized = true;
            mFirstFieldCount = mCurrentFieldCount;
        }
    }
}