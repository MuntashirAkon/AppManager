// SPDX-License-Identifier: Apache-2.0

package org.openintents.openpgp;

// Copyright 2015 Dominik Schürmann
interface IOpenPgpService2 {

    /**
     * see org.openintents.openpgp.util.OpenPgpApi for documentation
     */
    ParcelFileDescriptor createOutputPipe(in int pipeId);

    /**
     * see org.openintents.openpgp.util.OpenPgpApi for documentation
     */
    Intent execute(in Intent data, in ParcelFileDescriptor input, int pipeId);
}
