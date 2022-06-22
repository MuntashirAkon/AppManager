#!/usr/bin/env php
<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

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
    parse_packages($packages, $directory . '/' . $file);
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

function parse_packages(&$packages, $filename): void {
    $contents = explode("\n", file_get_contents($filename));
    $last_key = "";
    foreach ($contents as $dirty_line) {
        $line = trim($dirty_line);
        if (empty($line) || str_starts_with($line, '#')) {
            continue;
        }
        if (str_starts_with($line, 'declare -a')) {
            $last_key = substr($line, 11, strpos($line, '=(') - 11);
            $packages[$last_key] = array();
        }
        if (str_starts_with($line, '"')) {
            $packages[$last_key][] = substr($line, 1, strpos($line, '"', 1) - 1);
        }
    }
}
