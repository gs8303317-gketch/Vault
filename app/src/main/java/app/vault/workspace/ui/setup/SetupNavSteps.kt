package app.vault.workspace.ui.setup

/**
 * Pure setup wizard step machine for path-faithful back (and unit tests).
 * Confirm → Enter → ChooseType → null (pop to FirstRun).
 */
enum class SetupWizardStep {
    ChooseType,
    Enter,
    Confirm,
}

/** Previous step, or null when the host should pop the nav back stack. */
fun setupStepOnBack(step: SetupWizardStep): SetupWizardStep? = when (step) {
    SetupWizardStep.Confirm -> SetupWizardStep.Enter
    SetupWizardStep.Enter -> SetupWizardStep.ChooseType
    SetupWizardStep.ChooseType -> null
}

/**
 * Pattern confirm must compare the same encoding used at enter time
 * ([app.vault.workspace.auth.LockRules.encodePattern]).
 */
fun patternSecretsMatch(firstEncoded: String, confirmEncoded: String): Boolean =
    firstEncoded == confirmEncoded && firstEncoded.isNotEmpty()
