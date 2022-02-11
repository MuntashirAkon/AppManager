#!/usr/bin/env php
<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

date_default_timezone_set('UTC');

// USAGE: php ./scripts/docs.php VERB [ARGS]
// VERBS:
// build <lang>     Build HTML from TeX using Pandoc for the given language.

const MAIN_TEX = 'main.tex';
const CUSTOM_CSS = 'custom.css';
const OUTPUT_FILENAME = 'index.html';
const RAW_DIR = './docs/raw';
const BASE_DIR = RAW_DIR . '/en';

function build_html($lang) {
    $pwd = RAW_DIR . '/' . $lang;
    $main_tex = $pwd . '/' . MAIN_TEX;
    $output_file = $pwd . '/' . OUTPUT_FILENAME;
    // Fixed directories
    $base_dir = getcwd() . '/' . BASE_DIR;
    $custom_css = $base_dir . '/' . CUSTOM_CSS;
    $lua_dir = $base_dir . '/lua';
    // Sequence must be preserved
    $lua_files = [
        'toc_generator.lua',
        'img_to_object.lua',
        'header_with_hyperlinks.lua',
        'alert_fix.lua'
    ];
    $cmd = 'cd "' .$pwd. '" && pandoc "' . MAIN_TEX . '" -c "' . CUSTOM_CSS .'" -o "' . OUTPUT_FILENAME . '" -t html5'
        . ' -f latex -s --toc -N --section-divs --default-image-extension=png -i -F pandoc-crossref --citeproc'
        . ' --highlight-style=monochrome --verbose';
    foreach ($lua_files as $lua_script) {
        $cmd .= ' --lua-filter="' . $lua_dir . '/' . $lua_script . '"';
    }
    // Run command
    passthru($cmd, $ret_val);
    if ($ret_val != 0) {
        echo 'Pandoc could not generate an HTML file.';
        exit(1);
    }

    // Read colours
    $main_contents = file_get_contents($main_tex);
    preg_match_all('/\\\definecolor\{(?<name>[^\}]+)\}\{HTML\}\{(?<color>[0-9a-fA-F]+)\}/', $main_contents, $matches);

    // Replace colours
    $to_search = array();
    $to_replace = array();
    foreach ($matches['name'] as $color_name) {
        array_push($to_search, "/style=\"background-color: ${color_name}\"/", "/style=\"color: ${color_name}\"/");
    }
    foreach ($matches['color'] as $color_value) {
        array_push($to_replace, "class=\"colorbox\" style=\"background-color: #${color_value}\"", "style=\"color: #${color_value}\"");
    }

    // Replace version name and date
    $am_version = system("grep -m1 versionName ./app/build.gradle | awk -F \\\" '{print $2}'", $ret_val);
    if ($ret_val != 0) {
        echo 'Could not get the versionName from ./app/build.gradle';
        exit(1);
    }
    $today = date('j F Y');
    array_push($to_replace, $am_version, $today);
    array_push($to_search, '/\$ABC\$APP-MANAGER-VERSION\$XYZ\$/', '/\$ABC\$USER-MANUAL-DATE\$XYZ\$/');
    $output_contents = file_get_contents($output_file);
    $output_contents = preg_replace($to_search, $to_replace, $output_contents);

    file_put_contents($output_file, $output_contents);

    // Minify CSS
    $cmd = "minify \"${custom_css}\" -o \"${base_dir}/../css/custom.css\"";
    system($cmd, $ret_val);
    if ($ret_val != 0) {
        echo "Could not minify custom.css";
        exit(1);
    }
    // Minify HTML
    $cmd = "minify \"${output_file}\" -o \"${pwd}/index.min.html\" && mv \"${pwd}/index.min.html\" \"${output_file}\"";
    system($cmd, $ret_val);
    if ($ret_val != 0) {
        echo "Could not minify index.html";
        exit(1);
    }
}

// MAIN //
if ($argc < 2) {
    echo 'Invalid number of arguments.';
    exit(1);
}

$verb = $argv[1];

switch($verb) {
    case 'build':
        if (!isset($argv[2])) {
            echo 'build <lang>';
            exit(1);
        }
        build_html($argv[2]);
        break;
}