#!/usr/bin/env php
<?php
/* SPDX-License-Identifier: GPL-3.0-or-later */

date_default_timezone_set('UTC');

// USAGE: php ./scripts/docs.php VERB [ARGS]
// VERBS:
// build <lang>     Build HTML from TeX using Pandoc for the given language.
// rebase           Extract strings from the TeX files and re-create the base translation file.
// update <lang>    Rebuild HTML from strings.xml for the given language.
// deploy           Rebuild HTML and deploy it to the GitHub pages.
// pdf              Build PDF from TeX using pdflatex (English-only).
// debug            Do experiments.

// External requirements: Pandoc, pandoc-crossref, minify

require_once __DIR__ . "/utils.php";

const MAIN_TEX = 'main.tex';
const CUSTOM_CSS = 'custom.css';
const OUTPUT_FILENAME = 'index.html';
const STRINGS_XML = 'strings.xml';
const RAW_DIR = './docs/raw';
const BASE_DIR = RAW_DIR . '/en';

// Deployment
const DIST_DIR = './docs/dist';
const DIST_REPO = 'git@github.com:MuntashirAkon/AppManager.git';
const DIST_BRANCH = 'pages';

/**
 * Build and minify the outputs from TeX.
 *
 * @param string $lang Target language e.g. en, ru, ja, etc.
 */
function build_html(string $lang) {
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
        . ' --highlight-style=monochrome -M lang=' . get_IETF_language_tag($lang);
    foreach ($lua_files as $lua_script) {
        $cmd .= ' --lua-filter="' . $lua_dir . '/' . $lua_script . '"';
    }
    // Create variables first
    create_transient_tex($pwd, get_IETF_language_tag($lang));
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
        array_push($to_search, "/style=\"background-color: $color_name\"/", "/style=\"color: $color_name\"/");
    }
    foreach ($matches['color'] as $color_value) {
        array_push($to_replace, "class=\"colorbox\" style=\"background-color: #$color_value\"", "style=\"color: #$color_value\"");
    }

    $to_search[] = '/href=\"custom\.css\"/';
    $to_replace[] = 'href="../css/custom.css"';
    $output_contents = file_get_contents($output_file);
    $output_contents = preg_replace($to_search, $to_replace, $output_contents);

    file_put_contents($output_file, $output_contents);

    // Minify CSS
    $cmd = "minify \"$custom_css\" -o \"$base_dir/../css/custom.css\"";
    system($cmd, $ret_val);
    if ($ret_val != 0) {
        echo "Could not minify custom.css";
        exit(1);
    }
    // Minify HTML
    $cmd = "minify \"$output_file\" -o \"$pwd/index.min.html\" && mv \"$pwd/index.min.html\" \"$output_file\"";
    system($cmd, $ret_val);
    if ($ret_val != 0) {
        echo "Could not minify index.html";
        exit(1);
    }
    // Replace custom.css with ../css/custom.css
}

/**
 * Recursively parse all the \input command and gather all the included tex files from main.tex.
 *
 * @param string[] $tex_files Relative links to the TeX files
 */
function collect_tex_files(array &$tex_files, string $base_dir = null, string $tex_file = null) {
    if ($tex_file == null) {
        $base_dir = getcwd() . '/' . BASE_DIR;
        $tex_file = MAIN_TEX;
    }
    if (!file_exists($base_dir . '/' . $tex_file)) {
        echo "File $tex_file does not exist!";
        return;
    }
    $tex_files[] = $tex_file;
    $contents = file_get_contents($base_dir . '/' . $tex_file);
    preg_match_all('/\\\input\{(?<tex_file>[^\}]+)\}/', $contents, $matches);
    foreach ($matches['tex_file'] as $t) {
        collect_tex_files($tex_files, $base_dir, $t);
    }
}

/**
 * Parse the given TeX file and return the parsed contents as a key-value pair.
 */
function get_tex_contents_assoc(string $tex_file): array {
    $tex_file_contents = file_get_contents($tex_file);

    // Get all the titles
    preg_match_all('/((?<=section{)|(?<=subsection{)|(?<=subsubsection{)|(?<=chapter{)|(?<=caption{)|(?<=paragraph{))(?<raw_title>.*)(?<=%%##)(?<key>.*)(?=>>)/', $tex_file_contents, $matches);
    // Get titles from the raw titles
    $title_values = array();
    foreach ($matches['raw_title'] as $raw_title) {
        $c = 0; // number of extra {
        $len = strlen($raw_title);
        for ($i = 0; $i < $len; ++$i) {
            if ($raw_title[$i] == '{') {
                if ($i == 0 || $raw_title[$i - 1] != '\\') {
                    // Unescaped {
                    ++$c; // Increase { counter
                }
            } else if ($raw_title[$i] == '}') {
                if ($i == 0 || $raw_title[$i - 1] != '\\') {
                    // Unescaped }
                    --$c; // Decrease { counter since one match was found
                    if ($c < 0) {
                        // End of the title reached
                        $title_values[] = substr($raw_title, 0, $i);
                        break;
                    }
                }
            }
        }
    }
    // Check keys for verification testing
    foreach ($matches['key'] as $key) {
        if (strlen($key) == 0) {
            echo "Warning: Empty raw title for key $key\n";
            continue;
        }
        if ($key[0] != '$') {
            echo "Warning: First letter of the title is not `$` (key: $key)\n";
        }
        if (!preg_match('/^\$[a-zA-Z0-9-_.]+$/', $key)) {
            echo "Warning: Key ($key) didn't match the required Regex\n";
        }
    }
    // Convert to key => value pair
    $titles = array_combine($matches['key'], $title_values);

    // Extract all the contents
    preg_match_all('/(?<=%%!!)(?<key>.*)(?=<<)/', $tex_file_contents, $matches);
    $content_values = array();
    $offset = 0;
    foreach ($matches['key'] as $key) {
        $start_magic = '%%!!' . $key . "<<\n";
        $start_pos = strpos($tex_file_contents, $start_magic, $offset) + strlen($start_magic);
        $end_pos = strpos($tex_file_contents, "\n%%!!>>", $offset);
        $offset = $end_pos + 7;
        $content_values[] = substr($tex_file_contents, $start_pos, $end_pos - $start_pos);
    }
    foreach ($matches['key'] as $key) {
        if (strlen($key) == 0) {
            echo "Warning: Empty raw title for key $key\n";
            continue;
        }
        if (!preg_match('/^[a-zA-Z0-9-_.]+$/', $key)) {
            echo "Warning: Key ($key) didn't match the required Regex\n";
        }
    }
    $contents = array_combine($matches['key'], $content_values);

    return array_merge($titles, $contents);
}

/**
 * Update base strings.xml
 */
function rebase_strings() {
    $base_dir = getcwd() . '/' . BASE_DIR;
    $strings_file = $base_dir . '/' . STRINGS_XML;
    $tex_files = array();
    // Gather all the tex files
    collect_tex_files($tex_files);
    $xml = new XMLWriter();
    $xml->openUri($strings_file);
    $xml->setIndent(true);
    $xml->setIndentString('    ');
    $xml->startDocument('1.0', 'utf-8');
    $xml->writeComment('This file is auto-generated by ./scripts/docs.php. DO NOT EDIT THIS FILE.');
    $xml->startElement('resources');
    $xml->writeAttribute('xmlns:xliff', 'urn:oasis:names:tc:xliff:document:1.2');
    foreach ($tex_files as $tex_file) {
        $contents = get_tex_contents_assoc($base_dir . '/' . $tex_file);
        // Replace `/` and `.tex` with `$`
        $key_prefix = preg_replace(['/\//', '/\.tex$/'], '$', $tex_file);
        foreach ($contents as $key => $val) {
            $xml->startElement('string');
            $xml->writeAttribute('name', $key_prefix . $key);
            $xml->writeRaw(android_escape_slash_newline(ltrim($val)));
            $xml->endElement(); // string
        }
    }
    $xml->endElement(); // resources
}

/**
 * Update translation from strings.xml for the given language. It replaces the strings available in the strings.xml and
 * then rebuilds the HTML file.
 *
 * @param string $lang Target language e.g. en, ru, ja, etc.
 */
function update_translations(string $lang) {
    $pwd = RAW_DIR . '/' . $lang;
    $strings_file = $pwd . '/' . STRINGS_XML;
    $base_dir = getcwd() . '/' . BASE_DIR;
    // Read strings.xml: Get tex file and key
    $dom = new DOMDocument();
    $dom->loadXML(file_get_contents($strings_file));
    $string_nodes = $dom->getElementsByTagName('string');
    $strings = array();  // tex_file => [ key => value ]
    foreach ($string_nodes as $node) {
        $raw_key = $node->getAttribute('name');
        $pos = strrpos($raw_key, '$');
        if ($raw_key[$pos - 1] == '$') --$pos; // This $ was part of the title
        $tex_file = str_replace('$', '/', substr($raw_key, 0, $pos) . '.tex');
        $key = substr($raw_key, $pos + 1);
        if (strlen($tex_file) == 0 || strlen($key) == 0) {
            echo "Invalid TeX filename or key (raw: $raw_key)\n";
            exit(1);
        }
        if (!isset($strings[$tex_file])) $strings[$tex_file] = array();
        $strings[$tex_file][$key] = get_trimmed_content($node->textContent);
    }
    // Gather all the tex files
    $tex_files = array();
    collect_tex_files($tex_files);
    foreach ($tex_files as $tex_file) {
        $target_path = $pwd . '/' . $tex_file;
        // Create directories if not exists
        $dir = substr($target_path, 0, strrpos($target_path, '/'));
        if (!is_dir($dir)) {
            if (file_exists($dir)) unlink($dir);
            if (!mkdir($dir, 0777, true)) {
                echo "Error: Could not create $dir\n";
                exit(1);
            }
        }
        if (isset($strings[$tex_file])) { // Matched a tex file
            $contents = $strings[$tex_file];
            $tex_file_contents = file_get_contents($base_dir . '/' . $tex_file);
            foreach ($contents as $key => $val) {
                if (strlen($key) == 0) {
                    echo "Warning: Empty key (file: $tex_file)";
                    continue;
                }
                if ($key[0] == '$') {
                    // Fetch raw title and its offset
                    $magic = preg_quote('%%##' . $key . '>>', '/');
                    preg_match('/((?<=section{)|(?<=subsection{)|(?<=subsubsection{)|(?<=chapter{)|(?<=caption{)|(?<=paragraph{))(?<raw_title>.*)'. $magic .'/', $tex_file_contents, $matches, PREG_OFFSET_CAPTURE);
                    if (!isset($matches['raw_title'])) {
                        echo "Warning: Could not find magic $magic in $tex_file\n";
                        continue;
                    }
                    // Sanitize raw title to real title
                    $raw_title = $matches['raw_title'][0];
                    $start_pos = $matches['raw_title'][1];
                    $title = null;
                    $c = 0; // number of extra {
                    $len = strlen($raw_title);
                    for ($i = 0; $i < $len; ++$i) {
                        if ($raw_title[$i] == '{') {
                            if ($i == 0 || $raw_title[$i - 1] != '\\') {
                                // Unescaped {
                                ++$c; // Increase { counter
                            }
                        } else if ($raw_title[$i] == '}') {
                            if ($i == 0 || $raw_title[$i - 1] != '\\') {
                                // Unescaped }
                                --$c; // Decrease { counter since one match was found
                                if ($c < 0) {
                                    // End of the title reached
                                    $title = substr($raw_title, 0, $i);
                                    break;
                                }
                            }
                        }
                    }
                    // Replace title with translated title
                    $tex_file_contents = substr_replace($tex_file_contents, $val, $start_pos, strlen($title));
                    continue;
                }
                // Replace TeX contents
                $start_magic = '%%!!' . $key . "<<\n";
                $start_pos = strpos($tex_file_contents, $start_magic);
                if ($start_pos === false) {
                    echo "Warning: Key not found (file: $tex_file, key: $key)\n";
                    continue;
                }
                $start_pos += strlen($start_magic);
                $end_pos = strpos($tex_file_contents, "\n%%!!>>", $start_pos);
                $tex_file_contents = substr_replace($tex_file_contents, $val, $start_pos, $end_pos - $start_pos);
            }
            // Store the files in pwd
            file_put_contents($pwd . '/' . $tex_file, $tex_file_contents);
        } else { // Didn't match any translation
            // Simply copy the file
            copy($base_dir . '/' . $tex_file, $pwd . '/' . $tex_file);
        }
    }
    // Build HTML
    build_html($lang);
    // Delete all except index.html and strings.xml
    $skip_delete = [OUTPUT_FILENAME, STRINGS_XML];
    $paths = list_files_recursive($pwd);
    rsort($paths);
    foreach ($paths as $path) {
        if (!in_array($path, $skip_delete)) {
            $full_path = $pwd . '/' . $path;
            if (is_dir($full_path)) rmdir($full_path);
            else unlink($full_path);
        }
    }
}

function deploy() {
    $languages = collect_languages();
    // Rebuild HTML
    foreach ($languages as $language) {
        $output_file = RAW_DIR . '/' . $language . '/' . OUTPUT_FILENAME;
        $strings_file = RAW_DIR . '/' . $language . '/' . STRINGS_XML;
        if (!need_update($output_file, $strings_file)) {
            fprintf(STDERR, "Skipped updating HTML for language $language\n");
            continue;
        }
        if ($language == 'en') {
            // For en, only build HTML
            build_html($language);
        } else {
            update_translations($language);
        }
    }
    if (!is_dir(DIST_DIR)) {
        mkdir(DIST_DIR);
    }
    // Copy HTML files to dist dir
    foreach ($languages as $language) {
        $src_html = RAW_DIR . '/' . $language . '/index.html';
        $dst_dir = DIST_DIR . '/' . $language;
        if (!is_dir($dst_dir)) {
            mkdir($dst_dir);
        }
        copy($src_html, $dst_dir . '/index.html');
    }
    // Copy other files
    // - images
    $dir = RAW_DIR . '/images';
    $files = list_files_recursive($dir);
    mkdir(DIST_DIR . '/images');
    foreach ($files as $file) {
        copy($dir . '/' . $file, DIST_DIR . '/images/' . $file);
    }
    // - css
    $dir = RAW_DIR . '/css';
    $files = list_files_recursive($dir);
    mkdir(DIST_DIR . '/css');
    foreach ($files as $file) {
        copy($dir . '/' . $file, DIST_DIR . '/css/' . $file);
    }
    // Generate index.html
    $js_lang_html = array();
    foreach ($languages as $language) {
        $lang_code = get_IETF_language_tag($language);
        $js_lang_html[] = "  <a href=\"$language/\" onclick=\"setLanguage('$language')\">" . Locale::getDisplayName($lang_code, $lang_code) . "</a>";
    }
    $html_contents = file_get_contents(RAW_DIR . '/index.html');
    $html_contents = str_replace('PLACEHOLDER_LANGUAGES_AS_ARRAY', implode('\', \'', $languages), $html_contents);
    $html_contents = str_replace('<!-- PLACEHOLDER_LANGUAGES_AS_HTML -->', "\n" . implode(" &#x2022;\n", $js_lang_html) . "\n", $html_contents);
    file_put_contents(DIST_DIR . '/index.html', $html_contents);
    // Ignore .DS_Store files
    file_put_contents(DIST_DIR . '/' . '.gitignore', '*.DS_Store');
    // Commit changes
    passthru('cd ' . DIST_DIR . ' && git init && git add -A && git commit && git push -f ' . DIST_REPO . ' master:' . DIST_BRANCH);
    // Delete dist dir
    passthru('rm -rf ' . DIST_DIR);
}

function collect_languages() : array {
    $files = array_diff(list_files(RAW_DIR), array('css', 'images'));
    $languages = array();
    foreach ($files as $file) {
        if (is_dir(RAW_DIR . '/' . $file)) {
            $languages[] = $file;
        }
    }
    return $languages;
}

function need_update(string $html_file, string $strings_file) : bool {
    if (!is_file($html_file) || !is_file($strings_file)) {
        return true;
    }
    $html_time = filemtime($html_file);
    $strings_time = filemtime($strings_file);
    if ($html_time === false || $strings_time === false) {
        return true;
    }
    return $strings_time > $html_time;
}

function get_trimmed_content(string $content) : string {
    $len = strlen($content);
    if ($len > 0 && $content[0] == '"' && $content[$len - 1] == '"') {
        // Remove starting and ending quotations
        $content = substr($content, 1, strlen($content) - 2);
    }
    // Remove all newline literals
    return android_escape_slash_newline_reverse(str_replace("\n", '', $content));
}

function get_IETF_language_tag(string $lang): string {
    if (strpos($lang, '-') === false) return $lang;
    $lang_parts = explode('-', $lang);
    if ($lang_parts[1][0] == 'r') {
        // Skip r
        $lang_parts[1] = substr($lang_parts[1], 1, strlen($lang_parts[1]) - 1);
    }
    if ($lang_parts[1] == 'CN') $lang_parts[1] = 'Hans';
    else if ($lang_parts[1] == 'TW') $lang_parts[1] = 'Hant';
    return implode('-', $lang_parts);
}

function create_transient_tex(string $target_dir, string $ietf_lang = 'en') {
    $am_version = system("grep -m1 versionName ./app/build.gradle | awk -F \\\" '{print $2}'", $ret_val);
    if ($ret_val != 0) {
        echo 'Could not get the versionName from ./app/build.gradle';
        exit(1);
    }
    $fmt = new IntlDateFormatter(
        $ietf_lang,
        IntlDateFormatter::FULL,
        IntlDateFormatter::FULL,
        'UTC',
        IntlDateFormatter::GREGORIAN,
        'd MMMM yyyy'
    );
    $today = $fmt->format(new DateTime());
    $content = <<<EOF
\\newcommand{\\version}{v{$am_version}}
\\ifdefined\\Vanilla\\else
    \\renewcommand{\\today}{{$today}}
\\fi
EOF;

    file_put_contents($target_dir . '/transient.tex', $content);
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
    case 'rebase':
        rebase_strings();
        break;
    case 'update':
        if (!isset($argv[2])) {
            echo 'update <lang>';
            exit(1);
        }
        if ($argv[2] == 'en') {
            // For en, we only rebase string and update HTML (for some reason)
            rebase_strings();
            build_html($argv[2]);
        } else {
            update_translations($argv[2]);
        }
        break;
    case 'deploy':
        deploy();
        break;
    case 'pdf':
        create_transient_tex(BASE_DIR);
        passthru('cd ' . BASE_DIR . ' && pdflatex -shell-escape main_vanilla.tex', $return_code);
        if ($return_code == 0) {
            echo 'Built pdf: ' . BASE_DIR . '/main_vanilla.pdf' . "\n";
        } else {
            // Error
            exit($return_code);
        }
        break;
    case 'debug':
        echo "Nothing to do.";
        break;
}
