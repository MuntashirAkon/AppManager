#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

{ [[ $(uname) == Darwin ]] || [[ $(uname) =~ .*BSD.* ]]; } && {
  alias sed="gsed"
  alias grep="ggrep"
  alias awk="gawk"
}

function check_deps() {
  echo -n "Pandoc: " && ( command -v pandoc || echo "Not found." )
  echo -n "pandoc-crossref: " && ( { command -v pandoc-crossref || ls ./pandoc-crossref; } || echo "Not found." )
  echo -n "Python: " && ( command -v python || echo "Not found." )
  echo -n "xmllint: " && ( command -v xmllint || echo "Not found." )
  echo -n "Perl: " && ( command -v perl || echo "Not found." )
  { [[ $(uname) == Darwin ]] || [[ $(uname) =~ .*BSD.* ]]; } && {
    echo -n "GNU awk: " && ( command -v gawk || echo "Not found." )
    echo -n "GNU grep: " && ( command -v ggrep || echo "Not found." )
    echo -n "GNU sed: " && ( command -v gsed || echo "Not found." )
  }
  echo -n "urlextract: " && ( command -v urlextract || echo "Not found." )
}

function merge_translation() {
  INPUT="$2"
  OUTPUT_DIR="$3"
  [ -z "$2" ] && { echo "INPUT.xml not specified."; exit 0; }
  [ -z "$3" ] && { echo "Output directory not specified."; exit 0; }

  keys=$(grep -oP "(?<=<string name=\").*?(?=\">)" "${INPUT}")

  find . | grep -e '\.tex$' -e '.png$' -e '.svg$' -e doctool.sh | rsync -R $(cat) "${OUTPUT_DIR}"

  while read -r key_content; do
    content_value=$(echo 'cat resources/string[@name="'${key_content}'"]/text()' | xmllint --shell "${INPUT}" | sed -e '$d' -e '1d' | sed -e 's/\\/\\\\/g' | sed -e "s/\&amp;/\&/g" -e "s/\&lt;/</g" -e "s/\&gt;/>/g")
    file=$(grep -rl --include="*.tex" "\%\%!!${key_content}<<" "${OUTPUT_DIR}")
    source=$(cat "${file}")
    echo "import re;import sys;print(re.sub(r'(?<=%%!!"${key_content}"<<\n)[^%%!!>>]*(?=%%!!>>)', sys.argv[2]+'\n', sys.argv[1], flags=re.M))" | python - "${source}" "${content_value}" >"${file}"
  done < <(echo "$keys" | grep -Pv ".*(?===title)")

  while read -r key_title; do
    title_value=$(echo 'cat resources/string[@name="'${key_title}'"]/text()' | xmllint --shell "${INPUT}" | sed -e '$d' -e '1d' | sed -e 's/\\/\\\\/g' -e 's/\//\\\//g' | sed -e "s/\&amp;/\&/g" -e "s/\&lt;/</g" -e "s/\&gt;/>/g")
    file=$(grep -rl --include="*.tex" "\%\%##${key_title}>>" "${OUTPUT_DIR}")
    nests=0
    while true; do
      chknests=$(grep -oP '((?<=section{)|(?<=subsection{)|(?<=subsubsection{)|(?<=chapter{)|(?<=caption{)|(?<=paragraph{))([^}]*}){'$nests'}[^}]*(?=}.*\%\%\#\#${key_title}>>)')
      open=$(echo "$chknests" | grep -o "{" | wc -l)
      close=$(echo "$chknests" | grep -o "}" | wc -l)
      [[ "$open" == "$close" ]] && break
      [[ "$open" -gt "$close" ]] && nests=$((nests + 1))
    done
    perl -i -e "s/(section\{|subsection\{|subsubsection\{|chapter\{|caption\{|paragraph\{)$chknests(\}.*\%\%\#\#${key_title}>>)/\1${title_value}\2/" ${file}
  done < <(echo "$keys" | grep -P ".*(?===title)")
}

function detect_abuse() {
  baseDir=.
  compareDir="$2"

  [ -z "$2" ] && { echo "Compare directory not specified."; exit 0; }

  # Compare number of latex tags

  # Check URL changes
  baseFiles=$(find "$baseDir" | grep -e '\.tex$' | sed -e "s%$baseDir%%g" -e "s/^\///g")
  compareFiles=$(find "$compareDir" | grep -e '\.tex$' | sed -e "s%$compareDir%%g" -e "s/^\///g")

  while read -r test; do
    { echo "$compareFiles" | grep "$test" >/dev/null; } && {
      base=$(urlextract "$baseDir/$test")
      compare=$(urlextract "$compareDir/$test")

      echo "$compare" | grep -vh "$base"
      echo -n "--\n\n"
      echo "WARNING: $baseDir/$test(BASE) does not have these URLs, but $compareDir/$test(COMPARE) has. These links has possibility of spam!"
      echo -n "--\n\n"
    }
  done < <(echo "$baseFiles")
}

case $1 in
"merge") merge_translation "$@" ;;
"check") check_deps ;;
"checkabuse") detect_abuse "$@" ;;
esac
