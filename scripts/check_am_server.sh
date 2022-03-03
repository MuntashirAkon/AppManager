#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

if ! [[ -f ./app/src/main/assets/am.jar ]]; then
  echo "M"
  exit 0
fi

checksum=
checksum2=
os=$(uname -s)
if [[ "${os}" == "Darwin" ]]; then
  checksum=$(ls -alR ./server/src | shasum -a 256 | awk '{print $1}')
  checksum2=$(ls -alR ./libserver/src | shasum -a 256 | awk '{print $1}')
elif [[ "${os}" == "Linux" ]]; then
  checksum=$(ls -alR ./server/src | sha256sum | awk '{print $1}')
  checksum2=$(ls -alR ./libserver/src | sha256sum | awk '{print $1}')
else
  echo "M"
  exit 0
fi

source ./server/checksum.txt

# shellcheck disable=SC2154
if [[ "${checksum}" != "${old_checksum}" ]] || [[ "${checksum2}" != "${old_checksum2}" ]]; then
  echo "old_checksum=${checksum}" >./server/checksum.txt
  echo "old_checksum2=${checksum2}" >>./server/checksum.txt
  echo "M"
  exit 0
fi

echo "N" # Not modified
exit 0
