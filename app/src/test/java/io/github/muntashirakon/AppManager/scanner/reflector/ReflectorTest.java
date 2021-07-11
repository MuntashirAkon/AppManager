/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.muntashirakon.AppManager.scanner.reflector;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ReflectorTest {
    @Test
    public void getImportsTest() {
        List<String> expectedClasses = new ArrayList<String>() {
            {
                add("java.lang.String");
                add("java.util.Hashtable");
            }
        };
        Collections.sort(expectedClasses);
        Reflector reflector = new Reflector(ClassTypeAlgorithm.class);
        List<String> actualClasses = new ArrayList<>(reflector.getImports());
        Collections.sort(actualClasses);
        assertEquals(expectedClasses, actualClasses);
    }
}