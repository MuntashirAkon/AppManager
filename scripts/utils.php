<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

function list_files($dir) {
    return array_diff(scandir($res_dir), array('..', '.'));
}

function list_files_recursive($dir) {
    if ($dir[strlen($dir) - 1] != '/') $dir = $dir . '/';
    $iterator = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($dir));
    $paths = array();
    foreach ($iterator as $file) {
        // Ignore .. and .
        $pathname = $file->getPathname();
        $last_segment = substr($pathname, strrpos($pathname, '/') + 1);
        if ($last_segment == '..') continue;
        $relative_path = str_replace($dir, '', $pathname);
        if ($relative_path == '.') continue; // Ignore this directory
        if ($last_segment == '.') $relative_path = substr($relative_path, 0, strlen($relative_path) - 1);
        array_push($paths, $relative_path);
    }
    return $paths;
}

// https://developer.android.com/guide/topics/resources/string-resource.html#FormattingAndStyling
function android_escape($string) {
    return strtr($string, array('@' => '\@', '?' => '\?', '<' => '&lt;', '>' => '&gt;', '"' => '\"', "'" => "\'", '&' => '&amp;'));
}

function android_escape_slash($string) {
    return strtr($string, array('@' => '\@', '?' => '\?', '<' => '&lt;', '>' => '&gt;', '"' => '\"', "'" => "\'", '&' => '&amp;', '\\' => '\\\\'));
}

function android_escape_slash_newline($string) {
    return strtr($string, array('@' => '\@', '?' => '\?', '<' => '&lt;', '>' => '&gt;', '"' => '\"', "'" => "\'", '&' => '&amp;', '\\' => '\\\\', "\n" => '\n'));
}

function android_escape_slash_newline_reverse($string) {
    return strtr($string, array('\@' => '@', '\?' => '?', '&lt;' => '<', '&gt;' => '>', '\"' => '"', "\'" => "'", '&amp;' => '&', '\\\\' => '\\', '\n' => "\n"));
}
