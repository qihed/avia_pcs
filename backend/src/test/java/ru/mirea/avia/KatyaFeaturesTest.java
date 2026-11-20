package ru.mirea.avia;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.mirea.avia.dto.BookingBulkUpdateRequest;
import ru.mirea.avia.filter.CorrelationIdFilter;
import ru.mirea.avia.service.AnalyticsService;
import ru.mirea.avia.service.AuditService;
import ru.mirea.avia.service.BookingManagementService;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression tests for Katya's additions. Does not need a database or modify existing files. */
class KatyaFeaturesTest {
    @Test void correlationIdGeneratedAndMdcCleared() throws Exception {
        var filter = new CorrelationIdFilter();
        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();
        var observed = new AtomicReference<String>();
        filter.doFilter(req, res, (request, response) -> observed.set(MDC.get("correlationId")));
        assertThat(res.getHeader("X-Correlation-Id")).isNotBlank().isEqualTo(observed.get());
        assertThat(UUID.fromString(observed.get())).isNotNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test void incomingCorrelationIdIsPreserved() throws Exception {
        var filter = new CorrelationIdFilter();
        var req = new MockHttpServletRequest();
        req.addHeader("X-Correlation-Id", "case-123");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, (request, response) -> assertThat(MDC.get("correlationId")).isEqualTo("case-123"));
        assertThat(res.getHeader("X-Correlation-Id")).isEqualTo("case-123");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test void correlationIdClearedWhenRequestFails() {
        var filter = new CorrelationIdFilter();
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> { throw new jakarta.servlet.ServletException("test"); }))
                .isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test void emptyCsvIsRejected() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new BookingManagementService(jdbc, mock(AuditService.class));
        assertThatThrownBy(() -> service.previewCsv("  ")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void previewValidCsvDoesNotUpdateDatabase() {
        var jdbc = mock(JdbcTemplate.class);
        var audit = mock(AuditService.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("ABC123"))).thenReturn(1);
        var service = new BookingManagementService(jdbc, audit);
        var result = service.previewCsv("booking_code,status,archived\nABC123,PAID,true\n");
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.validRows()).isEqualTo(1);
        assertThat(result.invalidRows()).isZero();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(audit);
    }

    @Test void previewInvalidRowsReportsErrorsWithoutWrites() {
        var jdbc = mock(JdbcTemplate.class);
        var audit = mock(AuditService.class);
        var service = new BookingManagementService(jdbc, audit);
        var result = service.previewCsv("booking_code,status,archived\nINVALID,PAID,true\nABC123,UNKNOWN,false\nABC124,PAID,maybe\n");
        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.invalidRows()).isEqualTo(3);
        assertThat(result.rows()).allMatch(row -> !row.valid() && row.error() != null);
        verifyNoInteractions(jdbc, audit);
    }

    @Test void applyInvalidCsvIsAtomicAndDoesNotAudit() {
        var jdbc = mock(JdbcTemplate.class);
        var audit = mock(AuditService.class);
        var service = new BookingManagementService(jdbc, audit);
        assertThatThrownBy(() -> service.applyCsv("booking_code,status,archived\nBAD,PAID,true\n"))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(jdbc, audit);
    }

    @Test void applyValidCsvUpdatesAndAudits() {
        var jdbc = mock(JdbcTemplate.class);
        var audit = mock(AuditService.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("ABC123"))).thenReturn(1);
        when(jdbc.update(anyString(), eq("PAID"), eq(true), eq("ABC123"))).thenReturn(1);
        var service = new BookingManagementService(jdbc, audit);
        assertThat(service.applyCsv("booking_code,status,archived\nABC123,PAID,true\n")).isEqualTo(1);
        verify(jdbc).update(contains("UPDATE bookings"), eq("PAID"), eq(true), eq("ABC123"));
        verify(audit).record(eq("BOOKING_CSV_APPLY"), eq("BOOKING"), isNull(), contains("1"));
    }

    @Test void bulkUpdateRequiresAChange() {
        var jdbc = mock(JdbcTemplate.class);
        var audit = mock(AuditService.class);
        var service = new BookingManagementService(jdbc, audit);
        assertThatThrownBy(() -> service.updateSelected(new BookingBulkUpdateRequest(java.util.List.of(UUID.randomUUID()), null, null)))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(jdbc, audit);
    }

    @Test void bulkUpdateStatusAndArchivedAndAudit() {
        var jdbc = mock(JdbcTemplate.class);
        var audit = mock(AuditService.class);
        var id = UUID.randomUUID();
        when(jdbc.update(anyString(), eq("COMPLETED"), eq(true), eq(id))).thenReturn(1);
        var service = new BookingManagementService(jdbc, audit);
        assertThat(service.updateSelected(new BookingBulkUpdateRequest(java.util.List.of(id), "COMPLETED", true))).isEqualTo(1);
        verify(jdbc).update(contains("version=version+1"), eq("COMPLETED"), eq(true), eq(id));
        verify(audit).record(eq("BOOKING_BULK_UPDATE"), eq("BOOKING"), isNull(), contains("1"));
    }

    @Test void analyticsEmptyDatabaseReturnsZeros() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(java.util.List.of());
        var result = new AnalyticsService(jdbc).getSummary();
        assertThat(result.totalBookings()).isZero();
        assertThat(result.activeBookings()).isZero();
        assertThat(result.archivedBookings()).isZero();
        assertThat(result.totalPassengers()).isZero();
        assertThat(result.totalFlights()).isZero();
        assertThat(result.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.bookingsByStatus()).isEmpty();
        assertThat(result.bookingsByFareClass()).isEmpty();
    }

    @Test void auditRecordIncludesCorrelationId() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new AuditService(jdbc);
        MDC.put("correlationId", "test-correlation-1");
        try {
            service.record("BOOKING_CSV_EXPORT", "BOOKING", null, "test export");
            var values = ArgumentCaptor.forClass(Object.class);
            verify(jdbc).update(contains("INSERT INTO audit_events"), values.capture(), values.capture(), values.capture(), values.capture(), values.capture(), values.capture(), values.capture());
            assertThat(values.getAllValues().get(6)).isEqualTo("test-correlation-1");
        } finally {
            MDC.remove("correlationId");
        }
    }
}
