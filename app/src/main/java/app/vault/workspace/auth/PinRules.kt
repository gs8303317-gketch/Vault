package app.vault.workspace.auth

object PinRules {
    const val PIN_LENGTH = 4

    private val WEAK = setOf(
        "0000", "1111", "2222", "1234", "4321", "1212", "2580", "1122",
    )

    fun isExactFourDigits(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    fun isWeak(pin: String): Boolean = pin in WEAK

    /**
     * @return null if valid, else human-readable reason
     */
    fun validateNewPin(pin: String): String? {
        if (!isExactFourDigits(pin)) return "PIN must be exactly 4 digits"
        if (isWeak(pin)) return "PIN is too easy to guess"
        return null
    }
}
