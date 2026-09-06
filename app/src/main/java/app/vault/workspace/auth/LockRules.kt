package app.vault.workspace.auth

/**
 * Validation for vault lock credentials.
 * PIN: 4–6 digits; Password: 6–10 chars; Pattern: 3×3, min [PATTERN_MIN_POINTS] unique cells.
 */
object LockRules {
    const val PIN_MIN = 4
    const val PIN_MAX = 6
    const val PASSWORD_MIN = 6
    const val PASSWORD_MAX = 10
    const val PATTERN_MIN_POINTS = 4
    const val PATTERN_CELLS = 9

    /** @deprecated Use [PIN_MAX]; kept for older call sites expecting a single length. */
    @Deprecated("Variable PIN length 4–6", ReplaceWith("PIN_MAX"))
    const val PIN_LENGTH = PIN_MAX

    private val WEAK_PIN_4 = setOf(
        "0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777", "8888", "9999",
        "1234", "4321", "1212", "2580", "1122", "6969", "1004", "2000", "1313",
    )

    fun isValidPinFormat(pin: String): Boolean =
        pin.length in PIN_MIN..PIN_MAX && pin.all { it.isDigit() }

    fun isWeakPin(pin: String): Boolean {
        if (!isValidPinFormat(pin)) return false
        if (pin.all { it == pin[0] }) return true
        if (pin.length == 4 && pin in WEAK_PIN_4) return true
        // Ascending / descending runs
        val digits = pin.map { it - '0' }
        val asc = digits.zipWithNext().all { (a, b) -> b == a + 1 }
        val desc = digits.zipWithNext().all { (a, b) -> b == a - 1 }
        if (asc || desc) return true
        // Alternating two digits (e.g. 121212)
        if (pin.length >= 4 && pin.length % 2 == 0) {
            val a = pin[0]
            val b = pin[1]
            if (a != b && pin.indices.all { i -> pin[i] == if (i % 2 == 0) a else b }) {
                return true
            }
        }
        return false
    }

    /**
     * @return null if valid for new PIN, else human-readable reason
     */
    fun validateNewPin(pin: String): String? {
        if (!isValidPinFormat(pin)) {
            return "PIN must be $PIN_MIN–$PIN_MAX digits"
        }
        if (isWeakPin(pin)) return "PIN is too easy to guess"
        return null
    }

    fun isValidPasswordFormat(password: String): Boolean =
        password.length in PASSWORD_MIN..PASSWORD_MAX

    fun isWeakPassword(password: String): Boolean {
        if (!isValidPasswordFormat(password)) return false
        if (password.all { it == password[0] }) return true
        return false
    }

    fun validateNewPassword(password: String): String? {
        if (password.length < PASSWORD_MIN) {
            return "Password must be at least $PASSWORD_MIN characters"
        }
        if (password.length > PASSWORD_MAX) {
            return "Password must be at most $PASSWORD_MAX characters"
        }
        if (isWeakPassword(password)) return "Password is too easy to guess"
        return null
    }

    /** Pattern secret: concatenated cell indices '0'..'8', no repeats. */
    fun encodePattern(cells: List<Int>): String {
        require(cells.all { it in 0 until PATTERN_CELLS })
        return cells.joinToString("") { it.toString() }
    }

    fun decodePattern(secret: String): List<Int>? {
        if (secret.isEmpty() || !secret.all { it in '0'..'8' }) return null
        val cells = secret.map { it - '0' }
        if (cells.toSet().size != cells.size) return null // no repeats
        return cells
    }

    fun isValidPatternFormat(secret: String): Boolean {
        val cells = decodePattern(secret) ?: return false
        return cells.size in PATTERN_MIN_POINTS..PATTERN_CELLS
    }

    fun validateNewPattern(secret: String): String? {
        val cells = decodePattern(secret)
            ?: return "Invalid pattern"
        if (cells.size < PATTERN_MIN_POINTS) {
            return "Connect at least $PATTERN_MIN_POINTS dots"
        }
        return null
    }

    fun validateNew(type: LockType, secret: String): String? = when (type) {
        LockType.PIN -> validateNewPin(secret)
        LockType.PASSWORD -> validateNewPassword(secret)
        LockType.PATTERN -> validateNewPattern(secret)
    }

    fun isValidUnlockFormat(type: LockType, secret: String, expectedPinLength: Int? = null): Boolean =
        when (type) {
            LockType.PIN -> {
                if (!isValidPinFormat(secret)) false
                else if (expectedPinLength != null && expectedPinLength in PIN_MIN..PIN_MAX) {
                    secret.length == expectedPinLength
                } else {
                    true
                }
            }
            LockType.PASSWORD -> isValidPasswordFormat(secret)
            LockType.PATTERN -> isValidPatternFormat(secret)
        }
}

/** @deprecated Prefer [LockRules]; thin aliases for transitional call sites. */
@Deprecated("Use LockRules", ReplaceWith("LockRules"))
object PinRules {
    const val PIN_LENGTH = LockRules.PIN_MAX
    fun isExactFourDigits(pin: String): Boolean =
        pin.length == 4 && pin.all { it.isDigit() }
    fun isWeak(pin: String): Boolean = LockRules.isWeakPin(pin)
    fun validateNewPin(pin: String): String? = LockRules.validateNewPin(pin)
}
