#!/usr/bin/env php
<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

// Example usage: php ./scripts/update_libs.php > ./app/src/main/res/values/libs.xml

define('LIB_RADAR', 'https://gitlab.com/IzzyOnDroid/repo/-/raw/master/lib/libradar.txt');
define('LIB_RADAR_WILD', 'https://gitlab.com/IzzyOnDroid/repo/-/raw/master/lib/libradar_wild.txt');
define('LIB_SMALI', 'https://gitlab.com/IzzyOnDroid/repo/-/raw/master/lib/libsmali.txt');
define('AM_LIB_SMALI', './libs/libsmali.txt');

$libs_info = array();

addLibRadar(LIB_RADAR);
addLibRadar(LIB_RADAR_WILD);
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
<!--
  ~ Copyright (C) 2020 Muntashir Al-Islam
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program.  If not, see <https://www.gnu.org/licenses/>.
  -->

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

// https://developer.android.com/guide/topics/resources/string-resource.html#FormattingAndStyling
function android_escape($string) {
    return strtr($string, array('@' => '\@', '?' => '\?', '<' => '&lt;', '>' => '&gt;', '"' => '\"', "'" => "\'", '&' => '&amp;'));
}
