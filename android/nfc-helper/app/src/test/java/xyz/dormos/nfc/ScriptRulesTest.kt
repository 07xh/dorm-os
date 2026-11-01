package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class ScriptRulesTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"
    @Test fun rootCommandAllowlistRejectsInjectedArguments() {
        assertTrue(ScriptRules.safeArguments(listOf("--script-list")))
        assertTrue(ScriptRules.safeArguments(listOf("--script-read", id)))
        assertTrue(ScriptRules.safeArguments(listOf("--script-unbind", id)))
        assertTrue(ScriptRules.safeArguments(listOf("--script-bind", id, id)))
        assertFalse(ScriptRules.safeArguments(listOf("--script-run", "foo;reboot")))
        assertFalse(ScriptRules.safeArguments(listOf("--script-bind", id, "../secret")))
        assertFalse(ScriptRules.safeArguments(listOf("--script-save", id, "YQ==", "bad;command")))
        assertFalse(ScriptRules.safeArguments(listOf("--shell", "id")))
    }
}
