// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

@RunWith(RobolectricTestRunner.class)
public class ActionModelTest {
    private static Action definition() {
        PackageTargetSpec packages = new PackageTargetSpec(
                Collections.singletonList(new UserPackagePair("com.example", 10)), true, true);
        return new FreezeAction("action-id", "Example action", 1,
                new ActionTargetSpec(packages), parameters(true, FreezeUtils.FREEZE_SUSPEND),
                parameters(false, FreezeUtils.FREEZE_SUSPEND),
                Strictness.STRICT, RevertMode.EXPLICIT);
    }

    @Test
    public void jsonRoundTripPreservesIdentityTargetsAndModes() throws Exception {
        Action original = definition();
        Action restored = Action.DESERIALIZER.deserialize(original.serializeToJson());
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getKind(), restored.getKind());
        assertEquals(FreezeUtils.FREEZE_SUSPEND,
                ((FreezeAction) restored).getFreezeType(ActionMode.ON));
        assertEquals(original.getStrictness(), restored.getStrictness());
        assertEquals(original.getRevertMode(), restored.getRevertMode());
        assertEquals(original.getTarget().getPackageTarget().getConfiguredTargets(), restored.getTarget().getPackageTarget().getConfiguredTargets());
        assertTrue(restored.getTarget().getPackageTarget().includesInherited());
        assertEquals(original.getOnParameters().serializeToJson().toString(),
                restored.getOnParameters().serializeToJson().toString());
    }

    @Test
    public void parcelRoundTripPreservesPlan() throws Exception {
        ActionPlan plan = new ActionPlan("Plan", Collections.singletonList(definition()));
        ActionPlan jsonRestored = ActionPlan.DESERIALIZER.deserialize(plan.serializeToJson());
        assertEquals(plan.getActions().get(0).getId(), jsonRestored.getActions().get(0).getId());
        Parcel parcel = Parcel.obtain();
        plan.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ActionPlan restored = ActionPlan.CREATOR.createFromParcel(parcel);
        assertEquals(plan.getId(), restored.getId());
        assertEquals(1, restored.getActions().size());
        assertEquals("action-id", restored.getActions().get(0).getId());
        parcel.recycle();
    }

    @Test
    public void localizedNameIsOptionalAndStableIdIsNotName() {
        Action action = new FreezeAction(
                new ActionTargetSpec(new PackageTargetSpec(Collections.emptyList(), false, false)),
                parameters(true, FreezeUtils.FREEZE_DISABLE), parameters(false, FreezeUtils.FREEZE_DISABLE));
        assertNotNull(action.getId());
        assertEquals(ActionKind.FREEZE.name(), action.toString().substring(0, ActionKind.FREEZE.name().length()));
    }

    @Test
    public void invalidScopeAndMissingReverseAreRejected() {
        GlobalTargetSpec global = new GlobalTargetSpec(new JSONObject());
        try {
            new FreezeAction(new ActionTargetSpec(global), parameters(true, FreezeUtils.FREEZE_DISABLE),
                    parameters(false, FreezeUtils.FREEZE_DISABLE));
            fail("scope mismatch must fail");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new FreezeAction("explicit", null, 1, new ActionTargetSpec(global),
                    parameters(true, FreezeUtils.FREEZE_DISABLE), null, Strictness.STRICT,
                    RevertMode.EXPLICIT);
            fail("explicit revert without off value must fail");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static ActionParameters parameters(boolean freeze, int freezeType) {
        return new ActionParameters().put("freeze", freeze).put("freeze_type", freezeType);
    }

}
