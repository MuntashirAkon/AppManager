// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import static org.junit.Assert.*;

import android.app.Application;
import android.app.Service;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.EmptyLogger;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.actions.ActionContext.ProgressListener;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ActionProgressLifecycleTest {
    @Test
    public void aggregatesSeventeenUnitsAndReusesPreparedTargets() throws Exception {
        List<UserPackagePair> targets = new ArrayList<>();
        for (int i = 0; i < 8; i++) targets.add(new UserPackagePair("pkg" + i, i));
        PackageAction first = new PackageAction(targets);
        PackageAction second = new PackageAction(targets);
        GlobalAction global = new GlobalAction();
        ActionPlan plan = new ActionPlan("plan", Arrays.asList(first, second, global));
        List<ActionProgress> events = new ArrayList<>();
        ActionContext context = context(plan, ActionExecutionMode.BATCH, null, events::add);
        List<ActionResult> results = plan.execute(context, ActionMode.ON, new EmptyLogger());
        assertEquals(17, results.size());
        assertEquals(ActionProgress.State.STARTED, events.get(0).state);
        assertEquals(17, events.get(0).max);
        float previous = 0;
        for (ActionProgress event : events) {
            assertEquals(17, event.max);
            assertTrue(event.current >= previous);
            previous = event.current;
        }
        ActionProgress completed = events.get(events.size() - 1);
        assertEquals(ActionProgress.State.COMPLETED, completed.state);
        assertEquals(17f, completed.current, 0f);
        assertEquals(results, completed.results);
        assertSame(first.prepared, first.executed);
        assertSame(first.prepared, first.resolveTargets(context));
        assertTrue(global.cleaned);
    }

    @Test
    public void singletonGlobalStaysIndeterminateAndReplaysCompletion() throws Exception {
        ActionPlan plan = new ActionPlan("global", Collections.singletonList(new GlobalAction()));
        ActionContext progress = context(plan, ActionExecutionMode.SINGLETON, null, null);
        plan.execute(progress, ActionMode.ON, new EmptyLogger());
        List<ActionProgress> late = new ArrayList<>();
        progress.addProgressListener(late::add);
        assertEquals(1, late.size());
        assertEquals(ActionProgress.State.COMPLETED, late.get(0).state);
        assertEquals(-1, late.get(0).max);
        assertEquals(0f, late.get(0).current, 0f);
        assertEquals(1, late.get(0).results.size());
    }

    @Test
    public void actionsShareContextAndListenersRegisteredDuringExecution() throws Exception {
        PackageAction first = new PackageAction(Collections.singletonList(new UserPackagePair("first", 0)));
        PackageAction second = new PackageAction(Collections.singletonList(new UserPackagePair("second", 0)));
        ActionPlan plan = new ActionPlan("shared", Arrays.asList(first, second));
        List<ActionProgress> initialObserver = new ArrayList<>();
        List<ActionProgress> addedDuringExecution = new ArrayList<>();
        ProgressListener initialListener = initialObserver::add;
        ActionContext context = context(plan, ActionExecutionMode.BATCH, null, initialListener);
        first.listenerToAdd = addedDuringExecution::add;
        second.listenerToRemove = initialListener;

        plan.execute(context, ActionMode.ON, new EmptyLogger());

        assertSame(context, first.executedContext);
        assertSame(context, second.executedContext);
        assertTrue(addedDuringExecution.stream().anyMatch(event -> "second".equals(event.label)));
        assertFalse(initialObserver.stream().anyMatch(event -> "second".equals(event.label)));
        for (ActionProgress event : addedDuringExecution) assertEquals(2, event.max);
        ActionProgress completed = addedDuringExecution.get(addedDuringExecution.size() - 1);
        assertSame(completed, context.getLatestProgress());
        assertEquals(ActionProgress.State.COMPLETED, completed.state);
        assertEquals(2, completed.results.size());

        // The per-Action aggregation range must have been removed after execution.
        context.dispatch(ActionProgress.determinate("standalone", 8, 3));
        assertEquals(8, context.getLatestProgress().max);
        assertEquals(3f, context.getLatestProgress().current, 0f);
    }

    @Test
    public void workerEventsReachHandlerInOrderOnMainThreadWithoutCompletionAlert() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        ActionPlan plan = new ActionPlan("global", Collections.singletonList(new GlobalAction()));
        ActionContext context = context(plan, ActionExecutionMode.SINGLETON, handler, null);
        List<Throwable> failures = new ArrayList<>();
        Thread worker = new Thread(() -> {
            try {
                plan.execute(context, ActionMode.ON, new EmptyLogger());
            } catch (Throwable failure) {
                failures.add(failure);
            }
        });
        worker.start();
        worker.join();
        assertTrue(failures.toString(), failures.isEmpty());
        assertTrue(handler.events.isEmpty());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(ActionProgress.State.STARTED, handler.events.get(0).state);
        assertEquals(ActionProgress.State.COMPLETED,
                handler.events.get(handler.events.size() - 1).state);
        assertEquals(1, handler.completions);
        assertNull(handler.completionMessage);
        assertSame(handler.parentMessage, handler.getLastMessage());
        assertEquals(1, handler.getLastActionProgress().results.size());
        int eventCount = handler.events.size();
        context.dispatch(ActionProgress.determinate("after execution", 1, 0));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(eventCount, handler.events.size());
    }

    @Test
    public void observerAndLabelFailuresDoNotChangeExecutionOrCleanup() {
        PackageAction packages = new PackageAction(Collections.singletonList(new UserPackagePair("pkg", 0)));
        packages.failLabels = true;
        GlobalAction global = new GlobalAction();
        global.failLabels = true;
        ActionContext context = new ActionContext(null, Collections.emptyList(), "plan",
                ActionExecutionMode.BATCH, 1, false, null,
                event -> {
                    throw new IllegalStateException("Disconnected observer");
                });
        assertEquals(ActionResultStatus.SUCCESS,
                packages.execute(context, ActionMode.ON, Collections.emptyList()).get(0).status);
        assertEquals(ActionResultStatus.SUCCESS,
                global.execute(context, ActionMode.ON, Collections.emptyList()).get(0).status);
        assertTrue(packages.cleaned);
        assertTrue(global.cleaned);
    }

    @Test
    public void handlerReceivesQueuedCompletionBeforeItsSubscriptionIsRemoved() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        GlobalAction action = new GlobalAction();
        CountDownLatch executionEntered = new CountDownLatch(1);
        CountDownLatch finishExecution = new CountDownLatch(1);
        CountDownLatch replayEntered = new CountDownLatch(1);
        CountDownLatch releaseReplay = new CountDownLatch(1);
        action.executionHook = () -> {
            executionEntered.countDown();
            await(finishExecution);
        };
        ActionPlan plan = new ActionPlan("queued", Collections.singletonList(action));
        ActionContext context = context(plan, ActionExecutionMode.SINGLETON, handler, null);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        Thread worker = new Thread(() -> {
            try {
                plan.execute(context, ActionMode.ON, new EmptyLogger());
            } catch (Throwable failure) {
                failures.add(failure);
            }
        });
        Thread subscriber = new Thread(() -> context.addProgressListener(event -> {
            replayEntered.countDown();
            await(releaseReplay);
        }));
        worker.start();
        try {
            assertTrue(executionEntered.await(5, TimeUnit.SECONDS));
            subscriber.start();
            assertTrue(replayEntered.await(5, TimeUnit.SECONDS));
            finishExecution.countDown();
            worker.join(5000);
            assertFalse(worker.isAlive());
        } finally {
            finishExecution.countDown();
            releaseReplay.countDown();
            worker.join(5000);
            subscriber.join(5000);
        }
        assertFalse(subscriber.isAlive());
        assertTrue(failures.toString(), failures.isEmpty());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(ActionProgress.State.COMPLETED, handler.getLastActionProgress().state);
        assertEquals(ActionResultStatus.SUCCESS, handler.getLastActionProgress().results.get(0).status);
        assertEquals(1, handler.completions);
        int eventCount = handler.events.size();
        context.dispatch(ActionProgress.determinate("after completion", 1, 1));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(eventCount, handler.events.size());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    public void allTargetResultPathsAdvanceProgressAndCleanupRuns() {
        for (ActionResultStatus status : Arrays.asList(ActionResultStatus.SUCCESS,
                ActionResultStatus.UNCHANGED, ActionResultStatus.UNSUPPORTED,
                ActionResultStatus.SKIPPED_STRICT_MISMATCH, ActionResultStatus.FAILED)) {
            PackageAction action = new PackageAction(Collections.singletonList(new UserPackagePair("pkg", 0)),
                    status == ActionResultStatus.SKIPPED_STRICT_MISMATCH);
            ActionMode mode = ActionMode.ON;
            if (status == ActionResultStatus.UNCHANGED) action.currentState = 1;
            if (status == ActionResultStatus.SKIPPED_STRICT_MISMATCH) {
                action.currentState = 2;
                mode = ActionMode.OFF;
            }
            if (status == ActionResultStatus.UNSUPPORTED) mode = ActionMode.OFF;
            action.failExecution = status == ActionResultStatus.FAILED;
            List<ActionProgress> events = new ArrayList<>();
            ActionContext context = new ActionContext(null, Collections.emptyList(), "plan",
                    ActionExecutionMode.BATCH, 1, false, null, events::add);
            action.execute(context, mode, Collections.emptyList());
            ActionProgress completed = events.get(events.size() - 1);
            assertEquals(1f, completed.current, 0f);
            assertEquals(status, completed.results.get(0).status);
            assertTrue(action.cleaned);
        }
    }

    @Test
    public void zeroTargetActionEmitsLifecycleAndIsExcludedFromBatchWork() throws Exception {
        PackageAction empty = new PackageAction(Collections.emptyList());
        ActionPlan plan = new ActionPlan("plan", Arrays.asList(empty, new GlobalAction()));
        List<ActionProgress> events = new ArrayList<>();
        plan.execute(context(plan, ActionExecutionMode.BATCH, null, events::add),
                ActionMode.ON, new EmptyLogger());
        assertTrue(empty.cleaned);
        assertEquals(1, events.get(0).max);
        assertEquals(1f, events.get(events.size() - 1).current, 0f);
        assertEquals(1, events.get(events.size() - 1).results.size());
    }

    @Test
    public void preparationFailureCompletesFailedTargetsAndEmptyPlanCompletes() throws Exception {
        PackageAction action = new PackageAction(Collections.singletonList(new UserPackagePair("pkg", 0)));
        action.failPreparation = true;
        List<ActionProgress> events = new ArrayList<>();
        ActionPlan plan = new ActionPlan("failed", Collections.singletonList(action));
        plan.execute(context(plan, ActionExecutionMode.BATCH, null, events::add),
                ActionMode.ON, new EmptyLogger());
        assertEquals(ActionResultStatus.FAILED, events.get(events.size() - 1).results.get(0).status);
        assertTrue(action.cleaned);
        events.clear();
        plan = new ActionPlan("empty", Collections.emptyList());
        plan.execute(context(plan, ActionExecutionMode.BATCH, null, events::add),
                ActionMode.ON, new EmptyLogger());
        assertEquals(2, events.size());
        assertEquals(0, events.get(1).max);
        assertEquals(ActionProgress.State.COMPLETED, events.get(1).state);
    }

    private static ActionContext context(ActionPlan plan, ActionExecutionMode mode,
                                         @Nullable ProgressHandler handler,
                                         @Nullable ProgressListener listener) {
        return new ActionContext(null, Collections.emptyList(), plan.getId(), mode,
                1, false, handler, listener);
    }

    private static final class PackageAction extends PackageScopedAction {
        List<UserPackagePair> prepared;
        List<UserPackagePair> executed;
        boolean cleaned;
        boolean failLabels;
        boolean failPreparation;
        boolean failExecution;
        Integer currentState;
        ActionContext executedContext;
        ProgressListener listenerToAdd;
        ProgressListener listenerToRemove;

        PackageAction(List<UserPackagePair> targets) {
            this(targets, false);
        }

        PackageAction(List<UserPackagePair> targets, boolean reversible) {
            super(ActionKind.FORCE_STOP, new ActionTargetSpec(new PackageTargetSpec(targets, false, false)),
                    new ActionParameters(), reversible ? new ActionParameters() : null);
        }

        @Nullable
        @Override
        protected ActionState getCurrentState(@NonNull UserPackagePair target) {
            return currentState == null ? null : new ActionState("state", ActionValue.of(currentState));
        }

        @Nullable
        @Override
        protected ActionState getOnState(@NonNull UserPackagePair target) {
            return currentState == null ? null : new ActionState("state", ActionValue.of(1));
        }

        @Nullable
        @Override
        protected ActionState getOffState(@NonNull UserPackagePair target) {
            return currentState == null ? null : new ActionState("state", ActionValue.of(0));
        }

        @Override
        protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                    @NonNull List<UserPackagePair> targets, @NonNull List<ActionExecutionRecord> records) {
            executedContext = context;
            if (listenerToAdd != null) context.addProgressListener(listenerToAdd);
            if (listenerToRemove != null) context.removeProgressListener(listenerToRemove);
            prepared = resolveTargets(context);
            executed = targets;
            if (failPreparation) throw new IllegalStateException("preparation failed");
        }

        @NonNull
        @Override
        protected CharSequence getProgressLabel(@NonNull ActionContext context,
                                                @NonNull UserPackagePair target) {
            if (failLabels) throw new IllegalStateException("label failed");
            return target.getPackageName();
        }

        @NonNull
        @Override
        protected ActionResult onExecute(@NonNull ActionContext context,
                                         @NonNull ActionMode mode, @NonNull UserPackagePair target,
                                         @NonNull List<ActionExecutionRecord> records) {
            if (failExecution) throw new IllegalStateException("execution failed");
            return actionResult(target, ActionResultStatus.SUCCESS, null);
        }

        @Override
        protected void onPostExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull List<UserPackagePair> targets, @NonNull List<ActionResult> results) {
            cleaned = true;
        }
    }

    private static final class GlobalAction extends GlobalScopedAction {
        boolean cleaned;
        boolean failLabels;
        Runnable executionHook;

        GlobalAction() {
            super(ActionKind.REBOOT, emptyGlobalTarget(), new ActionParameters(), null);
        }

        @NonNull
        @Override
        protected CharSequence getProgressLabel(@NonNull ActionContext context) {
            if (failLabels) throw new IllegalStateException("label failed");
            return "global";
        }

        @NonNull
        @Override
        protected ActionResult onExecute(@NonNull ActionContext context,
                                         @NonNull ActionMode mode, @NonNull List<ActionExecutionRecord> records) {
            if (executionHook != null) executionHook.run();
            return globalResult(ActionResultStatus.SUCCESS, null);
        }

        @Override
        protected void onPostExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull List<ActionResult> results) {
            cleaned = true;
        }
    }

    private static final class RecordingHandler extends ProgressHandler {
        final Object parentMessage = new Object();
        final List<ActionProgress> events = new ArrayList<>();
        int completions;
        Object completionMessage;

        @Override
        public void onActionProgress(@NonNull ActionProgress progress) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            events.add(progress);
            super.onActionProgress(progress);
        }

        @Override
        public void onAttach(Service service, @NonNull Object message) {
        }

        @Override
        public void onProgressStart(int max, float current, Object message) {
        }

        @Override
        public void onProgressUpdate(int max, float current, Object message) {
        }

        @Override
        public void onResult(Object message) {
            completions++;
            completionMessage = message;
        }

        @Override
        public void onDetach(Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler(CharSequence operationName) {
            return new RecordingHandler();
        }

        @Override
        public Object getLastMessage() {
            return parentMessage;
        }

        @Override
        public int getLastMax() {
            return 0;
        }

        @Override
        public float getLastProgress() {
            return 0;
        }
    }
}
