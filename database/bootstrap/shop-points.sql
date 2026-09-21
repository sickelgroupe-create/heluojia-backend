CREATE TABLE IF NOT EXISTS shop_point_lot (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, member_id BIGINT NOT NULL, source_key VARCHAR(100) NOT NULL,
 remaining BIGINT NOT NULL, expires_at DATETIME NOT NULL,
 UNIQUE KEY point_source(member_id,source_key), INDEX point_expiry(member_id,expires_at)
);
CREATE TABLE IF NOT EXISTS shop_point_debt(member_id BIGINT PRIMARY KEY,amount BIGINT NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS shop_point_ledger (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, member_id BIGINT NOT NULL, event_key VARCHAR(100) NOT NULL,
 delta BIGINT NOT NULL, kind VARCHAR(30) NOT NULL, order_id BIGINT NULL, note VARCHAR(200) NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY point_event(member_id,event_key)
);
CREATE TABLE IF NOT EXISTS shop_order_points (
 order_id BIGINT PRIMARY KEY, member_id BIGINT NOT NULL, points_used BIGINT NOT NULL,
 discount_amount BIGINT NOT NULL, returned_points BIGINT NOT NULL DEFAULT 0,
 earned_points BIGINT NOT NULL DEFAULT 0, revoked_points BIGINT NOT NULL DEFAULT 0,
 state VARCHAR(20) NOT NULL DEFAULT 'reserved', settled BOOLEAN NOT NULL DEFAULT FALSE,
 ready_at DATETIME NULL, rules_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS shop_point_spend (
 order_id BIGINT NOT NULL, lot_id BIGINT NOT NULL, amount BIGINT NOT NULL,
 returned BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(order_id,lot_id)
);
INSERT IGNORE INTO shop_config(config_key,content) VALUES('loyalty','{"pointsPerYuan":100,"earnPerYuan":1,"maxPercent":10,"expiryDays":365,"settlementDays":15,"afterSaleDays":15,"orderMinutes":30,"commissionDays":15}');
