package com.hxwl.app61207;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
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
        private final MenuItemService menuItemService;

        OrderController(JdbcTemplate jdbc, MenuItemService menuItemService) {
            this.jdbc = jdbc;
            this.menuItemService = menuItemService;
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
        ResponseEntity<Map<String, Object>> create(@RequestBody OrderCreate body) {
            String drinkName = body.drinkName();
            String sweetness = body.sweetness();
            String iceLevel = body.iceLevel();

            if (body.menuItemId() != null) {
                MenuItem menuItem = menuItemService.findById(body.menuItemId());
                if (menuItem == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Menu item not found with id: " + body.menuItemId()));
                }
                if (!"on_shelf".equals(menuItem.getStatus())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Menu item is not on shelf: " + menuItem.getName()));
                }
                drinkName = menuItem.getName();
                if (sweetness == null || sweetness.isBlank()) {
                    sweetness = menuItem.getDefaultSweetness();
                }
                if (iceLevel == null || iceLevel.isBlank()) {
                    iceLevel = menuItem.getDefaultIceLevel();
                }
            }

            if (drinkName == null || drinkName.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "drinkName is required when menuItemId is not provided"));
            }
            if (sweetness == null || sweetness.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "sweetness is required"));
            }
            if (iceLevel == null || iceLevel.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "iceLevel is required"));
            }

            jdbc.update(
                """
                INSERT INTO orders
                (pickup_time, drink_name, sweetness, ice_level, cups, note)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                body.pickupTime(), drinkName, sweetness, iceLevel, body.cups(), body.note()
            );
            return ResponseEntity.ok(jdbc.queryForMap("SELECT * FROM orders ORDER BY id DESC LIMIT 1"));
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

    @RestController
    static class MenuItemController {
        private final MenuItemService menuItemService;

        MenuItemController(MenuItemService menuItemService) {
            this.menuItemService = menuItemService;
        }

        @PostMapping("/menu-items")
        Map<String, Object> create(@RequestBody MenuItemCreate body) {
            MenuItem menuItem = menuItemService.create(body);
            return Map.of(
                "id", menuItem.getId(),
                "name", menuItem.getName(),
                "category", menuItem.getCategory(),
                "defaultSweetness", menuItem.getDefaultSweetness(),
                "defaultIceLevel", menuItem.getDefaultIceLevel(),
                "basePrice", menuItem.getBasePrice(),
                "status", menuItem.getStatus()
            );
        }

        @GetMapping("/menu-items")
        List<MenuItem> list(@RequestParam(required = false) String category,
                            @RequestParam(required = false) String status) {
            return menuItemService.list(category, status);
        }

        @GetMapping("/menu-items/{id}")
        ResponseEntity<Map<String, Object>> getById(@PathVariable long id) {
            MenuItem menuItem = menuItemService.findById(id);
            if (menuItem == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Menu item not found with id: " + id));
            }
            return ResponseEntity.ok(Map.of(
                "id", menuItem.getId(),
                "name", menuItem.getName(),
                "category", menuItem.getCategory(),
                "defaultSweetness", menuItem.getDefaultSweetness(),
                "defaultIceLevel", menuItem.getDefaultIceLevel(),
                "basePrice", menuItem.getBasePrice(),
                "status", menuItem.getStatus()
            ));
        }

        @PatchMapping("/menu-items/{id}/on-shelf")
        ResponseEntity<Map<String, Object>> onShelf(@PathVariable long id) {
            boolean updated = menuItemService.updateStatus(id, "on_shelf");
            if (!updated) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Menu item not found with id: " + id));
            }
            return ResponseEntity.ok(Map.of("id", id, "status", "on_shelf", "updated", true));
        }

        @PatchMapping("/menu-items/{id}/off-shelf")
        ResponseEntity<Map<String, Object>> offShelf(@PathVariable long id) {
            boolean updated = menuItemService.updateStatus(id, "off_shelf");
            if (!updated) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Menu item not found with id: " + id));
            }
            return ResponseEntity.ok(Map.of("id", id, "status", "off_shelf", "updated", true));
        }
    }

    @Service
    static class MenuItemService {
        private final MenuItemRepository menuItemRepository;

        MenuItemService(MenuItemRepository menuItemRepository) {
            this.menuItemRepository = menuItemRepository;
        }

        MenuItem create(MenuItemCreate body) {
            MenuItem menuItem = new MenuItem();
            menuItem.setName(body.name());
            menuItem.setCategory(body.category());
            menuItem.setDefaultSweetness(body.defaultSweetness());
            menuItem.setDefaultIceLevel(body.defaultIceLevel());
            menuItem.setBasePrice(body.basePrice());
            menuItem.setStatus("off_shelf");
            return menuItemRepository.save(menuItem);
        }

        List<MenuItem> list(String category, String status) {
            return menuItemRepository.findAll(category, status);
        }

        MenuItem findById(long id) {
            return menuItemRepository.findById(id);
        }

        boolean updateStatus(long id, String status) {
            return menuItemRepository.updateStatus(id, status) > 0;
        }
    }

    @Repository
    static class MenuItemRepository {
        private final JdbcTemplate jdbc;

        MenuItemRepository(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            this.jdbc.execute("""
                CREATE TABLE IF NOT EXISTS menu_items (
                    id IDENTITY PRIMARY KEY,
                    name VARCHAR(120) NOT NULL,
                    category VARCHAR(80) NOT NULL,
                    default_sweetness VARCHAR(40) NOT NULL,
                    default_ice_level VARCHAR(40) NOT NULL,
                    base_price DECIMAL(10, 2) NOT NULL,
                    status VARCHAR(40) NOT NULL DEFAULT 'off_shelf',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }

        MenuItem save(MenuItem menuItem) {
            jdbc.update(
                """
                INSERT INTO menu_items
                (name, category, default_sweetness, default_ice_level, base_price, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                menuItem.getName(), menuItem.getCategory(), menuItem.getDefaultSweetness(),
                menuItem.getDefaultIceLevel(), menuItem.getBasePrice(), menuItem.getStatus()
            );
            return jdbc.queryForObject(
                "SELECT * FROM menu_items ORDER BY id DESC LIMIT 1",
                new BeanPropertyRowMapper<>(MenuItem.class)
            );
        }

        List<MenuItem> findAll(String category, String status) {
            StringBuilder sql = new StringBuilder("SELECT * FROM menu_items WHERE 1=1");
            java.util.ArrayList<Object> params = new java.util.ArrayList<>();

            if (category != null && !category.isBlank()) {
                sql.append(" AND category = ?");
                params.add(category);
            }
            if (status != null && !status.isBlank()) {
                sql.append(" AND status = ?");
                params.add(status);
            }
            sql.append(" ORDER BY id ASC");

            return jdbc.query(sql.toString(), new BeanPropertyRowMapper<>(MenuItem.class), params.toArray());
        }

        MenuItem findById(long id) {
            List<MenuItem> results = jdbc.query(
                "SELECT * FROM menu_items WHERE id = ?",
                new BeanPropertyRowMapper<>(MenuItem.class),
                id
            );
            return results.isEmpty() ? null : results.get(0);
        }

        int updateStatus(long id, String status) {
            return jdbc.update(
                "UPDATE menu_items SET status = ? WHERE id = ?",
                status, id
            );
        }
    }

    static class MenuItem {
        private Long id;
        private String name;
        private String category;
        private String defaultSweetness;
        private String defaultIceLevel;
        private BigDecimal basePrice;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDefaultSweetness() { return defaultSweetness; }
        public void setDefaultSweetness(String defaultSweetness) { this.defaultSweetness = defaultSweetness; }
        public String getDefaultIceLevel() { return defaultIceLevel; }
        public void setDefaultIceLevel(String defaultIceLevel) { this.defaultIceLevel = defaultIceLevel; }
        public BigDecimal getBasePrice() { return basePrice; }
        public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    record OrderCreate(String pickupTime, String drinkName, Long menuItemId, String sweetness, String iceLevel, int cups, String note) {}
    record StatusUpdate(String status) {}
    record MenuItemCreate(String name, String category, String defaultSweetness, String defaultIceLevel, BigDecimal basePrice) {}
}
