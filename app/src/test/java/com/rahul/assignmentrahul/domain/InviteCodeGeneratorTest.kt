package com.rahul.assignmentrahul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InviteCodeGeneratorTest {

    @Test
    fun `generated codes are valid`() {
        val random = Random(seed = 42)
        repeat(1_000) {
            val code = InviteCodeGenerator.generate(random)
            assertEquals(InviteCodeGenerator.LENGTH, code.length)
            assertTrue("Invalid code $code", InviteCodeGenerator.isValid(code))
        }
    }

    @Test
    fun `generated codes avoid look-alike characters`() {
        val random = Random(seed = 7)
        val allChars = (1..1_000).joinToString("") { InviteCodeGenerator.generate(random) }
        listOf('0', 'O', '1', 'I', 'L').forEach { assertFalse(allChars.contains(it)) }
    }

    @Test
    fun `normalize trims and uppercases user input`() {
        assertEquals("ABC234", InviteCodeGenerator.normalize("  abc234 "))
    }

    @Test
    fun `wrong length or characters are rejected`() {
        assertFalse(InviteCodeGenerator.isValid("ABC23"))
        assertFalse(InviteCodeGenerator.isValid("ABC2O4"))
    }
}
