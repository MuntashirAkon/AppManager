#!/usr/bin/env bash

LS_PATCH=$(cat <<EOF
diff --git a/app/build.gradle b/app/build.gradle
index fb4b9e3..af1ee87 100644
--- a/app/build.gradle
+++ b/app/build.gradle
@@ -44,6 +44,10 @@ android {
             minifyEnabled false
             proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
         }
+        preRelease {
+            minifyEnabled false
+            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
+        }
     }
     sourceSets.main {
         jni.srcDirs = []

EOF
)

cd lazysodium-android
if ! grep preRelease app/build.gradle; then
  echo "${LS_PATCH}" | git apply
fi
