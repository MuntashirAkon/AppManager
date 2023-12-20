#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -e

{ [[ $(uname) == Darwin ]] || [[ $(uname) =~ .*BSD.* ]]; } && {
  alias sed="gsed"
  alias grep="ggrep"
}

if [[ "$#" -lt 1 ]]; then
  echo "USAGE: RELEASE_TYPE [BUILD_AAB]"
  exit 1
fi

if ! which bundletool >/dev/null 2>&1; then
  echo "Bundletool doesn't exist in path"
  exit 1
fi

RELEASE_TYPE=$1
BUILD_AAB=true
if [[ "$2" == "false" ]] || [[ "$2" == "0" ]]; then
  BUILD_AAB=false
fi

APP_VERSION="v$(grep -m1 versionName ./app/build.gradle | awk -F \" '{print $2}')"
APP_NAME="AppManager_${APP_VERSION}"
DEFAULT_NAME="app-${RELEASE_TYPE}"

RELEASE_PATH="app/build/outputs/bundle/${RELEASE_TYPE}"
TMP_PATH="tmp"

AAB_PATH="${RELEASE_PATH}/${DEFAULT_NAME}.aab"
APKS_PATH="${RELEASE_PATH}/${DEFAULT_NAME}.apks"
APK_PATH="${RELEASE_PATH}/${DEFAULT_NAME}-universal.apks"

SUPPORTED_LANGUAGES=(ar de en es fa fr hi id it ja ko nb pl pt ro ru tr uk vi zh)
SUPPORTED_DPIS=(ldpi mdpi tvdpi hdpi xhdpi xxhdpi xxxhdpi)
SUPPORTED_ARCHS=(armeabi_v7a arm64_v8a x86 x86_64)

#KEYSTORE=
#KEY_ALIAS=
#KEYSTORE_PASS=
#KEY_ALIAS_PASS=

if [[ "${KEYSTORE}" == "" ]]; then
  read -rp "KeyStore file: " KEYSTORE
  echo
  read -rsp "KeyStore pass: " KEYSTORE_PASS
  echo
  read -rp "Key alias: " KEY_ALIAS
  echo
  read -rsp "Key alias pass: " KEY_ALIAS_PASS
  echo
fi

if [[ "$BUILD_AAB" == true ]]; then
  if [[ -f "${AAB_PATH}" ]]; then
    rm "${AAB_PATH}"
  fi
  ./gradlew "bundle$(tr '[:lower:]' '[:upper:]' <<<"${RELEASE_TYPE:0:1}")${RELEASE_TYPE:1}"
fi

if [[ -f ${AAB_PATH} ]]; then
  bundletool build-apks --overwrite --mode=universal --bundle="${AAB_PATH}" --output="${APK_PATH}" --ks="${KEYSTORE}" --ks-pass=pass:"${KEYSTORE_PASS}" --ks-key-alias="${KEY_ALIAS}" --key-pass=pass:"${KEY_ALIAS_PASS}"
  bundletool build-apks --overwrite --mode=default --bundle="${AAB_PATH}" --output="${APKS_PATH}" --ks="${KEYSTORE}" --ks-pass=pass:"${KEYSTORE_PASS}" --ks-key-alias="${KEY_ALIAS}" --key-pass=pass:"${KEY_ALIAS_PASS}"
else
  echo "$AAB_PATH doesn't exist"
  exit 1
fi

# Unzip output APKS file
if [[ -f ${APKS_PATH} ]]; then
  unzip "${APKS_PATH}" -d "${RELEASE_PATH}"/${TMP_PATH}
  rm "${APKS_PATH}"
else
  echo "$APKS_PATH doesn't exist"
  exit 1
fi

lastPWD=$(pwd)
cd "${RELEASE_PATH}"/${TMP_PATH}/splits
# Move required files
mv base-master.apk base.apk
for lang in "${SUPPORTED_LANGUAGES[@]}"; do
  mv "base-${lang}.apk" "config.${lang}.apk"
done
for dpi in "${SUPPORTED_DPIS[@]}"; do
  mv "base-${dpi}.apk" "config.${dpi}.apk"
done
for arch in "${SUPPORTED_ARCHS[@]}"; do
  if [ -f "base-${arch}.apk" ]; then
    mv "base-${arch}.apk" "config.${arch}.apk"
  else
    echo 2>&1 "base-${arch}.apk: not found."
  fi
done
# Delete rests
rm ./base-*
# Make zip
cd "${lastPWD}"
zip -j "${RELEASE_PATH}/${APP_NAME}.apks" "${RELEASE_PATH}/${TMP_PATH}/splits"/*
rm -rf "${RELEASE_PATH:?}/${TMP_PATH}"

# Unzip universal APKS file
if [[ -f ${APK_PATH} ]]; then
  unzip "${APK_PATH}" -d "${RELEASE_PATH}"/${TMP_PATH}
  rm "${APK_PATH}"
else
  echo "$APK_PATH doesn't exist"
  exit 1
fi
mv "${RELEASE_PATH}/${TMP_PATH}/universal.apk" "${RELEASE_PATH}/${APP_NAME}.apk"
touch "${RELEASE_PATH}/${APP_NAME}.apk"
rm -rf "${RELEASE_PATH:?}/${TMP_PATH}"

echo "Output generated at $(pwd)/${RELEASE_PATH}"
