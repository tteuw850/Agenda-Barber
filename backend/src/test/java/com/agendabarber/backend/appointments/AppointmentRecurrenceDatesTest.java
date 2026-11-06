package com.agendabarber.backend.appointments;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppointmentRecurrenceDatesTest {
    private static AppointmentRecurrenceService.Request request(LocalDate date,
                                                                List<Integer> weekdays,
                                                                int weeks) {
        return new AppointmentRecurrenceService.Request(
                UUID.randomUUID(), UUID.randomUUID(), date,
                LocalTime.of(14, 0), weekdays, weeks, null, null);
    }

    @Test
    void eachThursdayAndFridayRepeatsFourTimesWithoutCreatingRecords() {
        List<LocalDate> dates = AppointmentRecurrenceService.dates(
                request(LocalDate.of(2027, 6, 7), List.of(4, 5), 4));
        assertEquals(8, dates.size());
        assertEquals(LocalDate.of(2027, 6, 10), dates.getFirst());
        assertEquals(LocalDate.of(2027, 7, 2), dates.getLast());
    }

    @Test
    void excludesDaysBeforeTheFirstSelectedDate() {
        List<LocalDate> dates = AppointmentRecurrenceService.dates(
                request(LocalDate.of(2027, 6, 10), List.of(4, 5), 4));
        assertEquals(LocalDate.of(2027, 6, 10), dates.getFirst());
        assertEquals(8, dates.size());
    }

    @Test
    void rejectsDuplicatesAndMoreThanSixtyOccurrences() {
        assertThrows(AppointmentRecurrenceService.InvalidRecurrenceException.class,
                () -> AppointmentRecurrenceService.dates(
                        request(LocalDate.of(2027, 6, 7), List.of(4, 4), 4)));
        assertThrows(AppointmentRecurrenceService.InvalidRecurrenceException.class,
                () -> AppointmentRecurrenceService.dates(
                        request(LocalDate.of(2027, 6, 7), List.of(1, 2, 3), 26)));
    }
}
