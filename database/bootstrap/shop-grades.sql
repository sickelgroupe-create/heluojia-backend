CREATE TABLE IF NOT EXISTS shop_grade(scope CHAR(1) NOT NULL,owner_id BIGINT NOT NULL,tier INT NOT NULL DEFAULT 0,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY(scope,owner_id));
CREATE TABLE IF NOT EXISTS shop_grade_price(product_id BIGINT NOT NULL,tier INT NOT NULL,price BIGINT NOT NULL,PRIMARY KEY(product_id,tier));
CREATE TABLE IF NOT EXISTS shop_company_price(company_id BIGINT NOT NULL,product_id BIGINT NOT NULL,price BIGINT NOT NULL,min_qty INT NOT NULL DEFAULT 1,multiple_qty INT NOT NULL DEFAULT 1,starts_at DATETIME NULL,ends_at DATETIME NULL,enabled BOOLEAN NOT NULL DEFAULT TRUE,PRIMARY KEY(company_id,product_id));
CREATE TABLE IF NOT EXISTS shop_purchase_rule(product_id BIGINT PRIMARY KEY,min_qty INT NOT NULL,multiple_qty INT NOT NULL);
INSERT IGNORE INTO shop_config(config_key,content) VALUES('grades','{"names":["基础","银卡","金卡"],"autoC":false,"autoB":false,"silver":500000,"gold":2000000,"discounts":[10000,10000,10000]}');
