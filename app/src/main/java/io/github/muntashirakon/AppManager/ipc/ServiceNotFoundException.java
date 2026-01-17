// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

public class ServiceNotFoundException extends RuntimeException {
    public ServiceNotFoundException() {
        super();
    }

    public ServiceNotFoundException(String name) {
        super(name);
    }

    public ServiceNotFoundException(String name, Exception cause) {
        super(name, cause);
    }
}
