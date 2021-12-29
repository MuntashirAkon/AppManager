// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcelable;

// Copyright 2017 Zheng Li
public abstract class Caller implements Parcelable {
    protected Class<?>[] mParameterTypes;
    protected String[] mParameterTypesAsString;
    protected Object[] mParameters;

    protected void initParameters(Class<?>[] parameterTypes, Object[] parameters) {
        setParameterTypes(parameterTypes);
        this.mParameters = parameters;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        if (parameterTypes != null) {
            mParameterTypesAsString = new String[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                mParameterTypesAsString[i] = parameterTypes[i].getName();
            }
        }
    }

    public void setParameterTypes(String[] parameterTypes) {
        this.mParameterTypesAsString = parameterTypes;
    }

    public Class<?>[] getParameterTypes() {
        if (mParameterTypesAsString != null) {
            if (mParameterTypes == null) {
                mParameterTypes = ClassUtils.string2Class(mParameterTypesAsString);
            }
            return mParameterTypes;
        }
        return null;
    }

    public Object[] getParameters() {
        return mParameters;
    }

    public Caller wrapParameters() {
        return ParamsFixer.wrap(this);
    }

    public Caller unwrapParameters() {
        return ParamsFixer.unwrap(this);
    }

    public abstract int getType();
}
