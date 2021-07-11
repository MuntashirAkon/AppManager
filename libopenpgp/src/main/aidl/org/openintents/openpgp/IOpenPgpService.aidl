// SPDX-License-Identifier: Apache-2.0

package org.openintents.openpgp;

// Copyright 2014-2015 Dominik Schürmann
interface IOpenPgpService {

    /**
     * do NOT use this, data returned from the service through "output" may be truncated
     * @deprecated
     */
    Intent execute(in Intent data, in ParcelFileDescriptor input, in ParcelFileDescriptor output);

}