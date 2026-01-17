.. SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0

====================
Building App Manager
====================

Requirements
============

* **Hardware:** Any computer with 8 GB RAM and 20 GB storage
* **Operating system:** Linux/macOS/WSL
* **Software:** Android Studio/IntelliJ IDEA, Gradle, Latex, pandoc, JDK 17+
* **Active network connection:** Depending on your development environment,
  you may need at least 20 GB data package.

macOS
=====

The following steps are required only if you want to build APKS:

- Install Homebrew::

    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

- Install bundletool::

    brew install bundletool

Linux|GNU
=========

- Install the development tools.

  * For Debian/Ubuntu::

      sudo apt-get install build-essential

  * For Fedora/CentOS/RHEL::

      sudo yum groupinstall "Development Tools"

  * For Arch/Artix/Manjaro::

      sudo pacman -S base-devel

- Install `bundletool-all.jar`_ if you want to build APKS, and make sure it is
  available as ``bundletool`` command.  A quick way would be to create an alias
  as follows (assuming you're using ``bash``)::

    echo "alias bundletool='java -jar path/to/bundletool.jar'" >> ~/.bashrc

  Make sure to replace ``/path/to/bundletool-all.jar`` with the actual path for
  **bundletool-all.jar**.

  * For Arch/Artix/Majaro (with ``yay``)::

      yay -S bundletool


Clone and Build App Manager
===========================

1. Clone the repo along with submodules::

     git clone --recurse-submodules https://github.com/MuntashirAkon/AppManager.git

   You can use the `--depth 1` argument if you don't want to clone past
   commits.
2. Open the project **AppManager** using Android Studio/IntelliJ IDEA.  The IDE
   should start syncing automatically.  It will also download all the necessary
   dependencies automatically provided you have a working network connection.
3. Build debug version of App Manager from *Menu* > *Build* > *Make Project*,
   or, from the terminal::

     ./gradlew packageDebugUniversalApk

   The command will generate a universal APK instead of a bundled app.

Create Bundled App
==================

To create a bundled app in APKS format, run the following command::

  ./scripts/aab_to_apks.sh type

Replace ``type`` with ``release`` or ``debug`` based on your requirements.
It will ask for KeyStore credentials interactively.

The script above will also generate a universal APK.

.. _bundletool-all.jar: https://github.com/google/bundletool


Build documentation
===================
See  `docs/raw/en/README.md <docs/raw/en/README.md>`_
