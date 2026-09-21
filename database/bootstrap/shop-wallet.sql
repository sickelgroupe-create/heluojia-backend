CREATE TABLE IF NOT EXISTS shop_wallet_ledger (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, member_id BIGINT NOT NULL, event_key VARCHAR(100) NOT NULL,
 delta BIGINT NOT NULL, kind VARCHAR(30) NOT NULL, order_id BIGINT NULL, note VARCHAR(200) NOT NULL,
 verified TINYINT NULL DEFAULT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY wallet_event(member_id,event_key), INDEX wallet_owner(member_id,created_at)
);
CREATE TABLE IF NOT EXISTS shop_order_funds (
 order_id BIGINT PRIMARY KEY, member_id BIGINT NOT NULL, wallet_amount BIGINT NOT NULL,
 external_amount BIGINT NOT NULL, wallet_verified TINYINT NULL DEFAULT NULL, state VARCHAR(20) NOT NULL DEFAULT 'reserved',
 refunded_wallet BIGINT NOT NULL DEFAULT 0, refunded_external BIGINT NOT NULL DEFAULT 0
);
