package com.agendabarber.backend.finance;

import com.agendabarber.backend.appointments.AppointmentService;
import com.agendabarber.backend.finance.ExpenseDtos.ExpenseInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinanceValidationTests {
    @Test
    void rejectsInvalidExpenseAmountsAndBlankDescriptions() {
        LocalDate day = LocalDate.parse("2026-10-29");
        assertThrows(ExpenseService.InvalidExpenseException.class,
                () -> ExpenseService.validate(new ExpenseInput(day, "Aluguel",
                        new BigDecimal("-5.00"), "Fixo")));
        assertThrows(ExpenseService.InvalidExpenseException.class,
                () -> ExpenseService.validate(new ExpenseInput(day, " ",
                        new BigDecimal("10.00"), "Fixo")));
        assertThrows(ExpenseService.InvalidExpenseException.class,
                () -> ExpenseService.validate(new ExpenseInput(day, "Aluguel",
                        new BigDecimal("10.123"), "Fixo")));
        assertThrows(ExpenseService.InvalidExpenseException.class,
                () -> ExpenseService.validate(new ExpenseInput(day, "Aluguel",
                        new BigDecimal("0.00"), "Fixo")));
    }

    @Test
    void acceptsTwoDecimalExpensesAndZeroReceivedAmount() {
        ExpenseInput expense = ExpenseService.validate(new ExpenseInput(
                LocalDate.parse("2026-10-29"), "  Aluguel  ", new BigDecimal("12.50"), " Fixo "));
        assertEquals("Aluguel", expense.description());
        assertEquals("Fixo", expense.category());
        assertEquals(new BigDecimal("12.50"), expense.amount());
        assertEquals(new BigDecimal("0.00"),
                AppointmentService.validateReceivedAmount(BigDecimal.ZERO));
    }

    @Test
    void preventsInvalidOrRoundedReceiptAmounts() {
        assertThrows(AppointmentService.InvalidAppointmentException.class,
                () -> AppointmentService.validateReceivedAmount(new BigDecimal("-0.01")));
        assertThrows(AppointmentService.InvalidAppointmentException.class,
                () -> AppointmentService.validateReceivedAmount(new BigDecimal("1.999")));
        assertThrows(AppointmentService.InvalidAppointmentException.class,
                () -> AppointmentService.validateReceivedAmount(new BigDecimal("12345678901.00")));
        assertThrows(AppointmentService.InvalidAppointmentException.class,
                () -> AppointmentService.validateReceivedAmount(null));
    }
}
