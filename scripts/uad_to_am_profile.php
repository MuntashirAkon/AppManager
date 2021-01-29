#!/usr/bin/env php
<?php
/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

// Example usage: php ./scripts/uad_to_am_profile.php ./app/src/main/assets/profiles

if ($argc != 2) {
    echo "USAGE: php ./scripts/uad_to_am_profile.php <saving_dir>\n";
    exit(1);
}

$output_dir = $argv[1];

if (file_exists($output_dir)) {
    if (!is_dir($output_dir)) {
        echo "$output_dir is not a directory.\n";
    } else {
        # Remove old files
        $files = array_diff(scandir($output_dir), array('..', '.'));
        foreach ($files as $file) {
            unlink($output_dir . '/' . $file);
        }
    }
} else mkdir($output_dir);

$directory = "./scripts/universal-android-debloater/lists";
$files = array_diff(scandir($directory), array('..', '.', '0 - Pending.sh'));

$packages = array();

# Parse
foreach ($files as $file) {
    parse_packages($directory . '/' . $file);
}

# Save
echo "    <string-array name=\"profiles\">\n";
foreach ($packages as $profile_name => $package_list) {
    $profile = [
        "type" => 0,
        "version" => 1,
        "state" => "on",
        "name" => $profile_name,
        "packages" => $package_list,
        "misc" => ["disable"]
    ];
    file_put_contents($output_dir . '/' . $profile_name . '.am.json', json_encode($profile/*, JSON_PRETTY_PRINT*/));
    echo "        <item>$profile_name</item>\n";
}
echo "    </string-array>\n";

function parse_packages($filename) {
    global $packages;
    $contents = explode("\n", file_get_contents($filename));
    $last_key = "";
    foreach ($contents as $dirty_line) {
        $line = trim($dirty_line);
        if (empty($line) || substr($line, 0, 1) == '#') {
            continue;
        }
        if (substr($line, 0, 10) == 'declare -a') {
            $last_key = substr($line, 11, strpos($line, '=(') - 11);
            $packages[$last_key] = array();
        }
        if (substr($line, 0, 1) == '"') {
            array_push($packages[$last_key], substr($line, 1, strpos($line, '"', 1) - 1));
        }
    }
}
