#!/usr/bin/env php
<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

// Example usage: php ./scripts/fix_strings.php ./app/src/main/res/

if ($argc != 2) {
    echo "USAGE: php ./scripts/fix_strings.php <res_dir>\n";
    exit(1);
}

$res_dir = $argv[1];
$string_files = array();

if (file_exists($res_dir)) {
    if (!is_dir($res_dir)) {
        echo "$res_dir is not a directory.\n";
        exit(1);
    } else {
        $_files = array_diff(scandir($res_dir), array('..', '.'));
        foreach ($_files as $file) {
            if (str_contains($file, 'values-')) {
                $file = $res_dir . '/' . $file . '/strings.xml';
                if (file_exists($file)) {
                    $string_files[] = $file;
                }
            }
        }
    }
} else {
    echo "$res_dir doesn't exist.\n";
    exit(1);
}

// We've got all the strings.xml files at this point
// Apply fixes
$patterns = [
    /** @lang RegExp */
    '/\&lt\;xliff\:g xmlns\:xliff\=\\\"urn\:oasis\:names\:tc\:xliff\:document\:1\.2\\\" id\=\\\"([^\"]+)\\\" example\=\\\"([^\"]+)\\\"\&gt\;([^\&]+)\&lt\;\/xliff\:g\&gt\;/',
    /** @lang RegExp */
    '/\&lt\;a href\=\\\"([^\"]+)\\\"\&gt\;([^\&]+)\&lt\;\/a\&gt\;/',
    /** @lang RegExp */
    '/\&lt\;(\w+)\&gt\;([^\&]+)\&lt\;\/(\w+)\&gt\;/',
];
$replacements = [
    /** @lang text */
    '<xliff:g id="$1" example="$2">$3</xliff:g>',
    /** @lang text */
    '<a href="$1">$2</a>',
    /** @lang text */
    '<$1>$2</$3>'
];
foreach ($string_files as $string_file) {
    $contents = file_get_contents($string_file);
    file_put_contents($string_file, preg_replace($patterns, $replacements, $contents));
}
