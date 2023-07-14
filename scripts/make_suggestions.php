<?php
/* SPDX-License-Identifier: AGPL-3.0-or-later */

require_once __DIR__ . '/utils.php';

const REPO_DIR = __DIR__ . '/android-debloat-list';
const SUGGESTIONS_DIR = REPO_DIR . '/suggestions';

$target_file = __DIR__ . '/../app/src/main/assets/suggestions.json';

$suggestions = array();
foreach (list_files(SUGGESTIONS_DIR) as $filename) {
    if (!str_ends_with($filename, ".json")) {
        continue;
    }
    $suggestion_file = SUGGESTIONS_DIR . '/' . $filename;
    $suggestion_id = substr($filename, 0, -5);
    $single_suggestion_list = json_decode(file_get_contents($suggestion_file), true);
    if ($single_suggestion_list === null) {
        fprintf(STDERR, "Malformed file: $suggestion_file\n");
        continue;
    } else fprintf(STDERR, "Adding $filename\n");
    foreach ($single_suggestion_list as $suggestion) {
        $suggestion['_id'] = $suggestion_id;
        $suggestions[] = $suggestion;
    }
}

file_put_contents($target_file, json_encode($suggestions, JSON_UNESCAPED_UNICODE));
