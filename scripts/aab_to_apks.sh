#!/usr/bin/env bash
#
# Copyright (C) 2020 Muntashir Al-Islam
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

set -e

if [[ "$#" -lt 1 ]]; then
    echo "USAGE: [RELEASE_TYPE]"
    exit 1
fi

if ! which bundletool > /dev/null 2>&1; then
    echo "Bundletool doesn't exist in path"
    exit 1
fi

RELEASE_TYPE=$1
if [[ "$RELEASE_TYPE" == "" ]]; then
    RELEASE_TYPE=release
fi
default_name=app-${RELEASE_TYPE}
RELEASE_PATH=./app/${RELEASE_TYPE}
TMP_PATH=tmp
AAB_PATH=${RELEASE_PATH}/${default_name}.aab
APKS_PATH=${RELEASE_PATH}/${default_name}.apks

SUPPORTED_LANGUAGES=(bn de en es fr hi nb pl pt ru tr uk zh)
SUPPORTED_DPIS=(ldpi mdpi tvdpi hdpi xhdpi xxhdpi xxxhdpi)
SUPPORTED_ARCHS=(armeabi_v7a arm64_v8a x86 x86_64)

#KEYSTORE=
#KEY_ALIAS=
#KEYSTORE_PASS=
#KEY_ALIAS_PASS=

source ./scripts/KeyStore.sh

if [[ "${KEYSTORE}" == "" ]]; then
    read -sp "KeyStore file: " KEYSTORE
    echo
    read -sp "KeyStore pass: " KEYSTORE_PASS
    echo
    read -sp "Key alias: " KEY_ALIAS
    echo
    read -sp "Key alias pass: " KEY_ALIAS_PASS
    echo
fi

if [[ -f ${AAB_PATH} ]]; then
    bundletool build-apks --overwrite --mode=default --bundle=${AAB_PATH} --output=${APKS_PATH} --ks=${KEYSTORE} --ks-pass=pass:${KEYSTORE_PASS} --ks-key-alias=${KEY_ALIAS} --key-pass=pass:${KEY_ALIAS_PASS}
else
    echo "$AAB_PATH doesn't exist"
    exit 1
fi

# Unzip output APKS file
if [[ -f ${APKS_PATH} ]]; then
    unzip ${APKS_PATH} -d ${RELEASE_PATH}/${TMP_PATH}
    rm ${APKS_PATH}
else
    echo "$APKS_PATH doesn't exist"
    exit 1
fi

lastPWD=`pwd`
cd ${RELEASE_PATH}/${TMP_PATH}/splits
# Move required files
mv base-master.apk base.apk
for lang in ${SUPPORTED_LANGUAGES[@]}; do
    mv base-${lang}.apk config.${lang}.apk
done
for dpi in ${SUPPORTED_DPIS[@]}; do
    mv base-${dpi}.apk config.${dpi}.apk
done
for arch in ${SUPPORTED_ARCHS[@]}; do
    mv base-${arch}.apk config.${arch}.apk
done
# Delete rests
rm ./base-*
# Make zip
cd ${lastPWD}
zip -j ${RELEASE_PATH}/${default_name}.apks ${RELEASE_PATH}/${TMP_PATH}/splits/*
rm -rf ${RELEASE_PATH}/${TMP_PATH}
