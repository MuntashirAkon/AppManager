#!/bin/bash

INITIAL_HASH="021151c5a2b1cc201461625e286d1ca4531274db"
BRANCH="master"

# Ensure complete history
git fetch --unshallow origin

# Get up to 5 commits after initial in chronological order
commits=$(git rev-list --max-count=5 --reverse "$BRANCH" ^$INITIAL_HASH)

# Create new history from initial commit
git checkout -B temp-branch $INITIAL_HASH

# Batch cherry-pick all eligible commits at once
if [ -n "$commits" ]; then
  git cherry-pick $commits
fi

# Finalize branch update
git branch -f $BRANCH temp-branch
git checkout $BRANCH
git branch -D temp-branch
