// SPDX-License-Identifier: GPL-3.0-or-later

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