/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net;

/**
 * Manager for creating and modifying network policy rules.
 */
public class NetworkPolicyManager {
    /* POLICY_* are masks and can be ORed, although currently they are not. */
    /**
     * No specific network policy, use system default.
     */
    public static final int POLICY_NONE = 0x0;
    /**
     * Reject network usage on metered networks when application in background.
     */
    public static final int POLICY_REJECT_METERED_BACKGROUND = 0x1;
    /**
     * Allow metered network use in the background even when in data usage save mode.
     */
    public static final int POLICY_ALLOW_METERED_BACKGROUND = 0x4;
}