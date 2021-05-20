#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

# Abort on errors
set -e

config_js=docs/.vuepress/config.js

# Set base dir
sed -i "s|  base: '.*',|  base: '/AppManager/',|" "${config_js}"

# Install dependencies
yarn install

# Build docs
yarn build

base_dist=docs/.vuepress/dist
pages_repo="git@github.com:MuntashirAkon/AppManager.git"
pages_branch="pages"

# Navigate to the build output directory
cd "${base_dist}"

# Ignore .DS_Store files
echo "*.DS_Store" >.gitignore

# Commit changes
git init
git add -A
git commit

# Reset upstream git and push the commit as the first commit
git push -f $pages_repo master:$pages_branch

cd -

# Delete the output directory
rm -rf "${base_dist}"
