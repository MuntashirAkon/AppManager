<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

use JetBrains\PhpStorm\NoReturn;

function list_files(string $dir): array {
    return array_diff(scandir($dir), array('..', '.'));
}

function list_files_recursive(string $dir) : array {
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
        $paths[] = $relative_path;
    }
    return $paths;
}

// https://developer.android.com/guide/topics/resources/string-resource.html#FormattingAndStyling
function android_escape(string $string): string {
    return strtr($string, array('@' => '\@', '?' => '\?', '<' => '&lt;', '>' => '&gt;', '"' => '\"', "'" => "\'", '&' => '&amp;'));
}

function android_escape_slash(string $string) : string {
    return strtr($string, array('@' => '\@', '?' => '\?', '<' => '&lt;', '>' => '&gt;', '"' => '\"', "'" => "\'", '&' => '&amp;', '\\' => '\\\\'));
}

function android_escape_slash_newline(string $string) : string {
    return strtr($string, array('@' => '\@', '?' => '\?', '<' => '&lt;', '>' => '&gt;', '"' => '\"', "'" => "\'", '&' => '&amp;', '\\' => '\\\\', "\n" => '\n'));
}

function android_escape_slash_newline_reverse(string $string) : string {
    return strtr($string, array('\@' => '@', '\?' => '?', '&lt;' => '<', '&gt;' => '>', '\"' => '"', "\'" => "'", '&amp;' => '&', '\\\\' => '\\', '\n' => "\n"));
}

#[NoReturn]
function syntax_error_with_position(string $path, string $texts, int $position, string $error_message = "Syntax error"): void {
    $lines = explode("\n", $texts);
    $line_no = 0;
    foreach ($lines as $line) {
        ++$line_no;
        $len = strlen($line);
        if ($len > $position) {
            break;
        }
        $position -= ($len + 1);
    }
    fprintf(STDERR, "\e[41;1mError:\e[0m %s near $path:$line_no:%d \n", $error_message, abs($position));
    exit(1);
}