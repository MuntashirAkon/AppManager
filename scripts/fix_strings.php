#!/usr/bin/env php
<?php
/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// Example usage: php ./scripts/fix_strings.php ./app/src/main/res/

if ($argc != 2) {
  echo "USAGE: php ./scripts/fix_strings.php <res_dir>\n";
  exit(1);
}

$res_dir = $argv[1];
$string_files = array();

if (file_exists($res_dir)) {
  if (!is_dir($res_dir)) {
    echo "$res_dir is not a directory.\n";
    exit(1);
  } else {
    $_files = array_diff(scandir($res_dir), array('..', '.'));
    foreach ($_files as $file) {
      if (strpos($file, 'values-') !== false) {
        $file = $res_dir.'/'.$file.'/strings.xml';
        if (file_exists($file)) {
          array_push($string_files, $file);
        }
      }
    }
  }
} else {
  echo "$res_dir doesn't exist.\n";
  exit(1);
}

// We've got all the strings.xml files at this point
// Apply fixes
$patterns = [
  '/\&lt\;xliff\:g xmlns\:xliff\=\\\"urn\:oasis\:names\:tc\:xliff\:document\:1\.2\\\" id\=\\\"([^\"]+)\\\" example\=\\\"([^\"]+)\\\"\&gt\;([^\&]+)\&lt\;\/xliff\:g\&gt\;/',
  '/\&lt\;a href\=\\\"([^\"]+)\\\"\&gt\;([^\&]+)\&lt\;\/a\&gt\;/',
  '/\&lt\;(\w+)\&gt\;([^\&]+)\&lt\;\/(\w+)\&gt\;/',
];
$replacements = [
  '<xliff:g id="$1" example="$2">$3</xliff:g>',
  '<a href="$1">$2</a>',
  '<$1>$2</$3>'
];
foreach ($string_files as $string_file) {
  $contents = file_get_contents($string_file);
  file_put_contents($string_file, preg_replace($patterns, $replacements, $contents));
}
