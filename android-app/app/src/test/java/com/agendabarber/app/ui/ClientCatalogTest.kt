package com.agendabarber.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientCatalogTest {
    @Test fun filtersMatchBackend() {
        assertEquals("all", ClientFilter.ALL.apiValue)
        assertEquals("recent", ClientFilter.RECENT.apiValue)
        assertEquals("favorites", ClientFilter.FAVORITES.apiValue)
    }

    @Test fun dateInSaoPaulo() {
        assertEquals("28/10/2026", clientLastVisit("2026-10-29T02:30:00Z"))
        assertEquals("Sem atendimentos", clientLastVisit(null))
        assertEquals("Data indisponível", clientLastVisit("bad"))
    }
}
