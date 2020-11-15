/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.adb;

import com.tananaev.adblib.AdbStream;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Stack;

public class LineReader implements Closeable {
    private static final int CR = 13;
    private static final int LF = 10;
    private static final int EOF = -1;

    private final AdbStream adbStream;
    private String charsetName = "US-ASCII";

    public LineReader(AdbStream adbStream, String charsetName) {
        this.adbStream = adbStream;
        this.charsetName = charsetName;
    }

    public LineReader(AdbStream adbStream) {
        this.adbStream = adbStream;
    }

    private final ByteBuffer byteBuffer = ByteBuffer.allocate(8192);

    private final Stack<String> remainLines = new Stack<>();

    public String readLine() throws IOException, InterruptedException {
        while (!adbStream.isClosed()) {
            if (!remainLines.isEmpty()) {
                return remainLines.pop();
            }

            byte[] read = adbStream.read();

            if (read != null) {

                int startPos = 0;
                for (int i = 0; i < read.length; i++) {
                    if (read[i] == LF || read[i] == CR) {
                        //\n \r
                        int count = i - startPos;
                        if (count > 0) {
                            byteBuffer.put(read, startPos, count);
                            byteBuffer.flip();
                            remainLines.push(
                                    new String(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(),
                                            byteBuffer.remaining(), charsetName));
                            byteBuffer.clear();
                        }
                        startPos = i + 1;
                    }
                }
                int r = read.length - startPos;
                if (r > 0) {
                    byteBuffer.put(read, startPos, r);
                } else {
                    byteBuffer.clear();
                }
            }

            if (!remainLines.isEmpty()) {
                return remainLines.pop();
            }

        }
        return null;
    }

    @Override
    public void close() throws IOException {
        adbStream.close();
    }
}
