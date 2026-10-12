// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

@RunWith(RobolectricTestRunner.class)
public class ActionResultTest {
    @Test
    public void resolverDeduplicatesConfiguredSelectedAndInheritedTargets() {
        UserPackagePair configured = new UserPackagePair("configured", 0);
        UserPackagePair inherited = new UserPackagePair("trigger", 10);
        ActionContext context = new ActionContext(inherited, Arrays.asList(configured, inherited), "plan", 1L, true);
        PackageTargetSpec spec = new PackageTargetSpec(Collections.singletonList(configured), true, true);
        assertEquals(Arrays.asList(configured, inherited), new DefaultPackageTargetResolver().resolve(context, spec));
    }

    @Test
    public void resultParcelRoundTripPreservesConflictAndStates() {
        ActionState state = new ActionState("package:0:freeze", ActionValue.of(true));
        ActionResult result = new ActionResult("action", new UserPackagePair("pkg", 0), ActionResultStatus.CONFLICT,
                ActionConflict.EXTERNAL_CHANGE, state, null, "changed externally");
        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ActionResult restored = ActionResult.CREATOR.createFromParcel(parcel);
        assertEquals(result.status, restored.status);
        assertEquals(result.conflict, restored.conflict);
        assertTrue(restored.observedState.isSame(state));
        assertEquals(result.target, restored.target);
        parcel.recycle();
    }
}
