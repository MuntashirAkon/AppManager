<?php
/* SPDX-License-Identifier: AGPL-3.0-or-later */

require_once __DIR__ . '/utils.php';

const SUPPORTED_REMOVAL_TYPES = ['delete', 'replace', 'caution', 'unsafe'];
const SUPPORTED_TAGS = [];
const REPO_DIR = __DIR__ . '/android-debloat-list';

$target_file = __DIR__ . '/../app/src/main/assets/debloat.json';

$debloat_list = array();
foreach (list_files(REPO_DIR) as $filename) {
    if (!str_ends_with($filename, ".json")) {
        continue;
    }
    $file = REPO_DIR . '/' . $filename;
    $type = substr($filename, 0, -5);
    $list = json_decode(file_get_contents($file), true);
    if ($list === null) {
        fprintf(STDERR, "Malformed file: $file\n");
        continue;
    } else fprintf(STDERR, "Adding $filename\n");
    foreach ($list as $item) {
        if ($item['removal'] == 'unsafe') {
            fprintf(STDERR, "Removing unsafe item {$item['id']}\n");
            continue;
        }
        if (isset($item['suppress'])) {
            unset($item['suppress']);
        }
        $item['type'] = $type;
        $debloat_list[] = $item;
    }
}

file_put_contents($target_file, json_encode($debloat_list, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
