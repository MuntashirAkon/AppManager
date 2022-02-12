<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

function list_files($dir) {
    return array_diff(scandir($res_dir), array('..', '.'));
}

// https://developer.android.com/guide/topics/resources/string-resource.html#FormattingAndStyling
function android_escape($string) {
    return strtr($string, array('@' => '\@', '?' => '\?', '<' => '&lt;', '>' => '&gt;', '"' => '\"', "'" => "\'", '&' => '&amp;'));
}

function android_escape_reverse($string) {
    return strtr($string, array('\@' => '@', '\?' => '?', '&lt;' => '<', '&gt;' => '>', '\"' => '"', "\'" => "'", '&amp;' => '&'));
}

function android_escape_slash($string) {
    return strtr($string, array('@' => '\@', '?' => '\?', '<' => '&lt;', '>' => '&gt;', '"' => '\"', "'" => "\'", '&' => '&amp;', '\\' => '\\\\'));
}
