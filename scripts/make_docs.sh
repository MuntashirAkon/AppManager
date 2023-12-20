#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

# Abort on errors
set -e

{ [[ $(uname) == Darwin ]] || [[ $(uname) =~ .*BSD.* ]]; } && {
  alias sed="gsed"
  alias grep="ggrep"
}

# Contains all supported languages except English
SUPPORTED_LANGUAGES=(de es ja ru zh-rCN)

res_folder="docs/src/main/res"
assets_folder="docs/src/main/assets"
docs_folder="docs/raw"

[[ -d "${res_folder}" ]] || mkdir -p "${res_folder}"
[[ -d "${assets_folder}" ]] || mkdir -p "${assets_folder}"
! [[ -e "${assets_folder}/docs" ]] || rm -rf "${assets_folder}/docs"

# Copy base files
raw_folder="${res_folder}/raw"
[[ -d "${raw_folder}" ]] || mkdir -p "${raw_folder}"
cp -a "${docs_folder}/css/"* "${raw_folder}"
cp -a "${docs_folder}/images/"* "${raw_folder}"
cp -a "${docs_folder}/en/index.html" "${raw_folder}/"
sed -i -e 's|src=\.\./images/|src=|' \
 -e 's|href=\.\./css/|href=|' \
 -e 's|data=\.\./images/|data=|'  "${raw_folder}/index.html"

# Copy index html and css files
for lang in "${SUPPORTED_LANGUAGES[@]}"; do
  raw_folder="${res_folder}/raw-${lang}"
  [[ -d "${raw_folder}" ]] || mkdir -p "${raw_folder}"
  cp -a "${docs_folder}/${lang}/index.html" "${raw_folder}/"
  sed -i -e 's|src=\.\./images/|src=|' \
   -e 's|href=\.\./css/|href=|' \
   -e 's|data=\.\./images/|data=|' "${raw_folder}/index.html"
done
