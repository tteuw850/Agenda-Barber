package com.agendabarber.backend.appointments;

import com.agendabarber.backend.common.ReadRetryExecutor;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentControllerReadRetryTests {
    @Test
    void getEndpointRetriesSqlState08() {
        AppointmentService appointments = mock(AppointmentService.class);
        AppointmentController controller = new AppointmentController(appointments, new ReadRetryExecutor());
        UUID barber = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Jwt jwt = jwt(barber);
        when(appointments.get(barber, id)).thenThrow(failure()).thenReturn(null);
        assertNull(controller.get(jwt, id));
        verify(appointments, times(2)).get(barber, id);
    }

    @Test
    void mutationDoesNotUseReadRetryEvenOnSqlState08() {
        AppointmentService appointments = mock(AppointmentService.class);
        AppointmentController controller = new AppointmentController(appointments, new ReadRetryExecutor());
        UUID barber = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Jwt jwt = jwt(barber);
        when(appointments.transition(barber, id, "IN_PROGRESS")).thenThrow(failure());
        assertThrows(DataAccessResourceFailureException.class, () -> controller.start(jwt, id));
        verify(appointments, times(1)).transition(barber, id, "IN_PROGRESS");
    }

    private static Jwt jwt(UUID barber) {
        return Jwt.withTokenValue("test").header("alg", "none")
                .subject(UUID.randomUUID().toString()).claim("barber_id", barber.toString()).build();
    }

    private static DataAccessResourceFailureException failure() {
        return new DataAccessResourceFailureException("offline", new SQLException("connection reset", "08006"));
    }
}
