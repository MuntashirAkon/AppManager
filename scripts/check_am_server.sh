#!/usr/bin/env bash

if ! [[ -f ./app/src/main/assets/am.jar ]]; then
    echo "M"
    exit 0
fi

checksum=
os=`uname -s`
if [[ "${os}" == "Darwin" ]]; then
    checksum=`ls -alR ./server/src | shasum -a 256 | awk '{print $1}'`
elif [[ "${os}" == "Linux" ]]; then
    checksum=`ls -alR ./server/src | sha256sum | awk '{print $1}'`
else
    echo "M"
    exit 0
fi

old_checksum=`cat ./server/checksum.txt 2>/dev/null`

if [[ "${checksum}" != "${old_checksum}" ]]; then
    echo "${checksum}" > ./server/checksum.txt
    echo "M"
    exit 0
fi

echo "N"  # Not modified
exit 0