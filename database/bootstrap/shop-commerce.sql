CREATE TABLE IF NOT EXISTS shop_cart (
 member_id BIGINT NOT NULL, channel VARCHAR(1) NOT NULL, revision BIGINT NOT NULL DEFAULT 0,
 items_json TEXT NOT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY(member_id,channel)
);
CREATE TABLE IF NOT EXISTS shop_coupon_campaign (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(80) NOT NULL, threshold_amount BIGINT NOT NULL,
 discount_amount BIGINT NOT NULL, stock INT NOT NULL, claim_limit INT NOT NULL DEFAULT 1,
 valid_days INT NOT NULL DEFAULT 30, starts_at DATETIME NOT NULL, ends_at DATETIME NOT NULL,
 product_ids TEXT NOT NULL, audience VARCHAR(20) NOT NULL DEFAULT 'all', enabled BOOLEAN NOT NULL DEFAULT FALSE,
 claimed INT NOT NULL DEFAULT 0, archived TINYINT NOT NULL DEFAULT 0, terminated_at DATETIME NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS shop_coupon (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, campaign_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 name VARCHAR(80) NOT NULL, threshold_amount BIGINT NOT NULL, discount_amount BIGINT NOT NULL,
 product_ids TEXT NOT NULL, state VARCHAR(20) NOT NULL DEFAULT 'available',
 expires_at DATETIME NOT NULL, order_id BIGINT NULL, claim_key VARCHAR(80) NOT NULL,
 terminated_at DATETIME NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY coupon_claim(member_id,claim_key), INDEX coupon_owner(member_id,state), INDEX coupon_order(order_id), INDEX coupon_campaign(campaign_id)
);
CREATE TABLE IF NOT EXISTS shop_order_coupon (
 order_id BIGINT PRIMARY KEY, coupon_id BIGINT NOT NULL, discount_amount BIGINT NOT NULL,
 snapshot_json TEXT NOT NULL, refunded BOOLEAN NOT NULL DEFAULT FALSE
);
