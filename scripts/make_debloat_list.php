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
        verify_item($item);
        if ($item['removal'] == 'unsafe') {
            fprintf(STDERR, "Removing unsafe item {$item['id']}\n");
            continue;
        }
        $item['type'] = $type;
        $debloat_list[] = $item;
    }
}

file_put_contents($target_file, json_encode($debloat_list, JSON_UNESCAPED_UNICODE));

function verify_item(array $item): void {
    // `id` is a string
    if (gettype($item['id']) != 'string') {
        fprintf(STDERR, "Expected `id` field to be a string, found: " . gettype($item['id']) . "\n");
    }
    // `label` is an optional string
    if (isset($item['label']) && gettype($item['label']) != 'string') {
        fprintf(STDERR, "{$item['id']}: Expected `label` field to be a string, found: " . gettype($item['label']) . "\n");
    }
    // `dependencies` is an optional string[]
    if (isset($item['dependencies'])) {
        if (gettype($item['dependencies']) != 'array') {
            fprintf(STDERR, "{$item['id']}: Expected `dependencies` field to be an array, found: " . gettype($item['dependencies']) . "\n");
        } else {
            foreach ($item['dependencies'] as $dependency) {
                if (gettype($dependency) != 'string') {
                    fprintf(STDERR, "{$item['id']}: Expected `dependencies` items to be a string, found: " . gettype($dependency) . "\n");
                }
            }
        }
    }
    // `required_by` is an optional string[]
    if (isset($item['required_by'])) {
        if (gettype($item['required_by']) != 'array') {
            fprintf(STDERR, "{$item['id']}: Expected `required_by` field to be an array, found: " . gettype($item['required_by']) . "\n");
        } else {
            foreach ($item['required_by'] as $required_by) {
                if (gettype($required_by) != 'string') {
                    fprintf(STDERR, "{$item['id']}: Expected `required_by` items to be a string, found: " . gettype($required_by) . "\n");
                }
            }
        }
    }
    // `tags` is an optional string[]
    if (isset($item['tags'])) {
        if (gettype($item['tags']) != 'array') {
            fprintf(STDERR, "{$item['id']}: Expected `tags` field to be an array, found: " . gettype($item['tags']) . "\n");
        } else {
            foreach ($item['tags'] as $tag) {
                if (gettype($tag) != 'string') {
                    fprintf(STDERR, "{$item['id']}: Expected `tags` items to be a string, found: " . gettype($tag) . "\n");
                } else if (!in_array($tag, SUPPORTED_TAGS)) {
                    fprintf(STDERR, "{$item['id']}: Invalid `tag`: $tag\n");
                }
            }
        }
    }
    // `description` is a string
    if (gettype($item['description']) != 'string') {
        fprintf(STDERR, "{$item['id']}: Expected `description` field to be a string, found: " . gettype($item['description']) . "\n");
    }
    // `web` is an optional string[]
    if (isset($item['web'])) {
        if (gettype($item['web']) != 'array') {
            fprintf(STDERR, "{$item['id']}: Expected `web` field to be an array, found: " . gettype($item['web']) . "\n");
        } else {
            foreach ($item['web'] as $site) {
                if (gettype($site) != 'string') {
                    fprintf(STDERR, "{$item['id']}: Expected `web` items to be a string, found: " . gettype($site) . "\n");
                }
            }
        }
    }
    // `removal` is a string
    if (gettype($item['removal']) != 'string') {
        fprintf(STDERR, "{$item['id']}: Expected `removal` field to be a string, found: " . gettype($item['removal']) . "\n");
    } else if (!in_array($item['removal'], SUPPORTED_REMOVAL_TYPES)) {
        fprintf(STDERR, "{$item['id']}: Invalid `removal` type: {$item['removal']}\n");
    }
    // `warning` is an optional string
    if (isset($item['warning']) && gettype($item['warning']) != 'string') {
        fprintf(STDERR, "{$item['id']}: Expected `warning` field to be a string, found: " . gettype($item['warning']) . "\n");
    }
    // `warning` is an optional string
    if (isset($item['suggestions'])) {
        if (gettype($item['suggestions']) != 'string') {
            fprintf(STDERR, "{$item['id']}: Expected `suggestions` field to be a string, found: " . gettype($item['suggestions']) . "\n");
        } else {
            $suggestion_file = REPO_DIR . '/suggestions/' . $item['suggestions'] . '.json';
            if (!file_exists($suggestion_file)) {
                fprintf(STDERR, "{$item['id']}: Suggestion ID ({$item['suggestions']}) does not exist.\n");
            }
        }
    }
}
