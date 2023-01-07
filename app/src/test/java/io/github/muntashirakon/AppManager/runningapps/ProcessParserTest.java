// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Objects;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class ProcessParserTest {
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    public void parse() {
        assert classLoader != null;
        Path procDir = Paths.get(classLoader.getResource("proc").getFile());
        ProcessParser pp = new ProcessParser();
        HashMap<Integer, String> processStrings = new HashMap<Integer, String>(5) {
            {
                put(1129, "ProcessItem{pid=1129, ppid=1, rss=380, user='1000', uid=1000, state='S', state_extra=''," +
                        " name='ATFWD-daemon', context='u:r:atfwd:s0'}");
                put(11, "ProcessItem{pid=11, ppid=2, rss=0, user='0', uid=0, state='S', state_extra=''," +
                        " name='rcuos/0', context='u:r:kernel:s0'}");
                put(11547, "ProcessItem{pid=11547, ppid=2, rss=0, user='0', uid=0, state='S', state_extra=''," +
                        " name='kworker/u16:4', context='u:r:kernel:s0'}");
                put(123, "ProcessItem{pid=123, ppid=2, rss=0, user='0', uid=0, state='S', state_extra=''," +
                        " name='irq/33-bcl_vbat', context='u:r:kernel:s0'}");
                put(1101, "ProcessItem{pid=1101, ppid=1, rss=981, user='1046', uid=1046, state='S', state_extra=''," +
                        " name='media.swcodec', context='u:r:mediaswcodec:s0'}");
            }
        };
        HashMap<Integer, ProcessItem> processItemHashMap = pp.parse(procDir);
        for (int pid : processItemHashMap.keySet()) {
            assertEquals(processStrings.get(pid), Objects.requireNonNull(processItemHashMap.get(pid)).toString());
        }
    }
}