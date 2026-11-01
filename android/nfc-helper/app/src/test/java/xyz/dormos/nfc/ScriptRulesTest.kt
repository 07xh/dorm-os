package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class ScriptRulesTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"
    @Test fun rootCommandAllowlistRejectsInjectedArguments() {
        assertTrue(ScriptRules.safeArguments(listOf("--script-list")))
        assertTrue(ScriptRules.safeArguments(listOf("--folder-list")))
        assertTrue(ScriptRules.safeArguments(listOf("--folder-create", id, "root", "5a2m5Lmg")))
        assertTrue(ScriptRules.safeArguments(listOf("--folder-rename", id, "5pys5qGj")))
        assertTrue(ScriptRules.safeArguments(listOf("--script-move", id, "root")))
        assertTrue(ScriptRules.safeArguments(listOf("--script-rename", id, "Y29kZS5zaA==")))
        assertFalse(ScriptRules.safeArguments(listOf("--folder-create", id, "../bad", "5a2m5Lmg")))
        assertFalse(ScriptRules.safeArguments(listOf("--script-move", id, "../secret")))
        assertFalse(ScriptRules.safeArguments(listOf("--folder-delete", "root")))
        assertTrue(ScriptRules.safeArguments(listOf("--script-read", id)))
        assertTrue(ScriptRules.safeArguments(listOf("--script-unbind", id)))
        assertTrue(ScriptRules.safeArguments(listOf("--script-bind", id, id)))
        assertFalse(ScriptRules.safeArguments(listOf("--script-run", "foo;reboot")))
        assertFalse(ScriptRules.safeArguments(listOf("--script-bind", id, "../secret")))
        assertFalse(ScriptRules.safeArguments(listOf("--script-save", id, "YQ==", "bad;command")))
        assertFalse(ScriptRules.safeArguments(listOf("--shell", "id")))
    }
}
