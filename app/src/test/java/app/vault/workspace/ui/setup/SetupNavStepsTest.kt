package app.vault.workspace.ui.setup

import app.vault.workspace.auth.LockRules
import app.vault.workspace.ui.settings.ChangeLockStep
import app.vault.workspace.ui.settings.changeLockStepOnBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupNavStepsTest {
    @Test
    fun setupBackConfirmToEnterToChooseTypeThenPop() {
        assertEquals(SetupWizardStep.Enter, setupStepOnBack(SetupWizardStep.Confirm))
        assertEquals(SetupWizardStep.ChooseType, setupStepOnBack(SetupWizardStep.Enter))
        assertNull(setupStepOnBack(SetupWizardStep.ChooseType))
    }

    @Test
    fun patternConfirmUsesSameEncoding() {
        val cells = listOf(0, 1, 2, 5, 8)
        val first = LockRules.encodePattern(cells)
        val confirm = LockRules.encodePattern(cells)
        assertTrue(patternSecretsMatch(first, confirm))
        assertEquals(first, confirm)
        assertFalse(patternSecretsMatch(first, LockRules.encodePattern(listOf(0, 1, 2, 4))))
        assertFalse(patternSecretsMatch("", ""))
    }

    @Test
    fun changeLockBackIsPathFaithful() {
        assertEquals(ChangeLockStep.EnterNew, changeLockStepOnBack(ChangeLockStep.ConfirmNew))
        assertEquals(ChangeLockStep.ChooseType, changeLockStepOnBack(ChangeLockStep.EnterNew))
        assertEquals(ChangeLockStep.VerifyCurrent, changeLockStepOnBack(ChangeLockStep.ChooseType))
        assertNull(changeLockStepOnBack(ChangeLockStep.VerifyCurrent))
    }
}
