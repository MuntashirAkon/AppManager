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
