#!/usr/bin/env php
<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

// Example usage: php ./libs/add_lib.php

$filename = __DIR__ . '/libs/libsmali.jsonl';

$library_type = [
    "ui" => "UI Component",           // GUI support.
    "df" => "Development Framework",  // bigger frameworks
    "ut" => "Utility",                // other components to support a functional things like AWS support, video player.
    "da" => "Development Aid",        // fallback for dev stuff that doesn't fit in above or is unknown where to fit
    "sn" => "Social Network",
    "ad" => "Advertisement",
    "am" => "App Market",
    "ma" => "Mobile Analytics",
    "pa" => "Payment",
    "ge" => "Game Engine",
    "mp" => "Map"
];

while(true) {
    $entry = array();
    echo "ID: ";
    $entry["id"] = format_path(trim(fgets(STDIN)));
    echo "Path (Enter if same as ID): ";
    $entry["path"] = format_path(trim(fgets(STDIN)));
    if ($entry["path"] == '') $entry["path"] = $entry["id"];
    echo "Name: ";
    $entry["name"] = trim(fgets(STDIN));
    foreach($library_type as $key => $type) {
        echo ' ' . $key. ': ' . $type."\n";
    }
    echo "Type: ";
    $entry["type"] = $library_type[trim(fgets(STDIN))];
    echo "Permissions: ";
    $entry["perms"] = trim(fgets(STDIN));
    echo "URL: ";
    $entry["url"] = trim(fgets(STDIN));
    file_put_contents($filename, json_encode($entry)."\n", FILE_APPEND);

    echo "Add another? (y/n): ";
    if (trim(fgets(STDIN)) != 'y') break;
    echo "\n";
}

function format_path($path) {
    if ($path == '') return $path;
    if ($path[0] != '/') $path = '/'.$path;
    return str_replace('.', '/', $path);
}
