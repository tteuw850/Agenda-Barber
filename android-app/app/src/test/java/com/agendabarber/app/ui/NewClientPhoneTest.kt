package com.agendabarber.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewClientPhoneTest {
    @Test fun formatsNumber() {
        assertEquals("(31) 3333-4455", maskClientPhone("3133334455"))
        assertEquals("(31) 98888-7777", maskClientPhone("31988887777"))
    }

    @Test fun rejectsPartialNumber() {
        assertTrue(isValidClientPhone(""))
        assertTrue(isValidClientPhone("31988887777"))
        assertFalse(isValidClientPhone("3198888"))
    }
}
