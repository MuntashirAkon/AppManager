.. SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0

============
Contributing
============

You are welcome contribute to App Manager!  This doesn't mean that you need
coding skills.  You can contribute to App Manager by creating helpful issues,
attending discussions, improving documentations and translations, making icon
for icon packs, adding unrecognised libraries or ad/tracking signatures,
reviewing the source code, as well as reporting security vulnerabilities.

Rules
=====

- If you are going to implement or work on any specific feature, please inform
  us before doing so. Due to the complex nature of the project, integrating a
  new feature could be challenging.
- Your contributions are licensed under ``GPL-3.0-or-later`` by default.
  Please see related `Linux documentations`_ to see how to add license headers
  to a file, and remember the following:

  * If the files your are contributing to do not have ``GPL-3.0-or-later``, add
    it to the existing ``SPDX-License-Identifier`` using ``AND``, e.g.  ::

        SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

  * If the entire file or Java class is copied from another person or project,
    you have to add a copyright statement adding the person who wrote it first
    like this::

        // Copyright 2004 Linus Torvalds

    You can also add other contributors but they are not mandatory.  You do not
    need to include your name because it can already be available via the
    version control system.
  * Do not add the **@author** tag as it is considered a bad practice.

- You have to sign-off your work.  You can do that using the ``--signoff``
  argument.  If you are not using command line or a software that does not
  support it, you can add the following line at the end of your commit
  message::

    Signed-off-by: My Name <my.name@example.com>

  We also support most of the `commit message conventions`_ from Linux.

  App Manager is a legal software and its contributions are protected by
  copyright laws. Consider using real credentials ie. real name and email as
  we may be required to delete your valuable contributions in the event of
  introducing new license or adding exceptions to the existing license.

**Note:** Repositories located in sites other than GitHub are currently
considered mirrors and any pull or merge requests submitted there will not be
accepted.  Instead, you can submit patches (as ``.patch`` files) via email
attachment.  My email address is am4android [at] riseup [dot] net.  Beware
that such emails may be publicly accessible in future.  GitHub pull requests
will be merged manually using the corresponding patches.  As a result, GitHub
may falsely mark them *closed* instead of *merged*.

**Warning.** Every commit made by other users are thoroughly examined with the
exception of commits made through Weblate.  So, if it is found that you are
abusing the Weblate platform, you will be blocked on Weblate without a warning,
and ALL your contributions to this project shall be removed.  This is a hobby
project, and like any hobby, I want to make things neat and clean.  Existing
contributors are also encouraged to report any abuse.  Your identity shall be
kept secret.

.. _Linux documentations: https://github.com/torvalds/linux/blob/master/Documentation/process/license-rules.rst
.. _commit message conventions: https://git.wiki.kernel.org/index.php/CommitMessageConventions