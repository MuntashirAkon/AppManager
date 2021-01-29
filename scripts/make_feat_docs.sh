#!/usr/bin/env bash

# Abort on errors
set -e

config_js=docs/.vuepress/config.js

# Set base dir
sed -i "s|  base: '.*',|  base: '/android_asset/docs/',|" "${config_js}"

# Build
yarn build

assets_folder="feat_docs/src/main/assets"

if ! [[ -d "${assets_folder}" ]]; then
  mkdir -p "${assets_folder}"
fi

if [[ -e "${assets_folder}/docs" ]]; then
  rm -rf "${assets_folder}/docs"
fi

mv docs/.vuepress/dist "${assets_folder}/docs"
