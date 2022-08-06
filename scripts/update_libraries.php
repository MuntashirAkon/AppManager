<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

require_once __DIR__ . '/android-libraries/php/AndroidLibV1.php';
require_once __DIR__ . '/utils.php';

const LIBS_FILE = __DIR__ . '/android-libraries/libs.json';

const TRACKERS_XML = __DIR__ . '/../app/src/main/res/values/trackers.xml';
const LIBS_XML = __DIR__ . '/../app/src/main/res/values/libs.xml';
const NATIVE_LIBS_XML = __DIR__ . '/../app/src/main/res/values/native_libs.xml';

$libs = parse_libs_file(LIBS_FILE);

update_trackers($libs);
update_native_libraries($libs);
update_libraries($libs);

/**
 * @param AndroidLibV1[] $libs
 * @return void
 */
function update_libraries(array $libs): void {
    $libs_info = array();
    foreach ($libs as $lib) {
        if ($lib->code_signatures == null) continue;
        $lib_info = array();
        $lib_info['code_sigs'] = array();
        $arr = explode("|", $lib->code_signatures);
        foreach ($arr as $sig) {
            if (!in_array($sig, $lib_info['code_sigs'])) {
                if (str_starts_with($sig, '/')) {
                    $sig = substr($sig, 1, strlen($sig) - 1);
                }
                $lib_info['code_sigs'][] = str_replace('/', '.', $sig);
            }
        }
        $lib_info['type'] = $lib->type;
        $lib_info['website'] = $lib->website ?? '';
        $libs_info[$lib->label] = $lib_info;
    }
    ksort($libs_info);
    $out = <<<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<resources>
    <array name="lib_signatures">

EOF;

    foreach ($libs_info as $info) {
        foreach ($info["code_sigs"] as $sig) {
            $out .= "        <item>$sig</item>\n";
        }
    }

    $out .= <<<EOF
    </array>
    <array name="lib_names">

EOF;

    foreach ($libs_info as $name => $info) {
        foreach ($info["code_sigs"] as $ignored) {
            $out .= "        <item>" . android_escape($name) . "</item>\n";
        }
    }

    $out .= <<<EOF
    </array>
    <array name="lib_types">

EOF;

    foreach ($libs_info as $info) {
        foreach ($info["code_sigs"] as $ignored) {
            $out .= "        <item>" . android_escape($info['type']) . "</item>\n";
        }
    }

    $out .= <<<EOF
    </array>
    <array name="lib_website">

EOF;

    foreach ($libs_info as $info) {
        foreach ($info["code_sigs"] as $ignored) {
            $out .= "        <item>" . android_escape($info['website']) . "</item>\n";
        }
    }

    $out .= <<<EOF
    </array>
</resources>
EOF;
    file_put_contents(LIBS_XML, $out);
}


/**
 * @param AndroidLibV1[] $libs
 * @return void
 */
function update_native_libraries(array $libs): void {
    $libs_info = array();
    foreach ($libs as $lib) {
        if ($lib->solib_signatures == null) continue;
        $lib_info = array();
        $lib_info['label'] = $lib->label;
        $lib_info['signature'] = $lib->solib_signatures;
        $lib_info['tracker'] = $lib->anti_features != null && in_array('Tracking', $lib->anti_features);
        $lib_info['relativeUrl'] = $lib->website ?? '';
        $libs_info[] = $lib_info;
    }
    usort($libs_info, function ($o1, $o2) {
        return $o1['label'] <=> $o2['label'];
    });
    $out = <<<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<resources>
    <array name="lib_native_signatures">

EOF;

    foreach ($libs_info as $info) {
        $out .= "        <item>" . android_escape_slash($info["signature"]) . "</item>\n";
    }

    $out .= <<<EOF
    </array>
    <array name="lib_native_names">

EOF;

    foreach ($libs_info as $info) {
        $out .= "        <item>" . android_escape($info['label']) . "</item>\n";
    }

    $out .= <<<EOF
    </array>
    <integer-array name="lib_native_is_tracker">

EOF;

    foreach ($libs_info as $info) {
        $out .= "        <item>" . ($info['tracker'] == "yes" ? 1 : 0) . "</item>\n";
    }

    $out .= <<<EOF
    </integer-array>
    <array name="lib_native_website">

EOF;

    foreach ($libs_info as $info) {
        $out .= "        <item>" . android_escape($info['relativeUrl']) . "</item>\n";
    }

    $out .= <<<EOF
    </array>
</resources>
EOF;
    file_put_contents(NATIVE_LIBS_XML, $out);
}


/**
 * @param AndroidLibV1[] $libs
 * @return void
 */
function update_trackers(array $libs) : void {
    $tracker_info = array();
    foreach ($libs as $lib) {
        if ($lib->code_signatures == null) continue;
        if ($lib->exodus_id != null || $lib->etip_id != null) {
            $label = ($lib->exodus_id != null ? '' : '²') . $lib->label;
            if (!isset($tracker_info[$label])) {
                $tracker_info[$label] = array();
            }
            $tracker_info[$label]['code_sigs'] = array();
            $arr = explode("|", $lib->code_signatures);
            foreach ($arr as $sig) {
                if (!in_array($sig, $tracker_info[$label]["code_sigs"])) {
                    if (str_starts_with($sig, '/')) {
                        $sig = substr($sig, 1, strlen($sig) - 1);
                    }
                    $tracker_info[$label]["code_sigs"][] = str_replace('/', '.', $sig);
                }
            }
        }
    }
    ksort($tracker_info);
    $out = <<<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<resources>
    <string-array name="tracker_signatures">

EOF;

    foreach($tracker_info as $info) {
        foreach($info["code_sigs"] as $sig) {
            $out .= "        <item>$sig</item>\n";
        }
    }

    $out .= <<<EOF
    </string-array>
    <string-array name="tracker_names">

EOF;

    foreach($tracker_info as $tracker => $info) {
        foreach($info["code_sigs"] as $ignored) {
            $out .= "        <item>$tracker</item>\n";
        }
    }

    $out .= <<<EOF
    </string-array>
</resources>
EOF;
    file_put_contents(TRACKERS_XML, $out);
}


/**
 * @param string $libs_file
 * @return AndroidLibV1[]
 */
function parse_libs_file(string $libs_file): array {
    $libs = json_decode(file_get_contents($libs_file), true);

    $parsed_lib = array();
    foreach ($libs as $lib) {
        $parsed_lib[] = AndroidLibV1::fromJson($lib);
    }
    return $parsed_lib;
}
