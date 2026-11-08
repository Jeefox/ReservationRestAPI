package grevcev;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class QueryPlanIntegrationTest extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void representativeQueriesUsePartialIndexesOnLargeTables() {
        Long user = jdbc.queryForObject("""
                INSERT INTO users (name, email, password, role)
                VALUES ('Plan', 'query-plan@test.com', 'unused', 'USER') RETURNING id
                """, Long.class);
        Long room = jdbc.queryForObject("INSERT INTO rooms (name, capacity) VALUES ('Plan', 2) RETURNING id", Long.class);
        jdbc.update("""
                INSERT INTO reservations (user_id, room_id, start_date, end_date, status)
                SELECT ?, ?, current_date + i, current_date + i + 2, 'PENDING' FROM generate_series(1, 100000) i
                """, user, room);
        jdbc.execute("ANALYZE reservations");
        List<String> booking = jdbc.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS) SELECT exists(SELECT 1 FROM reservations
                WHERE room_id = ? AND start_date < current_date + 100001 AND end_date > current_date + 99999
                  AND status IN ('PENDING', 'APPROVED'))
                """, String.class, room);
        assertThat(String.join("\n", booking)).contains("reservation_active_room_dates_idx");

        jdbc.execute("""
                INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, created_at,
                    status, partition_key, processed_at)
                SELECT md5(i::text)::uuid, 'Reservation', i, 'RESERVATION_CREATED', '{}'::jsonb,
                    localtimestamp - interval '1 day', 'PROCESSED', i::text, localtimestamp
                FROM generate_series(1, 100000) i
                """);
        jdbc.execute("""
                INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, created_at,
                    status, partition_key)
                SELECT md5('ready-' || i)::uuid, 'Reservation', i, 'RESERVATION_CREATED', '{}'::jsonb,
                    localtimestamp, 'NEW', 'ready-' || i FROM generate_series(1, 10) i
                """);
        jdbc.execute("ANALYZE outbox_events");
        List<String> outbox = jdbc.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS) SELECT e.* FROM outbox_events e
                WHERE ((e.status = 'NEW' AND (e.next_attempt_at IS NULL OR e.next_attempt_at <= localtimestamp))
                    OR (e.status = 'PROCESSING' AND e.processing_started_at < localtimestamp - interval '10 minutes'))
                  AND NOT EXISTS (SELECT 1 FROM outbox_events earlier
                    WHERE earlier.partition_key = e.partition_key AND earlier.status <> 'PROCESSED'
                      AND (earlier.created_at, earlier.id) < (e.created_at, e.id))
                ORDER BY e.created_at, e.id LIMIT 10 FOR UPDATE OF e SKIP LOCKED
                """, String.class);
        assertThat(String.join("\n", outbox)).containsAnyOf("outbox_ready_idx", "outbox_unfinished_key_idx");
        System.out.println("Reservation EXPLAIN:\n" + String.join("\n", booking));
        System.out.println("Outbox EXPLAIN:\n" + String.join("\n", outbox));
    }
}
