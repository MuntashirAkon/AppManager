// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.csv;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

// Copyright 2020 Yong Mook Kim
// Copyright 2024 Muntashir Al-Islam
public class CsvWriterTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testCsvLineDefault() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            String[] record = {"1", "apple", "10", "9.99"};
            String expected = "1,apple,10,9.99";
            new CsvWriter(writer).addLine(record);
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvLineQuoted() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            String[] record = {"1", "apple", "10", "9.99"};
            String expected = "\"1\",\"apple\",\"10\",\"9.99\"";
            new CsvWriter(writer).addLine(record, true);
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvLineContainsEmptyValue() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            String[] record = {"1", "", "10", ""};
            String expected = "1,,10,";
            new CsvWriter(writer).addLine(record);
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvEmptyLine() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            String expected = "";
            new CsvWriter(writer).addLine();
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvLineWithCustomSeparator() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            String[] record = {"1", "apple;orange", "10", "9.99"};
            String expected = "1;\"apple;orange\";10;9.99";
            new CsvWriter(writer, ";").addLine(record);
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvLineContainsComma() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            String[] record = {"1", "apple,orange", "10", "9.99"};
            String expected = "1,\"apple,orange\",10,9.99";
            new CsvWriter(writer).addLine(record);
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvLineContainsDoubleQuotes() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            String[] record = {"1", "12\"apple", "10", "9.99"};
            String expected = "1,\"12\"\"apple\",10,9.99";
            new CsvWriter(writer).addLine(record);
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvLineContainsNewline() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            String[] record = {"1", "promotion!\napple", "10", "9.99"};
            String expected = "1,\"promotion!\napple\",10,9.99";
            new CsvWriter(writer).addLine(record);
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvLineDefaultTwoLines() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            List<String[]> records = new ArrayList<String[]>() {{
                add(new String[]{"1", "apple", "10", "9.99"});
                add(new String[]{"2", "orange", "5", "4.99"});
            }};
            String expected = "1,apple,10,9.99\n2,orange,5,4.99";
            new CsvWriter(writer).addLines(records);
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvLineTwoLinesDifferentSizeThrowsException() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            List<String[]> records = new ArrayList<String[]>() {{
                add(new String[]{"1", "apple", "10", "9.99"});
                add(new String[]{"2", "orange", "5"});
            }};
            assertThrows(IndexOutOfBoundsException.class, () -> new CsvWriter(writer).addLines(records));
            records.remove(1);
            records.add(new String[]{"2", "orange", "5", "4.99", "rotten"});
            assertThrows(IndexOutOfBoundsException.class, () -> new CsvWriter(writer).addLines(records));
        }
    }

    @Test
    public void testCsvLineDefaultViaField() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            String expected = "1,apple,10,9.99";
            CsvWriter csvWriter = new CsvWriter(writer);
            csvWriter.addField("1");
            csvWriter.addField("apple");
            csvWriter.addField("10");
            csvWriter.addField("9.99");
            csvWriter.addLine();
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvLineDefaultTwoLinesViaField() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            String expected = "1,apple,10,9.99\n2,orange,5,4.99";
            CsvWriter csvWriter = new CsvWriter(writer);
            csvWriter.addField("1");
            csvWriter.addField("apple");
            csvWriter.addField("10");
            csvWriter.addField("9.99");
            csvWriter.addLine();
            csvWriter.addField("2");
            csvWriter.addField("orange");
            csvWriter.addField("5");
            csvWriter.addField("4.99");
            csvWriter.addLine();
            String result = writer.toString();
            assertEquals(expected, result);
        }
    }

    @Test
    public void testCsvLineDefaultTwoLinesDifferentSizeThrowsExceptionViaField() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            CsvWriter csvWriter = new CsvWriter(writer);
            csvWriter.addField("1");
            csvWriter.addField("apple");
            csvWriter.addField("10");
            csvWriter.addField("9.99");
            csvWriter.addLine();
            csvWriter.addField("2");
            csvWriter.addField("orange");
            csvWriter.addField("5");
            assertThrows(IndexOutOfBoundsException.class, csvWriter::addLine);
            csvWriter.addField("4.99");
            assertThrows(IndexOutOfBoundsException.class, () -> csvWriter.addField("rotten"));
        }
    }
}