package com.agendabarber.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginValidationTest {
    @Test fun acceptsValidCredentials() {
        assertTrue(validLoginInput("barbeiro@exemplo.com", "senha123", "https://api.exemplo.com"))
    }

    @Test fun rejectsInvalidFields() {
        assertFalse(validLoginInput("sem-arroba", "senha", "https://api.exemplo.com"))
        assertFalse(validLoginInput("barbeiro@exemplo.com", "", "https://api.exemplo.com"))
        assertFalse(validLoginInput("barbeiro@exemplo.com", "senha", "http://inseguro.com"))
    }
}
