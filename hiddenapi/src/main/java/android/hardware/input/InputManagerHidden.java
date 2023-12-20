// SPDX-License-Identifier: Apache-2.0

package android.hardware.input;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(InputManager.class)
public class InputManagerHidden {
    /**
     * Input Event Injection Synchronization Mode: None.
     * Never blocks.  Injection is asynchronous and is assumed always to be successful.
     */
    public static /*final*/ int INJECT_INPUT_EVENT_MODE_ASYNC;

    /**
     * Input Event Injection Synchronization Mode: Wait for result.
     * Waits for previous events to be dispatched so that the input dispatcher can
     * determine whether input event injection will be permitted based on the current
     * input focus.  Does not wait for the input event to finish being handled
     * by the application.
     */
    public static /*final*/ int INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT;

    /**
     * Input Event Injection Synchronization Mode: Wait for finish.
     * Waits for the event to be delivered to the application and handled.
     */
    public static /*final*/ int INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH;
}
