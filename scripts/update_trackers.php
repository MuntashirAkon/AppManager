#!/usr/bin/env php
<?php
/* SPDX-License-Identifier: GPL-3.0-or-later*/

// Example usage: php ./scripts/update_trackers.php > ./app/src/main/res/values/trackers.xml

$tracker_info = array();
$j = 1;

// Add from Reports
$file = file_get_contents('https://reports.exodus-privacy.eu.org/api/trackers');
$trackers_json = json_decode($file, true);
addToTrackerInfo($trackers_json['trackers']);

// Add from ETIP
$file = file_get_contents('https://etip.exodus-privacy.eu.org/trackers/export');
$trackers_json = json_decode($file, true);
addToTrackerInfo($trackers_json['trackers'], '²');

// Add from custom list (lowest priority)
$file = file_get_contents('./scripts/tmp_trackers.json');
$trackers_json = json_decode($file, true);
addToTrackerInfo($trackers_json['trackers']);

printf_AM();

// === Functions === //

function printf_AM(): void {
  global $tracker_info;
  echo <<<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<resources>
    <string-array name="tracker_signatures">

EOF;

  foreach($tracker_info as $info) {
    foreach($info["code_sigs"] as $sig) {
      echo "        <item>$sig</item>\n";
    }
  }

  echo <<<EOF
    </string-array>
    <string-array name="tracker_names">

EOF;

  foreach($tracker_info as $tracker => $info) {
    foreach($info["code_sigs"] as $ignored) {
      echo "        <item>$tracker</item>\n";
    }
  }

  echo <<<EOF
    </string-array>
</resources>
EOF;
}

function addToTrackerInfo($trackers, $prefix=''): void {
  global $tracker_info, $j;
  foreach($trackers as $tracker) {
    $tmp = str_replace('\\', '', $tracker['code_signature']);
    $nc = $tracker['network_signature'];
    $name = $tracker['name'];
    if (empty($nc)) $nc = "NC";
    if (!empty($tmp)) { // We only need the ones with signatures
      if (isset($tracker_info[$name])) {
        // Tracker exists, update
        $arr = explode("|", $tmp);
        foreach ($arr as $sig) {
          if (!in_array($sig, $tracker_info[$name]["code_sigs"])) {
            $tracker_info[$name]["code_sigs"][] = $sig;
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
              $tracker_info[$name]["code_sigs"][] = $sig;
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
