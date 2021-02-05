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

package io.github.muntashirakon.AppManager.runningapps;

import org.junit.Test;

import java.io.File;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class ProcessParserTest {
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    public void parse() {
        assert classLoader != null;
        File procDir = new File(classLoader.getResource("proc").getFile());
        ProcessParser pp = new ProcessParser(true);
        HashMap<Integer, ProcessItem> processItemHashMap = pp.parse(procDir);
        assertEquals("{1129=ProcessItem{pid=1129, ppid=1, rss=380, vsz=81948672, user='1000', uid=1000," +
                " state='S', state_extra='', name='ATFWD-daemon', context='u:r:atfwd:s0'}, 11=ProcessItem{pid=11," +
                " ppid=2, rss=0, vsz=0, user='0', uid=0, state='S', state_extra='', name='rcuos/0'," +
                " context='u:r:kernel:s0'}, 11547=ProcessItem{pid=11547, ppid=2, rss=0, vsz=0, user='0', uid=0," +
                " state='S', state_extra='', name='kworker/u16:4', context='u:r:kernel:s0'}, 123=ProcessItem{pid=123," +
                " ppid=2, rss=0, vsz=0, user='0', uid=0, state='S', state_extra='', name='irq/33-bcl_vbat'," +
                " context='u:r:kernel:s0'}, 1101=ProcessItem{pid=1101, ppid=1, rss=981, vsz=277195735040, user='1046'," +
                " uid=1046, state='S', state_extra='', name='mediaswcodec', context='u:r:mediaswcodec:s0'}}",
                processItemHashMap.toString());
    }
}