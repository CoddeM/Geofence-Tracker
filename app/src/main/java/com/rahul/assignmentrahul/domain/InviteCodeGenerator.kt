package com.rahul.assignmentrahul.domain

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

object InviteCodeGenerator {
    const val LENGTH = 6

    // No 0/O or 1/I/L, so a code can be read out loud or typed without mistakes.
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = SecureRandom().asKotlinRandom()): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun normalize(input: String): String = input.trim().uppercase()

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}
