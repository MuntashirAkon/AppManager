// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

@RunWith(RobolectricTestRunner.class)
public class ActionPlanTest {
    @Test
    public void executesActionsInOrderAndKeepsStateRecords() {
        List<String> order = new java.util.ArrayList<>();
        RecordingAction first = new RecordingAction("first", "first-state", order);
        RecordingAction second = new RecordingAction("second", "second-state", order);
        ActionPlan plan = new ActionPlan("plan", Arrays.asList(first, second));

        List<ActionResult> results = plan.execute(
                new ActionContext(null, Collections.emptyList(), plan.getId(), 1L, false),
                ActionMode.ON);

        assertEquals(Arrays.asList("first", "second"), order);
        assertEquals(2, results.size());
        assertEquals(2, plan.getExecutionRecords().size());
        assertEquals("first-state", plan.getExecutionRecords().get(0).stateKey);
        assertEquals("second-state", plan.getExecutionRecords().get(1).stateKey);
    }

    @Test
    public void reportsProgressAndPreservesSuppliedStateSnapshots() {
        List<String> order = new ArrayList<>();
        RecordingProgressHandler progressHandler = new RecordingProgressHandler();
        ActionPlan plan = new ActionPlan("plan", Collections.singletonList(
                new RecordingAction("action", "state", order)));

        ActionContext context = new ActionContext(null, Collections.emptyList(), plan.getId(),
                1L, false, progressHandler);
        plan.execute(context, ActionMode.ON);

        assertEquals(1, progressHandler.startedMax);
        assertEquals(Collections.singletonList(1f), progressHandler.updates);
        assertEquals(1, plan.getExecutionRecords().size());
        assertEquals("state", plan.getExecutionRecords().get(0).stateKey);
        assertEquals(1, progressHandler.resultCount);
    }

    private static final class RecordingProgressHandler extends ProgressHandler {
        int startedMax;
        int resultCount;
        final List<Float> updates = new ArrayList<>();

        @Override
        public void onAttach(android.app.Service service, @NonNull Object message) {
        }

        @Override
        public void onProgressStart(int max, float current, Object message) {
            startedMax = max;
        }

        @Override
        public void onProgressUpdate(int max, float current, Object message) {
            updates.add(current);
        }

        @Override
        public void onResult(Object message) {
            resultCount++;
        }

        @Override
        public void onDetach(android.app.Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler() {
            return this;
        }

        @Override
        public Object getLastMessage() {
            return null;
        }

        @Override
        public int getLastMax() {
            return startedMax;
        }

        @Override
        public float getLastProgress() {
            return updates.isEmpty() ? 0 : updates.get(updates.size() - 1);
        }
    }

    private static final class RecordingAction extends Action {
        private final String mOrder;
        private final String mStateKey;
        private final List<String> mOrderList;

        RecordingAction(@NonNull String id, @NonNull String stateKey, @NonNull List<String> order) {
            super(id, null, CURRENT_SCHEMA_VERSION, ActionKind.FREEZE,
                    new ActionTargetSpec(new PackageTargetSpec(Collections.emptyList(), false, false)),
                    new ActionParameters().put("freeze", true).put("freeze_type", FreezeUtils.FREEZE_DISABLE),
                    new ActionParameters().put("freeze", false).put("freeze_type", FreezeUtils.FREEZE_DISABLE), Strictness.STRICT,
                    RevertMode.EXPLICIT);
            mOrder = id;
            mStateKey = stateKey;
            mOrderList = order;
        }

        @NonNull
        @Override
        public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                          @NonNull List<ActionExecutionRecord> previousRecords) {
            mOrderList.add(mOrder);
            ActionState state = new ActionState(mStateKey, ActionValue.of(true));
            return Collections.singletonList(new ActionResult(getId(),
                    new UserPackagePair("com.example", 0), ActionResultStatus.SUCCESS,
                    ActionConflict.NO_CONFLICT, state, state, null));
        }
    }
}
