package com.hxwl.app61207;

import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class Hxwl61207Application {
    public static void main(String[] args) {
        SpringApplication.run(Hxwl61207Application.class, args);
    }

    @RestController
    static class OrderController {
        private final JdbcTemplate jdbc;

        OrderController(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            this.jdbc.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    id IDENTITY PRIMARY KEY,
                    pickup_time VARCHAR(40) NOT NULL,
                    drink_name VARCHAR(120) NOT NULL,
                    sweetness VARCHAR(40) NOT NULL,
                    ice_level VARCHAR(40) NOT NULL,
                    cups INT NOT NULL,
                    note VARCHAR(500) DEFAULT '',
                    status VARCHAR(40) NOT NULL DEFAULT 'pending',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }

        @GetMapping("/health")
        Map<String, Object> health() {
            return Map.of("status", "ok", "port", 61207);
        }

        @PostMapping("/orders")
        Map<String, Object> create(@RequestBody OrderCreate body) {
            jdbc.update(
                """
                INSERT INTO orders
                (pickup_time, drink_name, sweetness, ice_level, cups, note)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                body.pickupTime(), body.drinkName(), body.sweetness(), body.iceLevel(), body.cups(), body.note()
            );
            return jdbc.queryForMap("SELECT * FROM orders ORDER BY id DESC LIMIT 1");
        }

        @GetMapping("/orders")
        List<Map<String, Object>> list(@RequestParam(required = false) String pickupDate) {
            if (pickupDate == null || pickupDate.isBlank()) {
                return jdbc.queryForList("SELECT * FROM orders ORDER BY pickup_time ASC, id ASC");
            }
            return jdbc.queryForList(
                "SELECT * FROM orders WHERE pickup_time LIKE ? ORDER BY pickup_time ASC, id ASC",
                pickupDate + "%"
            );
        }

        @PatchMapping("/orders/{id}/status")
        Map<String, Object> updateStatus(@PathVariable long id, @RequestBody StatusUpdate body) {
            int changed = jdbc.update("UPDATE orders SET status = ? WHERE id = ?", body.status(), id);
            return Map.of("updated", changed, "id", id, "status", body.status());
        }

        @GetMapping("/orders/unpicked/today")
        List<Map<String, Object>> todayUnpicked() {
            return jdbc.queryForList(
                """
                SELECT * FROM orders
                WHERE pickup_time LIKE FORMATDATETIME(CURRENT_DATE(), 'yyyy-MM-dd') || '%'
                  AND status <> 'picked_up'
                  AND status <> 'cancelled'
                ORDER BY pickup_time ASC
                """
            );
        }
    }

    record OrderCreate(String pickupTime, String drinkName, String sweetness, String iceLevel, int cups, String note) {}
    record StatusUpdate(String status) {}
}
