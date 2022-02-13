#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

{ [[ $(uname) == Darwin ]] || [[ $(uname) =~ .*BSD.* ]]; } && {
  alias sed="gsed"
  alias grep="ggrep"
}

function check_deps() {
  echo -n "Pandoc: " && ( command -v pandoc || echo "Not found." )
  echo -n "pandoc-crossref: " && ( { command -v pandoc-crossref || ls ./pandoc-crossref; } || echo "Not found." )
  echo -n "minify: " && ( command -v minify || echo "Not found." )
  { [[ $(uname) == Darwin ]] || [[ $(uname) =~ .*BSD.* ]]; } && {
    echo -n "GNU grep: " && ( command -v ggrep || echo "Not found." )
    echo -n "GNU sed: " && ( command -v gsed || echo "Not found." )
  }
  echo -n "urlextract: " && ( command -v urlextract || echo "Not found." )
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
"check") check_deps ;;
"checkabuse") detect_abuse "$@" ;;
esac
