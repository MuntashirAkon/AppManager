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
        validate_suggestion_item($suggestion);
        $suggestion['_id'] = $suggestion_id;
        $suggestions[] = $suggestion;
    }
}

file_put_contents($target_file, json_encode($suggestions, JSON_UNESCAPED_UNICODE));

function validate_suggestion_item(array $item): void {
    // `id` is a string
    if (gettype($item['id']) != 'string') {
        fprintf(STDERR, "Expected `id` field to be a string, found: " . gettype($item['id']) . "\n");
    }
    // `label` is a string
    if (gettype($item['label']) != 'string') {
        fprintf(STDERR, "{$item['id']}: Expected `label` field to be a string, found: " . gettype($item['label']) . "\n");
    }
    // `reason` is an optional string
    if (isset($item['reason']) && gettype($item['reason']) != 'string') {
        fprintf(STDERR, "{$item['id']}: Expected `reason` field to be a string, found: " . gettype($item['reason']) . "\n");
    }
    // `source` is an optional string
    if (isset($item['source'])) {
        if (gettype($item['source']) != 'string') {
            fprintf(STDERR, "{$item['id']}: Expected `source` field to be a string, found: " . gettype($item['source']) . "\n");
        } else if (!preg_match("/^[fgas]+$/", $item['source'])) {
            fprintf(STDERR, "{$item['id']}: Expected `source` field to contain one or more from `fgas`, found: {$item['source']}\n");
        }
    }
    // `repo` is a string
    if (gettype($item['repo']) != 'string') {
        fprintf(STDERR, "{$item['id']}: Expected `repo` field to be a string, found: " . gettype($item['repo']) . "\n");
    }
}
