#!/usr/bin/env php
<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

// Example usage: php ./scripts/update_native_libs.php > ./app/src/main/res/values/native_libs.xml

require_once __DIR__ . "/utils.php";

const LIB_NATIVE = 'https://raw.githubusercontent.com/gnuhead-chieb/solibs/main/solibs.json';

$libs_info = json_decode(file_get_contents(LIB_NATIVE), true);

printf_AM();

// === Functions === //
function printf_AM()
{
    global $libs_info;
    echo <<<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<resources>
    <array name="lib_native_signatures">

EOF;

    foreach ($libs_info as $info) {
        echo "        <item>" . android_escape_slash($info["signature"]) . "</item>\n";
    }

    echo <<<EOF
    </array>
    <array name="lib_native_names">

EOF;

    foreach ($libs_info as $info) {
        echo "        <item>" . android_escape($info['label']) . "</item>\n";
    }

    echo <<<EOF
    </array>
    <integer-array name="lib_native_is_tracker">

EOF;

    foreach ($libs_info as $info) {
        echo "        <item>" . ($info['tracker'] == "yes" ? 1 : 0) . "</item>\n";
    }

    echo <<<EOF
    </integer-array>
    <array name="lib_native_website">

EOF;

    foreach ($libs_info as $info) {
        echo "        <item>" . android_escape($info['relativeUrl']) . "</item>\n";
    }

    echo <<<EOF
    </array>
</resources>
EOF;
}
