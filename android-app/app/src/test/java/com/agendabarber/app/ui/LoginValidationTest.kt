package com.agendabarber.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginValidationTest {
    @Test fun acceptsUsernameAndPasswordWithoutApiInput() {
        assertTrue(validLoginInput("ebinho", "ExampleTestPass!"))
        assertTrue(validLoginInput("Barber_01", "ExampleTestPass!"))
    }

    @Test fun rejectsInvalidCredentials() {
        assertFalse(validLoginInput("", "ExampleTestPass!"))
        assertFalse(validLoginInput("ab", "ExampleTestPass!"))
        assertFalse(validLoginInput("user@example.com", "ExampleTestPass!"))
        assertFalse(validLoginInput("usuario com espacos", "ExampleTestPass!"))
        assertFalse(validLoginInput("ebinho", ""))
    }
}
