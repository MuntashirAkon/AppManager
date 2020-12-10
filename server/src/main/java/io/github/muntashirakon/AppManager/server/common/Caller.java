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

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcelable;

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
