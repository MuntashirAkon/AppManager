// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FileUtilsTest {
    @Test
    public void getLastPathComponent() {
        assertEquals("", FileUtils.getLastPathComponent(""));
        assertEquals("", FileUtils.getLastPathComponent("/"));
        assertEquals("", FileUtils.getLastPathComponent("//"));
        assertEquals("", FileUtils.getLastPathComponent("///"));
        assertEquals("a", FileUtils.getLastPathComponent("a/"));
        assertEquals("a", FileUtils.getLastPathComponent("a//"));
        assertEquals("a", FileUtils.getLastPathComponent("a///"));
        assertEquals("c", FileUtils.getLastPathComponent("a/b/c"));
        assertEquals("c", FileUtils.getLastPathComponent("a/b//c"));
        assertEquals("c", FileUtils.getLastPathComponent("a/b///c"));
        assertEquals("c", FileUtils.getLastPathComponent("a/b/c/"));
        assertEquals("c", FileUtils.getLastPathComponent("a/b/c//"));
        assertEquals("c", FileUtils.getLastPathComponent("a/b/c///"));
        assertEquals(".", FileUtils.getLastPathComponent("a/b/c/."));
        assertEquals("..", FileUtils.getLastPathComponent("a/b/c/.."));
        assertEquals("..", FileUtils.getLastPathComponent("a/b/c/../"));
        assertEquals("..", FileUtils.getLastPathComponent("a/b/c/..//"));
        assertEquals("ewrjpoewiwfjfpwrejtp", FileUtils.getLastPathComponent("asdkjrejvncnmiet/eru43jffn/ewrjpoewiwfjfpwrejtp"));
        assertEquals("ewrjpoewiwfjfpwrejtp", FileUtils.getLastPathComponent("asdkjrejvncnmiet/eru43jffn/ewrjpoewiwfjfpwrejtp/"));
    }

    @Test
    public void trimExtension() {
        assertEquals("", FileUtils.trimExtension(""));
        assertEquals("/", FileUtils.trimExtension("/"));
        assertEquals("/.", FileUtils.trimExtension("/."));
        assertEquals(".ext", FileUtils.trimExtension(".ext"));
        assertEquals("/.ext", FileUtils.trimExtension("/.ext"));
        assertEquals("a/", FileUtils.trimExtension("a/"));
        assertEquals("a/b/.", FileUtils.trimExtension("a/b/."));
        assertEquals("a/b/..", FileUtils.trimExtension("a/b/.."));
        assertEquals("a/b/", FileUtils.trimExtension("a/b/"));
        assertEquals("a/b/", FileUtils.trimExtension("a/b/"));
        assertEquals("a/b", FileUtils.trimExtension("a/b.c"));
        assertEquals("a/b", FileUtils.trimExtension("a/b.c/"));
        assertEquals("a/b.c/d", FileUtils.trimExtension("a/b.c/d"));
        assertEquals("a/b.c", FileUtils.trimExtension("a/b.c.d"));
        assertEquals("a/b.c.d", FileUtils.trimExtension("a/b.c.d.e"));
        assertEquals("asdkjrejvncnmiet/eru43jffn/ewrjpoewiwfjfpwrejtp",
                FileUtils.trimExtension("asdkjrejvncnmiet/eru43jffn/ewrjpoewiwfjfpwrejtp.ext"));
    }

    @Test
    public void getExtension() {
        assertEquals("", FileUtils.getExtension(""));
        assertEquals("", FileUtils.getExtension("/"));
        assertEquals("", FileUtils.getExtension("/."));
        assertEquals("ext", FileUtils.getExtension(".ext"));
        assertEquals("ext", FileUtils.getExtension("/.ext"));
        assertEquals("", FileUtils.getExtension("a/"));
        assertEquals("", FileUtils.getExtension("a/b/."));
        assertEquals("", FileUtils.getExtension("a/b/.."));
        assertEquals("", FileUtils.getExtension("a/b/"));
        assertEquals("", FileUtils.getExtension("a/b/"));
        assertEquals("c", FileUtils.getExtension("a/b.c"));
        assertEquals("c", FileUtils.getExtension("a/b.c/"));
        assertEquals("", FileUtils.getExtension("a/b.c/d"));
        assertEquals("d", FileUtils.getExtension("a/b.c.d"));
        assertEquals("e", FileUtils.getExtension("a/b.c.d.e"));
        assertEquals("ext", FileUtils.getExtension("asdkjrejvncnmiet/eru43jffn/ewrjpoewiwfjfpwrejtp.ext"));
    }
}