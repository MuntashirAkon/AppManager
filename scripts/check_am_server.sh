#!/usr/bin/env bash

checksum=
os=`uname -s`
if [[ "${os}" == "Darwin" ]]; then
    checksum=`ls -alR ./AppManagerServer/src | shasum -a 256 | awk '{print $1}'`
elif [[ "${os}" == "Linux" ]]; then
    checksum=`ls -alR ./AppManagerServer/src | sha256sum | awk '{print $1}'`
else
    echo "M"
    exit 0
fi

old_checksum=`cat ./AppManagerServer/checksum.txt 2>/dev/null`

if [[ "${checksum}" != "${old_checksum}" ]]; then
    echo "${checksum}" > ./AppManagerServer/checksum.txt
    echo "M"
    exit 0
fi

echo "N"  # Not modified
exit 0