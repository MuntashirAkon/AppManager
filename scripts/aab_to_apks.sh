#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -e

if [[ "$#" -lt 1 ]]; then
  echo "USAGE: [RELEASE_TYPE]"
  exit 1
fi

if ! which bundletool >/dev/null 2>&1; then
  echo "Bundletool doesn't exist in path"
  exit 1
fi

RELEASE_TYPE=$1
if [[ "$RELEASE_TYPE" == "" ]]; then
  RELEASE_TYPE=release
fi
default_name="app-${RELEASE_TYPE}"
RELEASE_PATH=./app/${RELEASE_TYPE}
TMP_PATH=tmp
AAB_PATH=${RELEASE_PATH}/${default_name}.aab
APKS_PATH=${RELEASE_PATH}/${default_name}.apks
APK_PATH=${RELEASE_PATH}/${default_name}-universal.apks
APK_FILE_PATH=${RELEASE_PATH}/${default_name}.apk

SUPPORTED_LANGUAGES=(ar bn de en es fa fr hi ja nb pl pt ru tr uk vi zh)
SUPPORTED_DPIS=(ldpi mdpi tvdpi hdpi xhdpi xxhdpi xxxhdpi)
SUPPORTED_ARCHS=(armeabi_v7a arm64_v8a x86 x86_64)

#KEYSTORE=
#KEY_ALIAS=
#KEYSTORE_PASS=
#KEY_ALIAS_PASS=

source ./scripts/KeyStore.sh

if [[ "${KEYSTORE}" == "" ]]; then
  read -rsp "KeyStore file: " KEYSTORE
  echo
  read -rsp "KeyStore pass: " KEYSTORE_PASS
  echo
  read -rsp "Key alias: " KEY_ALIAS
  echo
  read -rsp "Key alias pass: " KEY_ALIAS_PASS
  echo
fi

if [[ -f ${AAB_PATH} ]]; then
  bundletool build-apks --overwrite --mode=universal --bundle=${AAB_PATH} --output=${APK_PATH} --ks="${KEYSTORE}" --ks-pass=pass:"${KEYSTORE_PASS}" --ks-key-alias="${KEY_ALIAS}" --key-pass=pass:"${KEY_ALIAS_PASS}"
  bundletool build-apks --overwrite --mode=default --bundle=${AAB_PATH} --output=${APKS_PATH} --ks="${KEYSTORE}" --ks-pass=pass:"${KEYSTORE_PASS}" --ks-key-alias="${KEY_ALIAS}" --key-pass=pass:"${KEY_ALIAS_PASS}"
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

lastPWD=$(pwd)
cd ${RELEASE_PATH}/${TMP_PATH}/splits
# Move required files
mv base-master.apk base.apk
for lang in "${SUPPORTED_LANGUAGES[@]}"; do
  mv "base-${lang}.apk" "config.${lang}.apk"
done
for dpi in "${SUPPORTED_DPIS[@]}"; do
  mv "base-${dpi}.apk" "config.${dpi}.apk"
done
for arch in "${SUPPORTED_ARCHS[@]}"; do
  mv "base-${arch}.apk" "config.${arch}.apk"
done
# Delete rests
rm ./base-*
# Make zip
cd "${lastPWD}"
zip -j "${RELEASE_PATH}/${default_name}.apks" "${RELEASE_PATH}/${TMP_PATH}/splits"/*
rm -rf "${RELEASE_PATH:?}/${TMP_PATH}"

# Unzip universal APKS file
if [[ -f ${APK_PATH} ]]; then
  unzip ${APK_PATH} -d ${RELEASE_PATH}/${TMP_PATH}
  rm ${APK_PATH}
else
  echo "$APKS_PATH doesn't exist"
  exit 1
fi
mv "${RELEASE_PATH}/${TMP_PATH}/universal.apk" "${APK_FILE_PATH}"
rm -rf "${RELEASE_PATH:?}/${TMP_PATH}"
