#!/usr/bin/env sh
# SPDX-License-Identifier: GPL-3.0-or-later

if ! git push gitlab; then
    git remote add --mirror=push gitlab git@gitlab.com:muntashir/AppManager.git
    git push gitlab
fi

if ! git push riseup; then
    git remote add --mirror=push riseup git@0xacab.org:muntashir/AppManager.git
    git push riseup
fi

if ! git push codeberg; then
    git remote add --mirror=push codeberg git@codeberg.org:muntashir/AppManager.git
    git push codeberg
fi

if ! git push sourcehut; then
    git remote add --mirror=push sourcehut git@git.sr.ht:~muntashir/AppManager
    git push sourcehut
fi
