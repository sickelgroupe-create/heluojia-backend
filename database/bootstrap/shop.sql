CREATE TABLE IF NOT EXISTS shop_product (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(120) NOT NULL, category VARCHAR(40) NOT NULL,
 spec VARCHAR(60) NOT NULL, price BIGINT NOT NULL, wholesale BIGINT NOT NULL, stock INT NOT NULL,
 pack INT NOT NULL DEFAULT 12, image VARCHAR(800) NOT NULL, gallery TEXT, detail TEXT,
 enabled BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS shop_member (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, identity_key VARCHAR(150) UNIQUE NOT NULL,
 nickname VARCHAR(80) NOT NULL, phone VARCHAR(30), account_kind VARCHAR(20) NOT NULL,
 enterprise_state VARCHAR(20) NOT NULL DEFAULT 'none', enterprise_data TEXT, enterprise_note VARCHAR(500),
 distributor_state VARCHAR(20) NOT NULL DEFAULT 'none', distributor_data TEXT, distributor_note VARCHAR(500),
 referrer_id BIGINT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS shop_private_file (path VARCHAR(120) PRIMARY KEY, member_id BIGINT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS shop_session (token_hash VARCHAR(64) PRIMARY KEY, member_id BIGINT NOT NULL, expires_at TIMESTAMP NOT NULL);
CREATE TABLE IF NOT EXISTS shop_config (config_key VARCHAR(80) PRIMARY KEY, content LONGTEXT NOT NULL);
CREATE TABLE IF NOT EXISTS shop_media (
 slot VARCHAR(80) PRIMARY KEY, section_name VARCHAR(40), page_name VARCHAR(60), position_name VARCHAR(80),
 suggested_size VARCHAR(80), draft_url VARCHAR(800), published_url VARCHAR(800), previous_url VARCHAR(800), title VARCHAR(100), subtitle VARCHAR(200)
);
CREATE TABLE IF NOT EXISTS shop_address (id BIGINT PRIMARY KEY AUTO_INCREMENT, member_id BIGINT NOT NULL, name VARCHAR(60), phone VARCHAR(30), address VARCHAR(400), is_default BOOLEAN DEFAULT FALSE);
CREATE TABLE IF NOT EXISTS shop_order (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, order_no VARCHAR(40) UNIQUE NOT NULL, member_id BIGINT NOT NULL,
 channel VARCHAR(10) NOT NULL, status VARCHAR(30) NOT NULL, subtotal BIGINT NOT NULL, discount BIGINT NOT NULL,
 amount BIGINT NOT NULL, address_json TEXT NOT NULL, rules_json TEXT NOT NULL, request_key VARCHAR(80) NOT NULL,
 carrier VARCHAR(80), tracking VARCHAR(100), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, paid_at TIMESTAMP NULL,
 UNIQUE KEY order_request(member_id,request_key)
);
CREATE TABLE IF NOT EXISTS shop_order_item (id BIGINT PRIMARY KEY AUTO_INCREMENT, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL, name VARCHAR(120), spec VARCHAR(60), image VARCHAR(800), quantity INT NOT NULL, price BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS shop_after_sale (id BIGINT PRIMARY KEY AUTO_INCREMENT, order_id BIGINT UNIQUE NOT NULL, member_id BIGINT NOT NULL, reason VARCHAR(500), status VARCHAR(30) NOT NULL, note VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS shop_commission (id BIGINT PRIMARY KEY AUTO_INCREMENT, member_id BIGINT NOT NULL, order_id BIGINT UNIQUE NOT NULL, amount BIGINT NOT NULL, status VARCHAR(30) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS shop_withdrawal (id BIGINT PRIMARY KEY AUTO_INCREMENT, member_id BIGINT NOT NULL, amount BIGINT NOT NULL, fee BIGINT NOT NULL, account_info VARCHAR(300) NOT NULL, status VARCHAR(30) NOT NULL, note VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS shop_event (id BIGINT PRIMARY KEY AUTO_INCREMENT, object_type VARCHAR(30), object_id BIGINT, action VARCHAR(80), operator_name VARCHAR(80), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
-- The official catalog is imported explicitly; startup never creates sample merchandise.
INSERT IGNORE INTO shop_config VALUES ('rules','{"minimumWholesale":0,"couponThreshold":30000,"couponDiscount":0,"commissionPercent":0,"withdrawMinimum":1000,"withdrawFee":0,"commissionAfterReceipt":true}');
INSERT IGNORE INTO shop_media(slot,section_name,page_name,position_name,suggested_size,draft_url,published_url,previous_url,title,subtitle) VALUES
('home.hero','零售端','商城首页','顶部主视觉','750 × 640','/shop-assets/heluojia-rich-cream-20260920.jpg','/shop-assets/heluojia-rich-cream-20260920.jpg','','赫洛嘉 · 臻颜新肌','臻颜新肌护肤系列'),
('login.hero','零售端','登录页','品牌产品图','750 × 600','/shop-assets/heluojia-facial-oil-20260920.jpg','/shop-assets/heluojia-facial-oil-20260920.jpg','','欢迎来到赫洛嘉','发现美好，从呵护肌肤开始'),
('business.hero','采购端','采购首页','采购专区横幅','750 × 360','/shop-assets/heluojia-rich-cream-20260920.jpg','/shop-assets/heluojia-rich-cream-20260920.jpg','','专业采购 · 专属权益','整箱采购 · 专享价格'),
('distributor.hero','分销端','入驻申请','招募展示图','750 × 500','/shop-assets/heluojia-rich-cream-20260920.jpg','/shop-assets/heluojia-rich-cream-20260920.jpg','','分享好物，收获美好','申请成为分销员'),
('empty.cart','通用','购物车','空购物车插画','360 × 360','','','','购物车还是空的','去挑选心仪的好物吧');

CREATE TABLE IF NOT EXISTS shop_stock_log (id BIGINT PRIMARY KEY AUTO_INCREMENT,product_id BIGINT NOT NULL,delta BIGINT NOT NULL,balance BIGINT NOT NULL,request_key VARCHAR(100) NOT NULL,note VARCHAR(500),operator_name VARCHAR(80),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY inventory_request(product_id,request_key));
CREATE TABLE IF NOT EXISTS shop_address_request(member_id BIGINT NOT NULL,request_key VARCHAR(80) NOT NULL,PRIMARY KEY(member_id,request_key));
INSERT IGNORE INTO shop_media(slot,section_name,page_name,position_name,suggested_size,draft_url,published_url,previous_url,title,subtitle) VALUES
('home.story','零售端','商城首页','每日呵护横幅','750 × 400','/shop-assets/heluojia-rich-cream-20260920.jpg','/shop-assets/heluojia-rich-cream-20260920.jpg','','把温柔，留给肌肤',''),
('category.cleanser','零售端','商城首页','护肤分类：洁面','160 × 160','/shop-assets/heluojia-toner-20260920.jpg','/shop-assets/heluojia-toner-20260920.jpg','','',''),
('category.serum','零售端','商城首页','护肤分类：精华','160 × 160','/shop-assets/heluojia-facial-oil-20260920.jpg','/shop-assets/heluojia-facial-oil-20260920.jpg','','',''),
('category.cream','零售端','商城首页','护肤分类：面霜','160 × 160','/shop-assets/heluojia-clear-cream-20260920.jpg','/shop-assets/heluojia-clear-cream-20260920.jpg','','',''),
('category.mask','零售端','商城首页','护肤分类：面膜','160 × 160','','','','',''),
('category.toner','零售端','商城首页','护肤分类：爽肤水','160 × 160','','','','','');
