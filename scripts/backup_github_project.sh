#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later

GITHUB_USERNAME="MuntashirAkon"
REPOSITORY_NAME="AppManager"

if [[ -z "$GITHUB_TOKEN" ]]; then
  if [[ -f $(which secret) ]]; then
    echo "Using secret (https://github.com/angt/secret) for secrets...";
    GITHUB_TOKEN=$(secret show app_manager_backup)
  else
    echo "Error: GITHUB_TOKEN environment variable is not set."
    exit 1
  fi
fi

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <backup-directory>"
  exit 1
fi

BACKUP_DIRECTORY="$1"

github-backup "$GITHUB_USERNAME" \
  --token "$GITHUB_TOKEN" \
  --repository "$REPOSITORY_NAME" \
  --issues \
  --issue-comments \
  --issue-events \
  --pulls \
  --pull-comments \
  --pull-commits \
  --pull-details \
  --milestones \
  --releases \
  --assets \
  --labels \
  --skip-existing \
  --output-directory "$BACKUP_DIRECTORY"
