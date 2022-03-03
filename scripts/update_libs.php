#!/usr/bin/env php
<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

// Example usage: php ./scripts/update_libs.php > ./app/src/main/res/values/libs.xml

require_once __DIR__ . "/utils.php";

const LIB_SMALI = 'https://gitlab.com/IzzyOnDroid/repo/-/raw/master/lib/libsmali.txt';
const AM_LIB_SMALI = './libs/libsmali.txt';

$libs_info = array();

addLibSmali(LIB_SMALI);
addLibSmali(AM_LIB_SMALI);

printf_AM();

// === Functions === //

function printf_libs() {
    global $libs_info;
    $s = "____";
    foreach ($libs_info as $name => $info) {
        foreach ($info["code_sigs"] as $sig) {
            echo $name . $s . $sig . $s . $info['type'] . $s . $info['website'] . "\n";
        }
    }
}

function printf_AM() {
    global $libs_info;
    echo <<<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<resources>
    <array name="lib_signatures">

EOF;

    foreach ($libs_info as $name => $info) {
        foreach ($info["code_sigs"] as $sig) {
            echo "        <item>${sig}</item>\n";
        }
    }

    echo <<<EOF
    </array>
    <array name="lib_names">

EOF;

    foreach ($libs_info as $name => $info) {
        foreach ($info["code_sigs"] as $sig) {
            echo "        <item>" . android_escape($name) . "</item>\n";
        }
    }

    echo <<<EOF
    </array>
    <array name="lib_types">

EOF;

    foreach ($libs_info as $name => $info) {
        foreach ($info["code_sigs"] as $sig) {
            echo "        <item>" . android_escape($info['type']) . "</item>\n";
        }
    }

    echo <<<EOF
    </array>
    <array name="lib_website">

EOF;

    foreach ($libs_info as $name => $info) {
        foreach ($info["code_sigs"] as $sig) {
            echo "        <item>" . android_escape($info['website']) . "</item>\n";
        }
    }

    echo <<<EOF
    </array>
</resources>
EOF;
}

function addLibSmali($filename = 'libsmali.txt') {
    global $libs_info;
    $file = file_get_contents($filename);
    $libs = explode("\n", $file);
    foreach ($libs as $lib) {
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
                array_push($libs_info[$name]["code_sigs"], $sig);
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

function addLibRadar($filename = 'libradar.txt') {
    global $libs_info;
    $file = file_get_contents($filename);
    $libs = explode("\n", $file);
    foreach ($libs as $lib) {
        $lib_arr = json_decode($lib, true);
        $sig = $lib_arr['pn'];
        if ($sig[0] == '/') $sig = substr($sig, 1);
        $sig = str_replace('/', '.', $sig);
        $name = $lib_arr['lib'];
        if ($name == "") {
            print_r($lib_arr);
            continue;
        }
        $type = $lib_arr['tp'];
        $url = $lib_arr['ch'];
        if (isset($libs_info[$name])) {
            // Update signatures
            if (!in_array($sig, $libs_info[$name]["code_sigs"])) {
                array_push($libs_info[$name]["code_sigs"], $sig);
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
