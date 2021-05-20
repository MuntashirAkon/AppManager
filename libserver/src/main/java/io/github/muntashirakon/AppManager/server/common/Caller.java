// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcelable;

// Copyright 2017 Zheng Li
@SuppressWarnings("rawtypes")
public abstract class Caller implements Parcelable {
    protected Class[] cParamsType;
    protected String[] sParamsType;
    protected Object[] params;

    protected void initParams(Class[] paramsType, Object[] params) {
        setParamsType(paramsType);
        this.params = params;
    }

    public void setParamsType(Class[] paramsType) {
        if (paramsType != null) {
            sParamsType = new String[paramsType.length];
            for (int i = 0; i < paramsType.length; i++) {
                sParamsType[i] = paramsType[i].getName();
            }
        }
    }

    public void setParamsType(String[] paramsType) {
        this.sParamsType = paramsType;
    }

    public Class[] getParamsType() {
        if (sParamsType != null) {
            if (cParamsType == null) {
                cParamsType = ClassUtils.string2Class(sParamsType);
            }
            return cParamsType;
        }
        return null;
    }

    public Object[] getParams() {
        return params;
    }

    public Caller wrapParams() {
        return ParamsFixer.wrap(this);
    }

    public Caller unwrapParams() {
        return ParamsFixer.unwrap(this);
    }

    public abstract int getType();
}
