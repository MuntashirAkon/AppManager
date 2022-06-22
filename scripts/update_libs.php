#!/usr/bin/env php
<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

// Example usage: php ./scripts/update_libs.php > ./app/src/main/res/values/libs.xml

require_once __DIR__ . "/utils.php";

const LIB_SMALI = 'https://gitlab.com/IzzyOnDroid/repo/-/raw/master/lib/libsmali.jsonl';
const AM_LIB_SMALI = './libs/libsmali.jsonl';

$libs_info = array();

addLibSmali(LIB_SMALI);
addLibSmali(AM_LIB_SMALI);

printf_AM();

// === Functions === //

function printf_AM(): void {
    global $libs_info;
    echo <<<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<resources>
    <array name="lib_signatures">

EOF;

    foreach ($libs_info as $info) {
        foreach ($info["code_sigs"] as $sig) {
            echo "        <item>$sig</item>\n";
        }
    }

    echo <<<EOF
    </array>
    <array name="lib_names">

EOF;

    foreach ($libs_info as $name => $info) {
        foreach ($info["code_sigs"] as $ignored) {
            echo "        <item>" . android_escape($name) . "</item>\n";
        }
    }

    echo <<<EOF
    </array>
    <array name="lib_types">

EOF;

    foreach ($libs_info as $info) {
        foreach ($info["code_sigs"] as $ignored) {
            echo "        <item>" . android_escape($info['type']) . "</item>\n";
        }
    }

    echo <<<EOF
    </array>
    <array name="lib_website">

EOF;

    foreach ($libs_info as $info) {
        foreach ($info["code_sigs"] as $ignored) {
            echo "        <item>" . android_escape($info['website']) . "</item>\n";
        }
    }

    echo <<<EOF
    </array>
</resources>
EOF;
}

function addLibSmali($filename = 'libsmali.jsonl'): void {
    global $libs_info;
    $file = file_get_contents($filename);
    $libs = explode("\n", $file);
    foreach ($libs as $lib) {
        if (empty($lib)) continue;
        $lib_arr = json_decode($lib, true);
        $sig = $lib_arr['path'];
        if ($sig[0] == '/') $sig = substr($sig, 1);
        $sig = str_replace('/', '.', $sig);
        $name = $lib_arr['name'];
        if ($name == "") {
            continue;
        }
        $type = $lib_arr['type'];
        $url = $lib_arr['url'];
        if (isset($libs_info[$name])) {
            // Update signatures
            if (!in_array($sig, $libs_info[$name]["code_sigs"])) {
                $libs_info[$name]["code_sigs"][] = $sig;
            }
        } else {
            // Add new
            $libs_info[$name] = [
                "code_sigs" => array($sig),
                "type" => $type,
                "website" => $url
            ];
        }
    }
}
