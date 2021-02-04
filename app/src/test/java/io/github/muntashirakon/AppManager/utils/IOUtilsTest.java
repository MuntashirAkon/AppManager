/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class IOUtilsTest {
    @Test
    public void getLastPathComponent() {
        assertEquals("", IOUtils.getLastPathComponent(""));
        assertEquals("", IOUtils.getLastPathComponent("/"));
        assertEquals("", IOUtils.getLastPathComponent("//"));
        assertEquals("", IOUtils.getLastPathComponent("///"));
        assertEquals("a", IOUtils.getLastPathComponent("a/"));
        assertEquals("a", IOUtils.getLastPathComponent("a//"));
        assertEquals("a", IOUtils.getLastPathComponent("a///"));
        assertEquals("c", IOUtils.getLastPathComponent("a/b/c"));
        assertEquals("c", IOUtils.getLastPathComponent("a/b//c"));
        assertEquals("c", IOUtils.getLastPathComponent("a/b///c"));
        assertEquals("c", IOUtils.getLastPathComponent("a/b/c/"));
        assertEquals("c", IOUtils.getLastPathComponent("a/b/c//"));
        assertEquals("c", IOUtils.getLastPathComponent("a/b/c///"));
        assertEquals(".", IOUtils.getLastPathComponent("a/b/c/."));
        assertEquals("..", IOUtils.getLastPathComponent("a/b/c/.."));
        assertEquals("..", IOUtils.getLastPathComponent("a/b/c/../"));
        assertEquals("..", IOUtils.getLastPathComponent("a/b/c/..//"));
        assertEquals("ewrjpoewiwfjfpwrejtp", IOUtils.getLastPathComponent("asdkjrejvncnmiet/eru43jffn/ewrjpoewiwfjfpwrejtp"));
        assertEquals("ewrjpoewiwfjfpwrejtp", IOUtils.getLastPathComponent("asdkjrejvncnmiet/eru43jffn/ewrjpoewiwfjfpwrejtp/"));
    }

    @Test
    public void trimExtension() {
        assertEquals("", IOUtils.trimExtension(""));
        assertEquals("/", IOUtils.trimExtension("/"));
        assertEquals("/.", IOUtils.trimExtension("/."));
        assertEquals(".ext", IOUtils.trimExtension(".ext"));
        assertEquals("/.ext", IOUtils.trimExtension("/.ext"));
        assertEquals("a/", IOUtils.trimExtension("a/"));
        assertEquals("a/b/.", IOUtils.trimExtension("a/b/."));
        assertEquals("a/b/..", IOUtils.trimExtension("a/b/.."));
        assertEquals("a/b/", IOUtils.trimExtension("a/b/"));
        assertEquals("a/b/", IOUtils.trimExtension("a/b/"));
        assertEquals("a/b", IOUtils.trimExtension("a/b.c"));
        assertEquals("a/b", IOUtils.trimExtension("a/b.c/"));
        assertEquals("a/b.c/d", IOUtils.trimExtension("a/b.c/d"));
        assertEquals("a/b.c", IOUtils.trimExtension("a/b.c.d"));
        assertEquals("a/b.c.d", IOUtils.trimExtension("a/b.c.d.e"));
        assertEquals("asdkjrejvncnmiet/eru43jffn/ewrjpoewiwfjfpwrejtp",
                IOUtils.trimExtension("asdkjrejvncnmiet/eru43jffn/ewrjpoewiwfjfpwrejtp.ext"));
    }

    @Test
    public void getExtension() {
        assertEquals("", IOUtils.getExtension(""));
        assertEquals("", IOUtils.getExtension("/"));
        assertEquals("", IOUtils.getExtension("/."));
        assertEquals("ext", IOUtils.getExtension(".ext"));
        assertEquals("ext", IOUtils.getExtension("/.ext"));
        assertEquals("", IOUtils.getExtension("a/"));
        assertEquals("", IOUtils.getExtension("a/b/."));
        assertEquals("", IOUtils.getExtension("a/b/.."));
        assertEquals("", IOUtils.getExtension("a/b/"));
        assertEquals("", IOUtils.getExtension("a/b/"));
        assertEquals("c", IOUtils.getExtension("a/b.c"));
        assertEquals("c", IOUtils.getExtension("a/b.c/"));
        assertEquals("", IOUtils.getExtension("a/b.c/d"));
        assertEquals("d", IOUtils.getExtension("a/b.c.d"));
        assertEquals("e", IOUtils.getExtension("a/b.c.d.e"));
        assertEquals("ext", IOUtils.getExtension("asdkjrejvncnmiet/eru43jffn/ewrjpoewiwfjfpwrejtp.ext"));
    }
}