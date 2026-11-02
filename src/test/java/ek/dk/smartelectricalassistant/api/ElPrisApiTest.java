package ek.dk.smartelectricalassistant.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class ElPrisApiTest {

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void rejectsNullDate() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new ElPrisApi("http://localhost").getPrices(null, "DK2"));

        assertEquals(BAD_REQUEST, exception.getStatusCode());
        assertEquals("date is required", exception.getReason());
    }

    @Test
    void rejectsNullOrBlankPriceArea() {
        ElPrisApi api = new ElPrisApi("http://localhost");

        ResponseStatusException nullException = assertThrows(
                ResponseStatusException.class,
                () -> api.getPrices(LocalDate.of(2026, 11, 2), null));
        ResponseStatusException blankException = assertThrows(
                ResponseStatusException.class,
                () -> api.getPrices(LocalDate.of(2026, 11, 2), "  "));

        assertEquals(BAD_REQUEST, nullException.getStatusCode());
        assertEquals("priceArea is required", nullException.getReason());
        assertEquals(BAD_REQUEST, blankException.getStatusCode());
        assertEquals("priceArea is required", blankException.getReason());
    }

    @Test
    void reportsWhenPriceListIsEmpty() throws IOException {
        server.createContext("/", exchange -> {
            byte[] response = "[]".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        ElPrisApi api = new ElPrisApi("http://localhost:" + server.getAddress().getPort());
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> api.getPrices(LocalDate.of(2026, 11, 2), "DK2"));

        assertEquals(NOT_FOUND, exception.getStatusCode());
        assertEquals(
                "No electricity prices found for 2026-11-02 in DK2",
                exception.getReason());
    }
}
