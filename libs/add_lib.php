#!/usr/bin/env php
<?php
/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Example usage: php ./libs/add_lib.php

$filename = "./libs/libsmali.txt";

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
