#!/usr/bin/env php
<?php
/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// Example usage: php ./scripts/update_trackers.php > ./app/src/main/res/values/trackers.xml

$tracker_info = array();
$j = 1;

$file = file_get_contents('https://reports.exodus-privacy.eu.org/api/trackers');
$trackers_json = json_decode($file, true);
addToTrackerInfo($trackers_json['trackers']);

$i = $j;
$trackers = [
  $i => [
    "id" => $i++,
    "name" => "µ?Custom Activity On Crash",
    "code_signature" => "cat.ereza.customactivityoncrash",
    "website" => "https://github.com/Ereza/CustomActivityOnCrash",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "µ?ACRA",
    "code_signature" => "org.acra.|ch.acra.",
    "website" => "https://github.com/ACRA/acra",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "µ?Acrarium",
    "code_signature" => "com.faendir.acra.",
    "website" => "https://github.com/F43nd1r/Acrarium",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "µ?CleanInsights",
    "code_signature" => "io.cleaninsights.sdk.piwik.",
    "website" => "https://github.com/cleaninsights/cleaninsights-android-sdk/blob/master/cleaninsights-piwik-sdk/src/main/java/io/cleaninsights/sdk/CleanInsights.java",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°BugClipper",
    "code_signature" => "com.bugclipper.android.",
    "website" => "https://bugclipper.com/sdk-doc/android",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°?Buglife",
    "code_signature" => "com.buglife.sdk.",
    "website" => "https://buglife.com/docs/android",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°?Flipper",
    "code_signature" => "com.facebook.flipper.",
    "website" => "https://github.com/facebook/flipper/android",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°?Rollbar",
    "code_signature" => "com.rollbar.android.",
    "website" => "https://docs.rollbar.com/docs/android",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°?Critic",
    "code_signature" => "io.inventiv.critic.",
    "website" => "https://github.com/inventivtools/inventiv-critic-android",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°?Raygun",
    "code_signature" => "com.mindscapehq.android.raygun4android",
    "website" => "https://raygun.com/documentation/language-guides/android/installation",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°?Game Sparks",
    "code_signature" => "com.gamesparks.sdk.",
    "website" => "https://docs.gamesparks.com/sdk-center/android.html",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°?PlayFab java",
    "code_signature" => "com.playfab.PlayFab",
    "website" => "https://api.playfab.com/docs/getting-started/java-getting-started",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°Skillz",
    "code_signature" => "com.skillz.Skillz",
    "website" => "https://skillz.zendesk.com/hc/en-us/articles/208579286-Android-Core-Implementation",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°Woopra",
    "code_signature" => "com.woopra.tracking.",
    "website" => "https://github.com/Woopra/woopra-android-sdk",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°Askingpoint",
    "code_signature" => "com.askingpoint.",
    "website" => "https://www.askingpoint.com/blog/document/add-sdk-to-project",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°Databox",
    "code_signature" => "com.databox.",
    "website" => "https://github.com/databox/databox-java",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°Teliver",
    "code_signature" => "com.teliver.sdk.",
    "website" => "https://www.teliver.io",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°AppLink.io",
    "code_signature" => "io.applink.applinkio.AppLinkIO",
    "website" => "https://support.applink.io/hc/en-us/articles/360021172012-Getting-Started-with-the-React-Native-SDK",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°mTraction",
    "code_signature" => "com.mtraction.mtractioninapptracker.",
    "website" => "http://docs.mtraction.com/index.php/docs/integrations/android-unity-integration",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°Pulsate",
    "code_signature" => "com.pulsatehq.",
    "website" => "https://pulsate.readme.io/v2.0/docs/installing-sdk-android-studio",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°SAS",
    "code_signature" => "com.sas.mkt.mobile.sdk.",
    "website" => "https://communities.sas.com/t5/SAS-Communities-Library/Building-a-SAS-CI-enabled-mobile-app-for-Android/ta-p/354922",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°Backendless",
    "code_signature" => "com.backendless.",
    "website" => "https://backendless.com/docs/android/quick_start_guide.html",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°froger_mcs",
    "code_signature" => "com.frogermcs.activityframemetrics",
    "website" => "http://frogermcs.github.io/FrameMetrics-realtime-app-smoothness-tracking",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°FlowUp",
    "code_signature" => "io.flowup",
    "website" => "https://github.com/flowupio/FlowUpAndroidSDK",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°Parse",
    "code_signature" => "com.parse.Parse",
    "website" => "https://docs.parseplatform.org/android/guide/#analytics",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°RJFUN",
    "code_signature" => "com.rjfun.cordova.ad",
    "website" => "http://www.rjfun.com/w/index.php/en",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°AWS Kinesis",
    "code_signature" => "com.amazonaws.metrics.|com.amazonaws.util.AWSRequestMetrics.",
    "website" => "https://github.com/aws-amplify/aws-sdk-android",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°BugSense",
    "code_signature" => "com.bugsense.trace.",
    "website" => "https://github.com/bugsense/docs/blob/master/android.md",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "°?DebugDrawer",
    "code_signature" => "io.palaima.debugdrawer.",
    "website" => "https://github.com/palaima/DebugDrawer",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "Demdex",
    "code_signature" => "com.adobe.mobile.Config.",
    "website" => "https://www.adobe.com/data-analytics-cloud/audience-manager.html",
    "network_signature" => "demdex\.net"
  ],
  $i => [
    "id" => $i++,
    "name" => "S4M",
    "code_signature" => ".S4MAnalytic",
    "website" => "http://www.s4m.io",
    "network_signature" => "s4m\.io|sam4m\.com"
  ],
  $i => [
    "id" => $i++,
    "name" => "Instabug",
    "code_signature" => "com.instabug.library.Instabug",
    "website" => "https://instabug.com/crash-reporting",
    "network_signature" => ""
  ],
  $i => [
    "id" => $i++,
    "name" => "Factual",
    "code_signature" => "com.factual.Factual",
    "website" => "https://www.factual.com",
    "network_signature" => ""
  ],
];
addToTrackerInfo($trackers);

// Add from ETIP
$file = file_get_contents('https://etip.exodus-privacy.eu.org/trackers/export');
$trackers_json = json_decode($file, true);
addToTrackerInfo($trackers_json['trackers'], '²');

// printf_oF2pks();
printf_AM();

// === Functions === //

function printf_oF2pks() {
  global $tracker_info;
  $s = "____";
  foreach($tracker_info as $tracker => $info) {
    foreach($info["code_sigs"] as $sig) {
      echo $tracker.$s.$sig.$s.$info['website'].$s.$info['net_sigs'].$s.$info['id']."\n";
    }
  }
}

function printf_AM() {
  global $tracker_info;
  echo <<<EOF
<?xml version="1.0" encoding="utf-8"?>
<!--
  ~ Copyright (C) 2020 Muntashir Al-Islam
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program.  If not, see <https://www.gnu.org/licenses/>.
  -->

<resources>
    <string-array name="tracker_signatures">

EOF;

  foreach($tracker_info as $tracker => $info) {
    foreach($info["code_sigs"] as $sig) {
      echo "        <item>${sig}</item>\n";
    }
  }

  echo <<<EOF
</string-array>
<string-array name="tracker_names">

EOF;

  foreach($tracker_info as $tracker => $info) {
    foreach($info["code_sigs"] as $sig) {
      echo "        <item>${tracker}</item>\n";
    }
  }

  echo <<<EOF
    </string-array>
</resources>
EOF;
}

function addToTrackerInfo($trackers, $prefix='') {
  global $tracker_info, $j;
  foreach($trackers as $id => $tracker) {
    $tmp = $tracker['code_signature'];
    $nc = $tracker['network_signature'];
    $name = $tracker['name'];
    if (empty($nc)) $nc = "NC";
    if (!empty($tmp)) { // We only need the ones with signatures
      if (isset($tracker_info[$name])) {
        // Tracker exists, update
        $arr = explode("|", $tmp);
        foreach ($arr as $sig) {
          if (!in_array($sig, $tracker_info[$name]["code_sigs"])) {
            array_push($tracker_info[$name]["code_sigs"], $sig);
          }
        }
      } else {
        // Check with prefix
        $name = $prefix.$name;
        if (isset($tracker_info[$name])) {
          // Tracker exists, update
          $arr = explode("|", $tmp);
          foreach ($arr as $sig) {
            if (!in_array($sig, $tracker_info[$name]["code_sigs"])) {
              array_push($tracker_info[$name]["code_sigs"], $sig);
            }
          }
        } else {
          // Add new
          $tracker_info[$name] = [
            "code_sigs" => explode("|", $tmp),
            "net_sigs" => $nc,
            "website" => $tracker['website'],
            "id" => $j++,
          ];
        }
      }
    } // else contains no signature
  }
}
