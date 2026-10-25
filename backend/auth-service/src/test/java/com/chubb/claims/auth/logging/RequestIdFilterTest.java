package com.chubb.claims.auth.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearLogContext() {
        MDC.clear();
    }

    @Test
    void aRequestWithoutAnIdGetsANewOneOnTheLogsAndOnTheResponse() throws Exception {
        AtomicReference<String> seenByTheChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> seenByTheChain.set(MDC.get(RequestIdFilter.MDC_KEY)));

        String id = response.getHeader(RequestIdFilter.HEADER);
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(seenByTheChain.get()).isEqualTo(id);
    }

    @Test
    void aPlausibleIdFromTheCallerIsKept() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "trace-42.a_B");
        AtomicReference<String> seenByTheChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenByTheChain.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("trace-42.a_B");
        assertThat(seenByTheChain.get()).isEqualTo("trace-42.a_B");
    }

    @ParameterizedTest
    @ValueSource(strings = {"two words", "forged\r\nline", "", "caf\u00e9", "01234567890123456789012345678901234567890123456789012345678901234"})
    void anImplausibleIdIsReplacedSoItCannotForgeLogLines(String hostile) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, hostile);

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(UUID.fromString(response.getHeader(RequestIdFilter.HEADER))).isNotNull();
    }

    @Test
    void theIdLeavesTheLogContextEvenWhenTheRequestFails() {
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
