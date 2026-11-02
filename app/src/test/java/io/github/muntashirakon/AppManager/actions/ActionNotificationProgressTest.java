// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Collections;

import io.github.muntashirakon.AppManager.progress.ActionProgressText;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationInfo;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationManagerInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ActionNotificationProgressTest {
    @Test
    public void genericSubHandlerCopiesContextWithoutAnyActionEvents() {
        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        NotificationManagerInfo channel = new NotificationManagerInfo("progress", "Progress", 2);
        NotificationProgressHandler parent = new NotificationProgressHandler(app, channel, channel, null);
        NotificationInfo message = new NotificationInfo().setTitle("Files")
                .setBody("Copying files…").setOperationName("File manager");
        parent.onAttach(null, message);
        parent.onProgressStart(10, 1, null);
        ProgressHandler child = parent.newSubProgressHandler("Copy");
        child.onProgressStart(5, 2, null);
        assertNotSame(message, child.getLastMessage());
        assertNull(parent.getLastActionProgress());
        assertNull(child.getLastActionProgress());
        Notification notification = Shadows.shadowOf(manager).getAllNotifications().stream()
                .filter(item -> "Copy".contentEquals(item.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)))
                .findFirst().get();
        assertEquals("Files", notification.extras.getCharSequence(Notification.EXTRA_TITLE));
        assertEquals("Copying files…", notification.extras.getCharSequence(Notification.EXTRA_TEXT));
        assertNotNull(notification.getGroup());
        child.onResult(null);
        assertEquals(1, Shadows.shadowOf(manager).getAllNotifications().size());
        assertEquals(1f, parent.getLastProgress(), 0f);
        ProgressHandler inherited = parent.newSubProgressHandler(null);
        inherited.onProgressStart(-1, 0, null);
        assertTrue(Shadows.shadowOf(manager).getAllNotifications().stream()
                .allMatch(item -> "File manager".contentEquals(item.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))));
        inherited.onResult(null);
        parent.onResult(null);
        assertTrue(Shadows.shadowOf(manager).getAllNotifications().isEmpty());
    }

    @Test
    public void childNotificationsCopyContextAndFinishWithoutChangingParent() {
        verifyChildNotifications(false);
    }

    @Test
    public void lateChildSnapshotInitializesNotificationAtCurrentPosition() {
        verifyChildNotifications(true);
    }

    private void verifyChildNotifications(boolean replay) {
        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        NotificationManagerInfo channel = new NotificationManagerInfo("progress", "Progress", 2);
        NotificationProgressHandler handler = new NotificationProgressHandler(app, channel, channel, null);
        NotificationInfo parent = new NotificationInfo().setTitle("Original title")
                .setBody("Operation running…").setOperationName("Profiles");
        handler.onAttach(null, parent);
        handler.setProgressTextInterface(new ActionProgressText(app, "My profile"));
        ActionContext context = new ActionContext(null, Collections.emptyList(), "plan",
                ActionExecutionMode.BATCH, 0, false);
        if (!replay) context.addProgressHandler(handler);
        ActionProgress parentProgress = new ActionProgress("Running Backup for Maps…", 17, 3,
                ActionProgress.State.UPDATED, ActionExecutionMode.BATCH, Collections.emptyList());
        context.dispatch(parentProgress);
        ActionProgress.Child child = new ActionProgress.Child("backup", new UserPackagePair("maps", 10),
                "Backup", "Backing up Maps…");
        context.dispatch(childProgress(child, ActionProgress.State.STARTED, -1, 0));
        context.dispatch(childProgress(child, ActionProgress.State.UPDATED, 5, 2));
        if (replay) context.addProgressHandler(handler);
        assertEquals(2, Shadows.shadowOf(manager).getAllNotifications().size());
        Notification childNotification = Shadows.shadowOf(manager).getAllNotifications().stream()
                .filter(notification -> "Backing up Maps…".contentEquals(
                        notification.extras.getCharSequence(Notification.EXTRA_TITLE)))
                .findFirst().get();
        assertEquals("Backup · 40% complete", childNotification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        assertEquals("Backing up Maps…", childNotification.extras.getCharSequence(Notification.EXTRA_TEXT));
        assertEquals(4000, childNotification.extras.getInt(Notification.EXTRA_PROGRESS));
        assertNotNull(childNotification.getGroup());
        assertSame(parent, handler.getLastMessage());
        assertSame(parentProgress, handler.getLastActionProgress());
        assertEquals(17, handler.getLastMax());
        assertEquals(3f, handler.getLastProgress(), 0f);

        context.dispatch(childProgress(child, ActionProgress.State.COMPLETED, 5, 2));
        assertEquals(1, Shadows.shadowOf(manager).getAllNotifications().size());
        Notification parentNotification = Shadows.shadowOf(manager).getAllNotifications().get(0);
        assertEquals("My profile", parentNotification.extras.getCharSequence(Notification.EXTRA_TITLE));
        assertEquals("Profiles · 17% complete", parentNotification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        assertEquals("Running Backup for Maps…", parentNotification.extras.getCharSequence(Notification.EXTRA_TEXT));

        // Switching to an empty formatter reveals the original, unmodified host metadata.
        handler.setProgressTextInterface(null);
        handler.postUpdate(17, 4);
        Notification original = Shadows.shadowOf(manager).getAllNotifications().get(0);
        assertEquals("Original title", original.extras.getCharSequence(Notification.EXTRA_TITLE));
        assertEquals("Profiles", original.extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        assertEquals("Operation running…", original.extras.getCharSequence(Notification.EXTRA_TEXT));
        context.dispatch(new ActionProgress(null, 17, 4, ActionProgress.State.COMPLETED,
                ActionExecutionMode.BATCH, Collections.emptyList()));
        assertTrue(Shadows.shadowOf(manager).getAllNotifications().isEmpty());
    }

    private static ActionProgress childProgress(ActionProgress.Child child, ActionProgress.State state,
                                                int max, float current) {
        return new ActionProgress(child.title, max, current, state, ActionExecutionMode.BATCH,
                Collections.emptyList(), child);
    }

    @Test
    public void singletonSeparatesTitleTaskAndProgressText() {
        verify(ActionExecutionMode.SINGLETON, "Freeze", "Running on Maps…", "Profiles · 37% complete", 8, 3, 10000, 3750);
    }

    @Test
    public void batchKeepsPlanTitleAndSeparatesTaskAndProgressText() {
        verify(ActionExecutionMode.BATCH, "My profile", "Running on Maps…", "Profiles · 37% complete", 8, 3, 10000, 3750);
    }

    @Test
    public void indeterminateSingletonHasNoPercentageOrCompletionAlert() {
        verify(ActionExecutionMode.SINGLETON, "Freeze", "Running on Maps…", "Profiles", -1, 0, 0, 0);
    }

    @Test
    public void singletonFractionalUnitShowsHalfFilledBar() {
        verify(ActionExecutionMode.SINGLETON, "Freeze", "Running on Maps…", "Profiles · 50% complete", 1, 0.5f, 10000, 5000);
    }

    @Test
    public void batchFractionalUnitShowsHalfFilledBar() {
        verify(ActionExecutionMode.BATCH, "My profile", "Running on Maps…", "Profiles · 50% complete", 1, 0.5f, 10000, 5000);
    }

    @Test
    public void singletonRespectsCustomFormatter() {
        verify(ActionExecutionMode.SINGLETON, "Freeze", "Custom progress", "Profiles", 8, 3, 10000, 3750,
                handler -> "Custom progress");
    }

    @Test
    public void batchRespectsCountFormatter() {
        verify(ActionExecutionMode.BATCH, "Freeze", "3/8", "Profiles", 8, 3, 10000, 3750,
                ProgressHandler.PROGRESS_REGULAR);
    }

    @Test
    public void batchRespectsCustomLabelAndText() {
        verify(ActionExecutionMode.BATCH, "Custom title", "Custom task", "Profiles · Custom text", 8, 3, 10000, 3750,
                new ProgressHandler.ProgressTextInterface() {
                    @Override
                    public CharSequence getTitle(@NonNull ProgressHandler handler) {
                        return "Custom title";
                    }

                    @Override
                    public CharSequence getCurrentTaskLabel(@NonNull ProgressHandler handler) {
                        return "Custom task";
                    }

                    @Override
                    public CharSequence getProgressText(@NonNull ProgressHandler handler) {
                        return "Custom text";
                    }
                });
    }

    private void verify(ActionExecutionMode mode, String title, String text, String subtext, int max, float current,
                        int barMax, int barCurrent) {
        verify(mode, title, text, subtext, max, current, barMax, barCurrent,
                new ActionProgressText(RuntimeEnvironment.getApplication(), title));
    }

    private void verify(ActionExecutionMode mode, String title, String text, String subtext, int max, float current,
                        int barMax, int barCurrent, ProgressHandler.ProgressTextInterface formatter) {
        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        NotificationManagerInfo channel = new NotificationManagerInfo("progress", "Progress", 2);
        NotificationProgressHandler handler = new NotificationProgressHandler(app, channel, channel, null);
        handler.setProgressTextInterface(formatter);
        NotificationInfo parent = new NotificationInfo().setTitle("Freeze")
                .setBody("Operation running…").setOperationName("Profiles");
        handler.onAttach(null, parent);
        handler.postActionProgress(new ActionProgress(null, max, 0, ActionProgress.State.STARTED,
                mode, Collections.emptyList()));
        handler.postActionProgress(new ActionProgress("Running on Maps…", max, current,
                ActionProgress.State.UPDATED, mode, Collections.emptyList()));
        Notification notification = Shadows.shadowOf(manager).getAllNotifications().get(0);
        assertEquals(title, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals(text, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals(subtext, notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString());
        assertEquals(max == -1, notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE));
        assertEquals(barMax, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX));
        assertEquals(barCurrent, notification.extras.getInt(Notification.EXTRA_PROGRESS));
        assertEquals(max, handler.getLastMax());
        assertEquals(current, handler.getLastProgress(), 0f);
        assertSame(parent, handler.getLastMessage());
        handler.postActionProgress(new ActionProgress(null, max, current,
                ActionProgress.State.COMPLETED, mode, Collections.emptyList()));
        assertTrue(Shadows.shadowOf(manager).getAllNotifications().isEmpty());
    }
}
