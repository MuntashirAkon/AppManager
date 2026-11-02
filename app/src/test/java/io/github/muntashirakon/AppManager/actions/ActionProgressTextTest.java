// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.app.Service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;

import io.github.muntashirakon.AppManager.progress.ActionProgressText;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ActionProgressTextTest {
    @Test
    public void lambdaSuppliesTaskLabelWithOptionalTitleAndProgressText() {
        ProgressHandler.ProgressTextInterface formatter = handler -> "Custom text";
        TestHandler handler = new TestHandler();
        assertEquals("Custom text", formatter.getCurrentTaskLabel(handler));
        assertNull(formatter.getTitle(handler));
        assertNull(formatter.getProgressText(handler));
        assertEquals("50%", ProgressHandler.PROGRESS_PERCENT.getCurrentTaskLabel(handler));
        assertEquals("1/2", ProgressHandler.PROGRESS_REGULAR.getCurrentTaskLabel(handler));
        assertNull(ProgressHandler.PROGRESS_PERCENT.getProgressText(handler));
        assertNull(ProgressHandler.PROGRESS_REGULAR.getProgressText(handler));
    }

    @Test
    public void genericHandlerCanReadLabelBeforeTextAndRetainItUntilNextStart() {
        for (ActionExecutionMode mode : ActionExecutionMode.values()) {
            TestHandler handler = new TestHandler();
            ActionProgressText formatter = new ActionProgressText(RuntimeEnvironment.getApplication(), "My plan");
            assertEquals("My plan", formatter.getTitle(handler));
            assertNull(formatter.getCurrentTaskLabel(handler));
            handler.publish("Running on Maps…", ActionProgress.State.STARTED, mode);
            assertEquals("Running on Maps…", formatter.getCurrentTaskLabel(handler));
            assertEquals("50% complete", formatter.getProgressText(handler));

            handler.publish(null, ActionProgress.State.UPDATED, mode);
            assertEquals("Running on Maps…", formatter.getCurrentTaskLabel(handler));
            handler.publish(null, ActionProgress.State.COMPLETED, mode);
            assertEquals("Running on Maps…", formatter.getCurrentTaskLabel(handler));

            handler.publish(null, ActionProgress.State.STARTED, mode);
            assertNull(formatter.getCurrentTaskLabel(handler));
            assertEquals("My plan", formatter.getTitle(handler));
            assertEquals("50% complete", formatter.getProgressText(handler));
        }
    }

    private static final class TestHandler extends ProgressHandler {
        void publish(@Nullable String label, ActionProgress.State state, ActionExecutionMode mode) {
            postActionProgress(new ActionProgress(label, 2, 1, state, mode, Collections.emptyList()));
        }

        @Override
        public void onAttach(Service service, @NonNull Object message) {}

        @Override
        public void onProgressStart(int max, float current, Object message) {}

        @Override
        public void onProgressUpdate(int max, float current, Object message) {}

        @Override
        public void onResult(Object message) {}

        @Override
        public void onDetach(Service service) {}

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler(CharSequence operationName) {
            return new TestHandler();
        }

        @Nullable
        @Override
        public Object getLastMessage() {
            return null;
        }

        @Override
        public int getLastMax() {
            return 2;
        }

        @Override
        public float getLastProgress() {
            return 1;
        }
    }
}
