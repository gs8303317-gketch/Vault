package app.vault.workspace.auth

/**
 * How the user authenticates. Credential bytes always feed PBKDF2 → KEK;
 * type is UX + validation metadata stored in [LockPrefs] (never in vault.hdr).
 */
enum class LockType {
    PIN,
    PASSWORD,
    PATTERN,
    ;

    val displayName: String
        get() = when (this) {
            PIN -> "PIN"
            PASSWORD -> "Password"
            PATTERN -> "Pattern"
        }

    val unlockPrompt: String
        get() = when (this) {
            PIN -> "Enter your PIN"
            PASSWORD -> "Enter your password"
            PATTERN -> "Draw your pattern"
        }
}
