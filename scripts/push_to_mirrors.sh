#!/usr/bin/env sh
# SPDX-License-Identifier: GPL-3.0-or-later

if ! git remote get-url gitlab; then
    git remote add --mirror=push gitlab git@gitlab.com:muntashir/AppManager.git
fi

if ! git remote get-url riseup; then
    git remote add --mirror=push riseup git@0xacab.org:muntashir/AppManager.git
fi

if ! git remote get-url codeberg; then
    git remote add --mirror=push codeberg git@codeberg.org:muntashir/AppManager.git
fi

if ! git remote get-url sourcehut; then
    git remote add --mirror=push sourcehut git@git.sr.ht:~muntashir/AppManager
fi

git push gitlab
git push riseup
git push codeberg
git push sourcehut
