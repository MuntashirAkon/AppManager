#!/usr/bin/env sh

# Abort on errors
set -e

# Build
yarn build

base_dist=docs/.vuepress/dist
pages_repo="git@github.com:MuntashirAkon/AppManager.git"
pages_branch="pages"

# Navigate to the build output directory
cd "${base_dist}"

# Ignore .DS_Store files
echo "*.DS_Store" > .gitignore

# Commit changes
git init
git add -A
git commit

# Reset upstream git and push the commit as the first commit
git push -f $pages_repo master:$pages_branch

cd -

# Delete the output directory
rm -rf "${base_dist}"

