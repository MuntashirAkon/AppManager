// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.filters.options.FilterOptions;

@RunWith(RobolectricTestRunner.class)
public class FilterItemTest {
    @Test
    public void testAddFilterOption() {
        FilterItem filterItem = new FilterItem();
        filterItem.addFilterOption(FilterOptions.create("apk_size"));
        assertEquals("apk_size_1", filterItem.getExpr());
        filterItem.addFilterOption(FilterOptions.create("app_label"));
        assertEquals("apk_size_1 & app_label_2", filterItem.getExpr());
        filterItem.addFilterOption(FilterOptions.create("app_type"));
        assertEquals("apk_size_1 & app_label_2 & app_type_3", filterItem.getExpr());
        filterItem.addFilterOption(FilterOptions.create("backup"));
        assertEquals("apk_size_1 & app_label_2 & app_type_3 & backup_4", filterItem.getExpr());
    }

    @Test
    public void testUpdateFilterOption() {
        FilterItem filterItem = new FilterItem();
        filterItem.addFilterOption(FilterOptions.create("apk_size"));
        filterItem.addFilterOption(FilterOptions.create("app_label"));
        filterItem.addFilterOption(FilterOptions.create("app_type"));
        filterItem.addFilterOption(FilterOptions.create("backup"));
        assertEquals("apk_size_1 & app_label_2 & app_type_3 & backup_4", filterItem.getExpr());
        filterItem.updateFilterOptionAt(0, FilterOptions.create("compile_sdk"));
        assertEquals("compile_sdk_1 & app_label_2 & app_type_3 & backup_4", filterItem.getExpr());
        filterItem.updateFilterOptionAt(0, FilterOptions.create("components"));
        assertEquals("components_1 & app_label_2 & app_type_3 & backup_4", filterItem.getExpr());
        filterItem.updateFilterOptionAt(1, FilterOptions.create("pkg_name"));
        assertEquals("components_1 & pkg_name_2 & app_type_3 & backup_4", filterItem.getExpr());
        filterItem.updateFilterOptionAt(2, FilterOptions.create("version_name"));
        assertEquals("components_1 & pkg_name_2 & version_name_3 & backup_4", filterItem.getExpr());
        filterItem.updateFilterOptionAt(3, FilterOptions.create("times_opened"));
        assertEquals("components_1 & pkg_name_2 & version_name_3 & times_opened_4", filterItem.getExpr());
        assertThrows(IllegalArgumentException.class, () -> filterItem.updateFilterOptionAt(4, FilterOptions.create("apk_size")));
        filterItem.removeFilterOptionAt(0);
        filterItem.removeFilterOptionAt(0);
        filterItem.removeFilterOptionAt(0);
        assertEquals("times_opened_4", filterItem.getExpr());
        filterItem.updateFilterOptionAt(0, FilterOptions.create("running_apps"));
        assertEquals("running_apps_4", filterItem.getExpr());
        filterItem.removeFilterOptionAt(0);
        assertEquals("", filterItem.getExpr());
    }

    @Test
    public void testRemoveFilterOption() {
        FilterItem filterItem = new FilterItem();
        filterItem.addFilterOption(FilterOptions.create("apk_size"));
        assertEquals("apk_size_1", filterItem.getExpr());
        filterItem.addFilterOption(FilterOptions.create("app_label"));
        assertEquals("apk_size_1 & app_label_2", filterItem.getExpr());
        filterItem.addFilterOption(FilterOptions.create("app_type"));
        assertEquals("apk_size_1 & app_label_2 & app_type_3", filterItem.getExpr());
        filterItem.addFilterOption(FilterOptions.create("backup"));
        assertEquals("apk_size_1 & app_label_2 & app_type_3 & backup_4", filterItem.getExpr());
        filterItem.removeFilterOptionAt(3);
        assertEquals("apk_size_1 & app_label_2 & app_type_3", filterItem.getExpr());
        filterItem.removeFilterOptionAt(0);
        assertEquals("app_label_2 & app_type_3", filterItem.getExpr());
        filterItem.addFilterOption(FilterOptions.create("times_opened"));
        assertEquals("app_label_2 & app_type_3 & times_opened_1", filterItem.getExpr());
        filterItem.addFilterOption(FilterOptions.create("times_opened"));
        assertEquals("app_label_2 & app_type_3 & times_opened_1 & times_opened_4", filterItem.getExpr());
        filterItem.addFilterOption(FilterOptions.create("pkg_name"));
        assertEquals("app_label_2 & app_type_3 & times_opened_1 & times_opened_4 & pkg_name_5", filterItem.getExpr());
        filterItem.removeFilterOptionAt(0);
        filterItem.removeFilterOptionAt(0);
        filterItem.removeFilterOptionAt(0);
        filterItem.removeFilterOptionAt(0);
        assertEquals("pkg_name_5", filterItem.getExpr());
        filterItem.removeFilterOptionAt(0);
        assertEquals("", filterItem.getExpr());
    }
}