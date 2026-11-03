// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import static org.junit.Assert.*;

import android.app.Application;
import android.app.Service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.EmptyLogger;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ActionSubProgressTest {
    @Test
    public void childRangesAreIndependentAndLateObserversReplayActiveWork() throws Exception {
        NestedAction action = new NestedAction();
        ActionPlan plan = new ActionPlan("Plan", Collections.singletonList(action));
        RecordingHandler primary = new RecordingHandler();
        RecordingHandler late = new RecordingHandler();
        ActionContext context = context(plan, primary);
        List<ActionProgress> events = new ArrayList<>();
        context.addProgressListener(events::add);
        action.duringOperation = () -> {
            assertEquals(1, context.getLatestProgress().max);
            assertEquals(0f, context.getLatestProgress().current, 0f);
            assertNull(context.getLatestProgress().child);
            context.addProgressHandler(late);
        };

        List<ActionResult> results = plan.execute(context, ActionMode.ON, new EmptyLogger());
        assertEquals(ActionResultStatus.SUCCESS, results.get(0).status);
        assertEquals(1, primary.children.size());
        assertEquals(1, late.children.size());
        RecordingHandler child = primary.children.get(0);
        assertEquals(ActionProgress.State.STARTED, child.events.get(0).state);
        assertEquals(-1, child.events.get(0).max);
        assertEquals(5, child.events.get(2).max);
        assertEquals(2f, child.events.get(2).current, 0f);
        ActionProgress childCompleted = child.events.get(child.events.size() - 1);
        assertEquals(ActionProgress.State.COMPLETED, childCompleted.state);
        assertEquals(3f, childCompleted.current, 0f);
        assertEquals(action.getId(), childCompleted.child.actionId);
        assertEquals(action.target, childCompleted.child.target);
        assertEquals("Backup", childCompleted.child.operationName);

        RecordingHandler lateChild = late.children.get(0);
        assertEquals(ActionProgress.State.STARTED, lateChild.events.get(0).state);
        assertEquals(2f, lateChild.events.get(0).current, 0f);
        assertEquals(5, lateChild.events.get(0).max);
        assertSame(childCompleted.child, lateChild.events.get(0).child);
        assertEquals(ActionProgress.State.COMPLETED, lateChild.events.get(lateChild.events.size() - 1).state);

        assertEquals(ActionProgress.State.COMPLETED, primary.getLastActionProgress().state);
        assertEquals(1f, primary.getLastActionProgress().current, 0f);
        assertEquals(ActionProgress.State.COMPLETED, late.getLastActionProgress().state);
        List<ActionProgress> replay = new ArrayList<>();
        context.addProgressListener(replay::add);
        assertEquals(1, replay.size());
        assertNull(replay.get(0).child);
        for (ActionProgress event : events) {
            if (event.child == null) assertEquals(1, event.max);
        }
    }

    @Test
    public void cleanupCompletesChildOnEarlyAndMidOperationFailuresWithoutParentHandler() throws Exception {
        for (int failAt : new int[]{1, 2}) {
            NestedAction action = new NestedAction();
            action.failAt = failAt;
            ActionPlan plan = new ActionPlan("Plan", Collections.singletonList(action));
            ActionContext context = context(plan, null);
            List<ActionProgress> children = new ArrayList<>();
            context.addProgressListener(progress -> {
                throw new IllegalStateException("observer");
            });
            context.addProgressListener(progress -> {
                if (progress.child != null) children.add(progress);
            });
            List<ActionResult> results = plan.execute(context, ActionMode.ON, new EmptyLogger());
            assertEquals(ActionResultStatus.FAILED, results.get(0).status);
            assertEquals(ActionProgress.State.STARTED, children.get(0).state);
            ActionProgress completed = children.get(children.size() - 1);
            assertEquals(ActionProgress.State.COMPLETED, completed.state);
            assertEquals(failAt == 1 ? -1 : 5, completed.max);
            assertEquals(ActionProgress.State.COMPLETED, context.getLatestProgress().state);
            assertEquals(1f, context.getLatestProgress().current, 0f);
            List<ActionProgress> replay = new ArrayList<>();
            context.addProgressListener(replay::add);
            assertEquals(1, replay.size());
        }
    }

    @Test
    public void repeatedOperationsHaveDistinctIdentitiesAndEachPresentationFinishes() throws Exception {
        NestedAction action = new NestedAction();
        action.repetitions = 2;
        ActionPlan plan = new ActionPlan("Plan", Collections.singletonList(action));
        RecordingHandler handler = new RecordingHandler();
        plan.execute(context(plan, handler), ActionMode.ON, new EmptyLogger());
        assertEquals(2, handler.children.size());
        assertNotEquals(handler.children.get(0).getLastActionProgress().child.id,
                handler.children.get(1).getLastActionProgress().child.id);
        assertEquals(1, handler.completions);
        for (RecordingHandler child : handler.children) assertEquals(1, child.completions);
    }

    @Test
    public void detachingOnePresentationFinishesOnlyItsOwnChildren() throws Exception {
        NestedAction action = new NestedAction();
        ActionPlan plan = new ActionPlan("Plan", Collections.singletonList(action));
        RecordingHandler primary = new RecordingHandler();
        RecordingHandler detached = new RecordingHandler();
        ActionContext context = context(plan, primary);
        ActionContext.ProgressListener subscription = context.addProgressHandler(detached);
        action.duringOperation = () -> {
            assertEquals(1, detached.children.size());
            context.removeProgressListener(subscription);
            assertEquals(1, detached.children.get(0).completions);
        };
        plan.execute(context, ActionMode.ON, new EmptyLogger());
        assertEquals(1, primary.completions);
        assertEquals(1, primary.children.get(0).completions);
        assertEquals(0, detached.completions);
        assertEquals(1, detached.children.get(0).completions);
        assertEquals(2f, detached.children.get(0).getLastProgress(), 0f);
        assertEquals(3f, primary.children.get(0).getLastProgress(), 0f);
    }

    @NonNull
    private static ActionContext context(@NonNull ActionPlan plan, @Nullable ProgressHandler handler) {
        return new ActionContext(null, Collections.emptyList(), plan.getId(),
                ActionExecutionMode.BATCH, 0, true, handler);
    }

    private static final class NestedAction extends PackageScopedAction {
        final UserPackagePair target = new UserPackagePair("example", 10);
        Runnable duringOperation;
        int failAt;
        int repetitions = 1;

        NestedAction() {
            super(ActionKind.BACKUP, new ActionTargetSpec(new PackageTargetSpec(
                            Collections.singletonList(new UserPackagePair("example", 10)), false, false)),
                    new ActionParameters(), null);
        }

        @NonNull
        @Override
        protected CharSequence getProgressLabel(@NonNull ActionContext context, @NonNull UserPackagePair target) {
            return "Running Backup for Example…";
        }

        @NonNull
        @Override
        protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode, @NonNull UserPackagePair target,
                                         @NonNull List<ActionExecutionRecord> previous) {
            for (int i = 0; i < repetitions; i++) {
                try (SubProgressHandler child = newSubProgressHandler(context, target, "Backup", "Backing up Example…")) {
                    if (failAt == 1) throw new IllegalStateException("before work");
                    child.postUpdate(5, 0);
                    child.postUpdate(2);
                    assertEquals(2f, child.getLastProgress(), 0f);
                    assertEquals(5, child.getLastMax());
                    if (duringOperation != null) duringOperation.run();
                    if (failAt == 2) throw new IllegalStateException("during work");
                    child.postUpdate(child.getLastProgress() + 1);
                }
            }
            return actionResult(target, ActionResultStatus.SUCCESS, null);
        }
    }

    private static final class RecordingHandler extends ProgressHandler {
        final List<RecordingHandler> children = new ArrayList<>();
        final List<ActionProgress> events = new ArrayList<>();
        int max;
        float current;
        int completions;

        @Override
        public void onActionProgress(@NonNull ActionProgress progress) {
            events.add(progress);
            super.onActionProgress(progress);
        }

        @Override
        public void onAttach(Service service, @NonNull Object message) {
        }

        @Override
        public void onProgressStart(int max, float current, Object message) {
            onProgressUpdate(max, current, message);
        }

        @Override
        public void onProgressUpdate(int max, float current, Object message) {
            this.max = max;
            this.current = current;
        }

        @Override
        public void onResult(Object message) {
            ++completions;
        }

        @Override
        public void onDetach(Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler(CharSequence operationName) {
            RecordingHandler child = new RecordingHandler();
            children.add(child);
            return child;
        }

        @Nullable
        @Override
        public Object getLastMessage() {
            return null;
        }

        @Override
        public int getLastMax() {
            return max;
        }

        @Override
        public float getLastProgress() {
            return current;
        }
    }
}
