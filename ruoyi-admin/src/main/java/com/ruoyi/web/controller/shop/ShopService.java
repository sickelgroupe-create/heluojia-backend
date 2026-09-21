package com.ruoyi.web.controller.shop;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import com.alibaba.fastjson2.*;

@Service
public class ShopService {
 @Autowired @org.springframework.context.annotation.Lazy ShopWechatPay wechatPay;
 public boolean wechatPaymentReady(){return wechatPay.ready();}
 public boolean wechatPaymentPaid(long id){return wechatPay.paid(id);}
 public boolean closeWechatPayment(Map<String,Object> order){return wechatPay.closeBeforeRelease(order);}
 public boolean wechatRefundConfirmed(long id){return wechatPay.refundConfirmed(id);}
 public Map<String,Object> lockWechatOrder(long id){return wechatPay.lock(id);}
 public void validateWechatPayment(Map<String,Object> order){partner.validatePayment(order);}
 @Autowired @org.springframework.context.annotation.Lazy ShopDistribution distribution;
 @Autowired @org.springframework.context.annotation.Lazy ShopGrades grades;
 @Autowired @org.springframework.context.annotation.Lazy ShopRebate rebate;
 @Autowired @org.springframework.context.annotation.Lazy ShopWarehouse warehouse;
 @Autowired @org.springframework.context.annotation.Lazy ShopDelivery delivery;
 @Autowired @org.springframework.context.annotation.Lazy ShopMarketing marketing;
 @Autowired public JdbcTemplate db;
 @Autowired @org.springframework.context.annotation.Lazy ShopReviews reviews;
 @Autowired @org.springframework.context.annotation.Lazy ShopInvoice invoice;
 @Autowired @org.springframework.context.annotation.Lazy ShopCredit credit;
 @Autowired @org.springframework.context.annotation.Lazy ShopBank bank;
 @Autowired @org.springframework.context.annotation.Lazy ShopPartner partner;
 @Autowired @org.springframework.context.annotation.Lazy ShopCompany company;
 @Autowired DataSource dataSource;
 @Autowired @org.springframework.context.annotation.Lazy ShopCommerce commerce;
 @Autowired @org.springframework.context.annotation.Lazy ShopWallet wallet;
 @Autowired @org.springframework.context.annotation.Lazy ShopPoints points;
 @Value("${shop.demo:false}") public boolean demo;
 @Value("${shop.test-data:false}") public boolean testData;
 @Value("${shop.official-display:false}") public boolean officialDisplay;
 @PostConstruct public void init(){ new ResourceDatabasePopulator(new ClassPathResource("shop.sql")).execute(dataSource);if(db.queryForObject("select CHARACTER_MAXIMUM_LENGTH from information_schema.COLUMNS where TABLE_SCHEMA=DATABASE() and TABLE_NAME='shop_event' and COLUMN_NAME='action'",Long.class)<2000)db.execute("ALTER TABLE shop_event MODIFY action VARCHAR(2000)");
 new ResourceDatabasePopulator(new ClassPathResource("shop-notice.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-chat.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-commerce.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-wallet.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-wechat-pay.sql")).execute(dataSource);
 column("shop_wallet_ledger","verified","TINYINT NULL DEFAULT NULL");column("shop_order_funds","wallet_verified","TINYINT NULL DEFAULT NULL");
 new ResourceDatabasePopulator(new ClassPathResource("shop-points.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-company.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-bank.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-credit.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-statement.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-invoice.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-review.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-distribution.sql")).execute(dataSource);
 column("shop_distribution_rule","basis","VARCHAR(20) NOT NULL DEFAULT 'net'");
 new ResourceDatabasePopulator(new ClassPathResource("shop-grades.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-rebate.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-account-appeal.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-exchange.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-warehouse.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-delivery.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-marketing.sql")).execute(dataSource);
 column("shop_warehouse_stock","campaign_reserved","BIGINT NOT NULL DEFAULT 0");column("shop_warehouse_stock","warning_qty","BIGINT NOT NULL DEFAULT 5");
 new ResourceDatabasePopulator(new ClassPathResource("shop-catalog.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-promo.sql")).execute(dataSource);
 new ResourceDatabasePopulator(new ClassPathResource("shop-partner.sql")).execute(dataSource);column("shop_partner_profile","mode","VARCHAR(20) NOT NULL DEFAULT 'legacy'");db.update("update shop_partner_profile set mode=case when enabled=0 then 'blocked' when tier=3 then 'fixed' else 'auto' end where mode='legacy'");column("shop_order","business_type","VARCHAR(20) NULL");db.update("update shop_order set business_type=if(channel='B','enterprise','retail') where business_type is null");column("shop_order_item","standard_price","BIGINT NULL");
 column("shop_product","archived","TINYINT NOT NULL DEFAULT 0");column("shop_withdrawal","payment_mode","VARCHAR(30) NOT NULL DEFAULT 'simulation'");column("shop_order","is_test","TINYINT NOT NULL DEFAULT 0");column("shop_member","is_test","TINYINT NOT NULL DEFAULT 0");
 db.update("insert ignore into shop_config(config_key,content) values('product_series','[]')");column("shop_product","series","VARCHAR(60) NOT NULL DEFAULT ''");
 column("shop_product","brand","VARCHAR(60) NOT NULL DEFAULT ''");column("shop_product","retail_enabled","TINYINT NOT NULL DEFAULT 1");column("shop_product","wholesale_enabled","TINYINT NOT NULL DEFAULT 1");column("shop_product","sort_order","INT NOT NULL DEFAULT 0");
 column("shop_product","video_url","VARCHAR(200) NOT NULL DEFAULT ''");db.execute("CREATE TABLE IF NOT EXISTS shop_product_video(path VARCHAR(200) PRIMARY KEY,size BIGINT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

 column("shop_product","product_code","VARCHAR(60) NULL");db.update("update shop_product set product_code=concat('P',lpad(id,10,'0')) where product_code is null or product_code=''");if(db.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and table_name='shop_product' and index_name='uk_product_code'",Integer.class)==0)db.execute("alter table shop_product add unique index uk_product_code(product_code)");
 column("shop_product","minimum_tier","INT NOT NULL DEFAULT 0");column("shop_member","avatar","VARCHAR(200) NOT NULL DEFAULT ''");column("shop_member","contact_phone","VARCHAR(20) NOT NULL DEFAULT ''");column("shop_address","province","VARCHAR(40) NOT NULL DEFAULT ''");column("shop_address","city","VARCHAR(40) NOT NULL DEFAULT ''");column("shop_address","district","VARCHAR(40) NOT NULL DEFAULT ''");column("shop_address","detail","VARCHAR(300) NOT NULL DEFAULT ''");column("shop_review","anonymous","TINYINT NOT NULL DEFAULT 0");column("shop_review","images_json","TEXT NULL");db.execute("CREATE TABLE IF NOT EXISTS shop_public_file(path VARCHAR(200) PRIMARY KEY,member_id BIGINT NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
 column("shop_order","manual_after_sale_until","DATETIME NULL");column("shop_order_item","review_until","DATETIME NULL");
 column("shop_credit_repayment","allocation_scope","VARCHAR(20) NOT NULL DEFAULT 'specified'");
 column("shop_company","certified_member_id","BIGINT NULL");
 db.update("update shop_company set certified_member_id=owner_id where certified_member_id is null");
 column("shop_bank_order","settings_json","TEXT NULL");
 column("shop_order","company_id","BIGINT NULL");
 int migratedOrders=db.update("update shop_order o join shop_company c on c.certified_member_id=o.member_id set o.company_id=c.id where o.channel='B' and o.company_id is null");if(migratedOrders>0)event("company",0,"将历史采购订单归入原认证企业："+migratedOrders+"笔","系统");
 column("shop_order_item","net_amount","BIGINT NULL");
 column("shop_order_item","points_used","BIGINT NOT NULL DEFAULT 0");
 column("shop_order","received_at","DATETIME NULL");
 column("shop_commission","ready_at","DATETIME NULL");
 db.execute("CREATE TABLE IF NOT EXISTS shop_identity (identity_key VARCHAR(150) PRIMARY KEY,member_id BIGINT NOT NULL,INDEX identity_member(member_id))");
 db.update("INSERT IGNORE INTO shop_identity SELECT identity_key,id FROM shop_member");
 db.execute("CREATE TABLE IF NOT EXISTS shop_shipment(id BIGINT PRIMARY KEY AUTO_INCREMENT,order_id BIGINT NOT NULL,carrier VARCHAR(80),tracking VARCHAR(100),items_json TEXT NOT NULL,received BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
 db.execute("CREATE TABLE IF NOT EXISTS shop_case(id BIGINT PRIMARY KEY AUTO_INCREMENT,order_id BIGINT NOT NULL,member_id BIGINT NOT NULL,type VARCHAR(20) NOT NULL,status VARCHAR(30) NOT NULL,items_json TEXT NOT NULL,amount BIGINT NOT NULL,reason VARCHAR(500),note VARCHAR(500),return_address VARCHAR(500),return_carrier VARCHAR(80),return_tracking VARCHAR(100),replacement_carrier VARCHAR(80),replacement_tracking VARCHAR(100),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
 column("shop_order","refunded_amount","BIGINT NOT NULL DEFAULT 0");
 column("shop_commission","original_amount","BIGINT NULL");
 db.update("update shop_commission set original_amount=amount where original_amount is null");db.update("update shop_commission set amount=0 where status='reversed'");
 column("shop_product","pricing_json","TEXT NULL");
 column("shop_product","family_id","BIGINT NOT NULL DEFAULT 0");
 for(String key:new String[]{"config_draft","config_live","config_previous"})column("shop_media",key,"TEXT NULL");
 db.execute("CREATE TABLE IF NOT EXISTS shop_credentials(username VARCHAR(40) PRIMARY KEY,member_id BIGINT NOT NULL UNIQUE,password_hash VARCHAR(100) NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
 db.execute("CREATE TABLE IF NOT EXISTS shop_recovery(member_id BIGINT PRIMARY KEY,code_hash VARCHAR(64) NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
 db.execute("CREATE TABLE IF NOT EXISTS shop_withdraw_request(member_id BIGINT NOT NULL,request_key VARCHAR(80) NOT NULL,amount BIGINT NOT NULL,account_hash VARCHAR(64) NOT NULL,PRIMARY KEY(member_id,request_key))");
 db.execute("CREATE TABLE IF NOT EXISTS shop_payment(order_id BIGINT PRIMARY KEY,member_id BIGINT NOT NULL,amount BIGINT NOT NULL,mode VARCHAR(30) NOT NULL DEFAULT 'simulation',paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
 db.execute("CREATE TABLE IF NOT EXISTS shop_finance_event(event_key VARCHAR(80) PRIMARY KEY,kind VARCHAR(20) NOT NULL,object_id BIGINT NOT NULL,amount BIGINT NOT NULL,fee BIGINT NOT NULL DEFAULT 0,occurred_at DATETIME NULL,INDEX finance_time(occurred_at))");
 db.execute("CREATE TABLE IF NOT EXISTS shop_commission_event(id BIGINT PRIMARY KEY AUTO_INCREMENT,member_id BIGINT NOT NULL,order_id BIGINT NOT NULL,delta BIGINT NOT NULL,event_key VARCHAR(80) UNIQUE NOT NULL,note VARCHAR(200),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
 db.update("INSERT IGNORE INTO shop_payment(order_id,member_id,amount,paid_at) SELECT id,member_id,amount,paid_at FROM shop_order WHERE paid_at IS NOT NULL");
 db.update("INSERT IGNORE INTO shop_finance_event SELECT concat('pay_',order_id),'payment',order_id,amount,0,paid_at FROM shop_payment");
 db.update("INSERT IGNORE INTO shop_finance_event SELECT concat('case_',id),'refund',order_id,amount,0,null FROM shop_case WHERE status='refunded'");
 db.update("INSERT IGNORE INTO shop_finance_event SELECT concat('legacy_',a.id),'refund',a.order_id,o.amount,0,null FROM shop_after_sale a JOIN shop_order o ON o.id=a.order_id WHERE a.status='refunded'");
 db.update("INSERT IGNORE INTO shop_finance_event SELECT concat('withdraw_',id),'withdrawal',id,amount,fee,null FROM shop_withdrawal WHERE status='paid'");
 for(Map<String,Object> c:db.queryForList("select id,type,items_json,status from shop_case")){JSONArray items=JSON.parseArray(str(c.get("items_json")));boolean changed=false;for(int i=0;i<items.size();i++){JSONObject item=items.getJSONObject(i);if(!item.containsKey("unshipped")){long released=0;if("refund".equals(c.get("type")))released=db.queryForObject("select coalesce(sum(delta),0) from shop_stock_log where product_id=? and request_key=?",Long.class,item.getLongValue("productId"),"case_refund_"+c.get("id"));item.put("unshipped",released);changed=true;}}if(changed)db.update("update shop_case set items_json=? where id=?",items.toJSONString(),c.get("id"));}
 db.update("INSERT IGNORE INTO shop_commission_event(member_id,order_id,delta,event_key,note) SELECT member_id,order_id,original_amount,concat('payment_',order_id),'历史非实收付款佣金' FROM shop_commission");
 db.update("INSERT IGNORE INTO shop_commission_event(member_id,order_id,delta,event_key,note) SELECT c.member_id,c.order_id,c.amount-c.original_amount,concat('legacy_refund_',c.order_id),'历史退款佣金扣回' FROM shop_commission c WHERE c.amount<c.original_amount AND NOT EXISTS (SELECT 1 FROM shop_commission_event e WHERE e.order_id=c.order_id AND e.delta<0)");
 column("shop_coupon_campaign","return_on_refund","TINYINT NOT NULL DEFAULT 1");column("shop_coupon","return_on_refund","TINYINT NOT NULL DEFAULT 1");
 column("shop_coupon_campaign","archived","TINYINT NOT NULL DEFAULT 0");column("shop_coupon_campaign","terminated_at","DATETIME NULL");column("shop_coupon","terminated_at","DATETIME NULL");
 if(db.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and table_name='shop_coupon' and index_name='coupon_campaign'",Integer.class)==0)db.execute("alter table shop_coupon add index coupon_campaign(campaign_id)");
 column("shop_member","referrer_expires_at","DATETIME NULL");column("shop_company_referral","expires_at","DATETIME NULL");
 column("shop_member","enabled","BOOLEAN NOT NULL DEFAULT TRUE");
 db.update("delete from shop_media where slot like 'category.custom_%' and position_name in ('洁面分类图','精华分类图','面霜分类图','面膜分类图','爽肤水分类图') and coalesce(draft_url,'')='' and coalesce(published_url,'')=''");
 column("shop_product","version","BIGINT NOT NULL DEFAULT 0");
 column("shop_media","has_previous","BOOLEAN NOT NULL DEFAULT FALSE");
 column("shop_media","default_url","VARCHAR(800) NULL");
 db.update("insert ignore into shop_media(slot,section_name,page_name,position_name,suggested_size,draft_url,published_url,previous_url,title,subtitle,default_url) values('brand.logo','通用','店铺标识','首页店铺标识','160 × 160','','','','','','')");
 column("shop_order","expires_at","DATETIME NULL");
 column("shop_order","close_reason","VARCHAR(100) NULL");
 db.update("update shop_media set has_previous=1 where previous_url<>''");
 db.update("update shop_media set default_url=case when slot in ('home.hero','home.story','business.hero','distributor.hero') then '/shop-assets/hero.png' when slot in ('login.hero','category.serum') then '/shop-assets/serum.png' when slot='category.cleanser' then '/shop-assets/cleanser.png' when slot='category.cream' then '/shop-assets/cream.png' else '' end where default_url is null or (slot in ('category.serum','category.cleanser','category.cream') and default_url='')");
 column("shop_shipment","received_at","DATETIME NULL");column("shop_shipment","warehouse_id","BIGINT NOT NULL DEFAULT 1");column("shop_order","shipping_amount","BIGINT NOT NULL DEFAULT 0");column("shop_order","refunded_shipping_amount","BIGINT NOT NULL DEFAULT 0");column("shop_case","return_due_at","DATETIME NULL");column("shop_case","reason_code","VARCHAR(20) NOT NULL DEFAULT 'customer'");column("shop_case","proofs_json","TEXT NULL");column("shop_case","replacement_warehouse_id","BIGINT NULL");column("shop_case","return_shipping_payer","VARCHAR(20) NOT NULL DEFAULT 'customer'");
 db.execute("create table if not exists shop_assistance_request(actor varchar(100) not null,request_key varchar(80) not null,payload_hash varchar(64) not null,order_id bigint not null,created_at datetime not null default current_timestamp,primary key(actor,request_key))");
 column("shop_withdrawal","due_at","DATETIME NULL");column("shop_withdrawal","submitted_account","VARCHAR(100) NULL");column("shop_withdrawal","reviewer","VARCHAR(100) NULL");column("shop_withdrawal","handled_at","DATETIME NULL");
 column("shop_case","shipping_amount","BIGINT NOT NULL DEFAULT 0");column("shop_case","shipping_request_key","VARCHAR(80) NULL");
 db.update("update shop_order set expires_at=date_add(created_at,interval 30 minute) where expires_at is null");
 }
 private void column(String table,String name,String definition){if(db.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name=? and column_name=?",Integer.class,table,name)==0)db.execute("alter table "+table+" add column "+name+" "+definition);}
 public static Long referral(Object value){try{long id=Long.parseLong(str(value));return id>0?id:null;}catch(Exception ignored){return null;}}
 public static long integer(Object value,long min,long max,String message){try{long n=new java.math.BigDecimal(str(value)).longValueExact();check(n>=min&&n<=max,message);return n;}catch(ArithmeticException|NumberFormatException e){throw new IllegalArgumentException(message);}}
 @Transactional public void expireOrder(long id){Map<String,Object> owner=one("select member_id from shop_order where id=?",id);one("select id from shop_member where id=? for update",owner.get("member_id"));Map<String,Object> o=one("select * from shop_order where id=? for update",id);if("pending".equals(o.get("status"))&&expired(o)){if(!wechatPay.closeBeforeRelease(o))return;rebate.release(id);credit.release(id);bank.close(id);commerce.release(id);wallet.release(id);points.release(id);restoreStock(id);marketing.release(id);db.update("update shop_order set status='cancelled',close_reason='超时未付款，已自动关闭' where id=?",id);event("order",id,"超时关闭并释放库存","系统");}}
 private boolean expired(Map<String,Object> o){return Boolean.TRUE.equals(db.queryForObject("select expires_at<=now() from shop_order where id=?",Boolean.class,o.get("id")));}
 @Transactional public void inventory(long id,long delta,String key,String note,String who){Map<String,Object> p=one("select * from shop_product where id=? for update",id);if(db.queryForObject("select count(*) from shop_stock_log where product_id=? and request_key=?",Long.class,id,key)>0)return;warehouse.change(id,delta,key,note,who);long stock=number(p.get("stock"))+delta;check(stock>=0&&stock<=100000000,"调整后库存超出范围");db.update("update shop_product set stock=?,version=version+1 where id=?",stock,id);db.update("insert into shop_stock_log(product_id,delta,balance,request_key,note,operator_name) values(?,?,?,?,?,?)",id,delta,stock,key,note,who);}
 public long rebateReturned(long oid){return rebate.returned(oid);}
 public long rebateUsed(long oid){return rebate.used(oid);}
 public void returnOrderCoupon(long oid){rebate.refund(oid);invoice.refund(oid);credit.refund(oid);wallet.refund(oid);commerce.refund(oid);}
 public void orderReceived(long oid){marketing.refresh(oid);rebate.refresh(oid);distribution.refresh(oid);points.received(oid);reviews.received(oid);points.refunded(oid);Map<String,Object> o=one("select status,rules_json from shop_order where id=?",oid);if("completed".equals(o.get("status"))){JSONObject snapshot=JSON.parseObject(str(o.get("rules_json")));JSONObject loyalty=snapshot.getJSONObject("loyalty");long days=loyalty==null?15:loyalty.getLongValue("commissionDays");db.update("update shop_commission set ready_at=date_add(now(),interval ? day) where order_id=? and status='pending' and ready_at is null",days,oid);}}
 @Transactional public void settleCommission(long oid){Map<String,Object> o=one("select status from shop_order where id=? for update",oid);if(!"completed".equals(o.get("status")))return;if(db.queryForObject("select count(*) from shop_case where order_id=? and status not in ('refunded','completed','rejected')",Long.class,oid)>0)return;db.update("update shop_commission set status='available' where order_id=? and status='pending' and ready_at<=now()",oid);}
 public String companyOrderScope(long uid){return company.orderScope(uid);}
 public Map<String,Object> companyOrder(long uid,long oid,String action,boolean lock){Map<String,Object> o=one("select * from shop_order where id=?"+(lock?" for update":""),oid);company.authorize(uid,o,action);return o;}
 public Map<String,Object> companyCase(long uid,long id,String action,boolean lock){Map<String,Object> c=one("select * from shop_case where id=?"+(lock?" for update":""),id);companyOrder(uid,number(c.get("order_id")),action,false);return c;}
 public void companyAccess(long uid){company.context(uid);}
 public void authorizeOrder(long uid,Map<String,Object> o,String action){company.authorize(uid,o,action);}
 public void companyPurchase(long uid){company.purchase(uid);}
 public JdbcTemplate jdbc(){return db;} public boolean isDemo(){return demo&&!officialDisplay;} public boolean isOfficialDisplay(){return officialDisplay;}
 public String officialOrder(String orderId){return officialDisplay?" and exists(select 1 from shop_order display_order join shop_member display_member on display_member.id=display_order.member_id where display_order.id="+orderId+" and display_order.is_test=0 and display_member.is_test=0 and not exists(select 1 from shop_payment display_payment where display_payment.order_id=display_order.id and display_payment.mode='simulation'))":"";}
 public String verifiedPayment(String orderId){return officialDisplay?officialOrder(orderId)+" and exists(select 1 from shop_payment verified where verified.order_id="+orderId+" and verified.mode<>'simulation')":"";}
 public boolean walletPayment(long oid){return wallet.paidByWallet(oid);}
 public boolean rebatePayment(long oid){return rebate.paidByRebate(oid);}
 public static void check(boolean ok,String message){if(!ok) throw new IllegalArgumentException(message);}
 public static long number(Object x){return x==null?0:Long.parseLong(x.toString());}
 public static String str(Object x){return x==null?"":x.toString();}
 public static java.sql.Timestamp timestamp(Object value){if(value instanceof java.sql.Timestamp)return (java.sql.Timestamp)value;if(value instanceof java.time.LocalDateTime)return java.sql.Timestamp.valueOf((java.time.LocalDateTime)value);if(value instanceof java.util.Date)return new java.sql.Timestamp(((java.util.Date)value).getTime());return java.sql.Timestamp.valueOf(str(value).replace('T',' '));}
 public Map<String,Object> one(String sql,Object... args){List<Map<String,Object>> r=db.queryForList(sql,args);check(!r.isEmpty(),"记录不存在");return r.get(0);}
 public static java.sql.Timestamp workingDeadline(java.sql.Timestamp start,long days){java.time.LocalDateTime t=start.toLocalDateTime();while(days>0){t=t.plusDays(1);if(t.getDayOfWeek()!=java.time.DayOfWeek.SATURDAY&&t.getDayOfWeek()!=java.time.DayOfWeek.SUNDAY)days--;}return java.sql.Timestamp.valueOf(t);}
 public JSONArray productSeries(){return JSON.parseArray(str(one("select content from shop_config where config_key='product_series'").get("content")));}
 public JSONObject rules(){JSONObject r=JSON.parseObject(str(one("select content from shop_config where config_key='rules'").get("content")));r.putIfAbsent("simulationPaymentEnabled",true);r.putIfAbsent("walletPaymentEnabled",true);if(!r.containsKey("withdrawWorkingDays"))r.put("withdrawWorkingDays",3);if(!r.containsKey("categories"))r.put("categories",Arrays.asList("洁面","精华","面霜","面膜","爽肤水"));return r;}
 public void event(String type,long id,String action,String who){org.springframework.jdbc.support.KeyHolder key=new org.springframework.jdbc.support.GeneratedKeyHolder();db.update(connection->{java.sql.PreparedStatement p=connection.prepareStatement("insert into shop_event(object_type,object_id,action,operator_name) values(?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);p.setString(1,type);p.setLong(2,id);p.setString(3,action);p.setString(4,who);return p;},key);db.update("insert into shop_notice_outbox(event_id) values(?)",key.getKey().longValue());}
 public String hash(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 public Map<String,Object> member(String authorization){check(authorization!=null&&authorization.startsWith("Bearer "),"请先登录");List<Map<String,Object>> rows=db.queryForList("select m.* from shop_member m join shop_session s on s.member_id=m.id where s.token_hash=? and s.expires_at>now()",hash(authorization.substring(7)));check(!rows.isEmpty(),"登录已过期，请重新登录");Map<String,Object> m=rows.get(0);check(Boolean.TRUE.equals(m.get("enabled"))||"1".equals(str(m.get("enabled"))),"账号已停用，请联系客服");m.remove("identity_key");List<Map<String,Object>> membership=db.queryForList("select c.name,c.owner_id,c.certified_member_id,cm.role,cm.enabled member_enabled,owner.enabled owner_enabled,certified.enterprise_state,certified.enterprise_data company_enterprise_data from shop_company_member cm join shop_company c on c.id=cm.company_id join shop_member owner on owner.id=c.owner_id join shop_member certified on certified.id=c.certified_member_id where cm.member_id=?",m.get("id"));boolean access="approved".equals(m.get("enterprise_state"));if(!membership.isEmpty()){Map<String,Object> cm=membership.get(0);access=(Boolean.TRUE.equals(cm.get("member_enabled"))||"1".equals(str(cm.get("member_enabled"))))&&(Boolean.TRUE.equals(cm.get("owner_enabled"))||"1".equals(str(cm.get("owner_enabled"))))&&"approved".equals(cm.get("enterprise_state"));m.put("company_role",cm.get("role"));m.put("company_name",cm.get("name"));m.put("company_owner",cm.get("owner_id"));m.put("company_pricing_member",cm.get("certified_member_id"));m.put("company_enterprise_state",cm.get("enterprise_state"));JSONObject profile=JSON.parseObject(str(cm.get("company_enterprise_data")).isEmpty()?"{}":str(cm.get("company_enterprise_data")));JSONObject visible=new JSONObject();for(String field:Arrays.asList("company","creditCode","name","phone"))visible.put(field,profile.get(field));m.put("company_enterprise_data",visible.toJSONString());}m.put("enterprise_access",access);return m;}
 @Transactional public Map<String,Object> login(String identity,String kind,String phone,Long referrer){
  List<Map<String,Object>> alias=db.queryForList("select member_id from shop_identity where identity_key=?",identity);
  int created=0;Map<String,Object> m;
  if(alias.isEmpty()){created=db.update("insert ignore into shop_member(identity_key,nickname,phone,account_kind) values(?,?,?,?)",identity,"新朋友",phone,kind);m=one("select * from shop_member where identity_key=? for update",identity);db.update("insert ignore into shop_identity values(?,?)",identity,m.get("id"));}
  else m=one("select * from shop_member where id=? for update",alias.get(0).get("member_id"));long id=number(m.get("id"));
  if(created==1&&referrer!=null&&referrer!=id&&m.get("referrer_id")==null){List<Map<String,Object>> r=db.queryForList("select id from shop_member where id=? and distributor_state='approved' and enabled=1",referrer);if(!r.isEmpty()){db.update("update shop_member set referrer_id=?,referrer_expires_at=? where id=? and referrer_id is null",referrer,distribution.newExpiry(),id);event("referral",id,"首次注册绑定推荐人 "+referrer,str(id));}}
  check(Boolean.TRUE.equals(m.get("enabled"))||"1".equals(str(m.get("enabled"))),"账号已停用，请联系客服");byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  db.update("insert into shop_session values(?,?,date_add(now(),interval 7 day))",hash(token),id);m.remove("identity_key");
  Map<String,Object> result=new HashMap<>();result.put("token",token);result.put("member",m);result.put("demo",demo);return result;
 }

 @Transactional public void bindIdentity(long uid,String identity,String phone){
  one("select id from shop_member where id=? for update",uid);
  List<Map<String,Object>> existing=db.queryForList("select member_id from shop_identity where identity_key=? for update",identity);
  check(existing.isEmpty()||number(existing.get(0).get("member_id"))==uid,"该登录方式已有独立账号，请使用原账号登录；已有资料不会自动合并");
  db.update("insert ignore into shop_identity values(?,?)",identity,uid);
  check(number(one("select member_id from shop_identity where identity_key=?",identity).get("member_id"))==uid,"该登录方式已被绑定");
  if(phone!=null)db.update("update shop_member set phone=? where id=?",phone,uid);
  event("member",uid,"绑定登录方式",str(uid));
 }
 public long procurementPrice(Map<String,Object> p,long uid,long qty){return number(grades.purchase(p,uid,qty).get("price"));}
 public Map<String,Object> procurementInfo(Map<String,Object> p,long uid,long qty){return grades.purchase(p,uid,qty);}
 public List<Map<String,Object>> catalogProducts(long uid,long certified){return grades.catalog(uid,certified);}
 public void warehouseEnsure(long pid){warehouse.ensure(pid);}
 public void reshipWarehouse(Map<String,Object> c,long wid,String who){warehouse.reship(c,wid,who);}
 public JSONObject distributionApplicationSettings(){return distribution.settings();}
 public void campaignShip(long oid){marketing.canShip(oid);}
 public boolean campaignOrder(long oid){return db.queryForObject("select count(*) from shop_campaign_order where order_id=? and kind='group'",Long.class,oid)>0;}
 public long channelStock(long pid,String channel){return warehouse.available(pid,channel);}
 public long shipWarehouse(long oid,long wid,JSONArray items,String who){check(!delivery.pickup(oid),"自提订单请使用自提码核销，不填写快递运单");return warehouse.ship(oid,wid,items,who);}
 public long shippingRefundFee(JSONObject b,Map<String,Object> o,JSONArray items,long remaining){return delivery.refundFee(b,o,items,remaining);}
 public void recordShippingRefund(Map<String,Object> c,Map<String,Object> o){delivery.refund(c,o);}
 public void returnWarehouse(Map<String,Object> c,long wid,String who){warehouse.returned(c,wid,who);}
 public void inspectWarehouse(Map<String,Object> c,JSONObject b,String who){warehouse.inspect(c,b,who);}
 public void catalogStock(List<Map<String,Object>> products){Map<String,Long> totals=new HashMap<>();for(Map<String,Object> x:db.queryForList("select x.product_id,x.channel,sum(x.available) quantity from shop_warehouse_stock x join shop_warehouse w on w.id=x.warehouse_id where w.enabled=1 group by x.product_id,x.channel"))totals.put(x.get("product_id")+"_"+x.get("channel"),number(x.get("quantity")));for(Map<String,Object> p:products){p.put("stock",totals.getOrDefault(p.get("id")+"_C",0L));p.put("procurement_stock",totals.getOrDefault(p.get("id")+"_B",0L));}}
 public long retailPrice(Map<String,Object> p,long uid){return grades.retail(p,uid);}
 public List<Map<String,Object>> publishedMedia(String channel){check(Arrays.asList("C","B").contains(channel),"渠道不正确");List<Map<String,Object>> rows=db.queryForList("select slot,section_name,page_name,position_name,published_url,title,subtitle,config_live from shop_media");rows.removeIf(m->{JSONObject c=JSON.parseObject(str(m.get("config_live")).isEmpty()?"{}":str(m.get("config_live")));String scope=str(c.getOrDefault("channel","all")),start=str(c.get("start")),end=str(c.get("end"));return (c.containsKey("enabled")&&!c.getBooleanValue("enabled"))||(!"all".equals(scope)&&!channel.equals(scope))||(!start.isEmpty()&&timestamp(start).getTime()>System.currentTimeMillis())||(!end.isEmpty()&&timestamp(end).getTime()<=System.currentTimeMillis());});return rows;}
 public List<Map<String,Object>> products(){return db.queryForList("select p.*,greatest(0,coalesce(sold.qty,0)-coalesce(ret.qty,0)) sold_quantity from shop_product p left join (select i.product_id,sum(i.quantity) qty from shop_order_item i join shop_order o on o.id=i.order_id where o.paid_at is not null and o.is_test=0"+verifiedPayment("o.id")+" group by i.product_id) sold on sold.product_id=p.id left join (select j.product_id,sum(j.quantity) qty from shop_case c join shop_order o on o.id=c.order_id join JSON_TABLE(c.items_json,'$[*]' columns(product_id BIGINT PATH '$.productId',quantity BIGINT PATH '$.quantity')) j where c.status='refunded' and o.is_test=0"+verifiedPayment("o.id")+" group by j.product_id) ret on ret.product_id=p.id where p.enabled=1 and p.archived=0 order by p.sort_order,p.id");}
 public List<Map<String,Object>> orders(long member){List<Map<String,Object>> rows=db.queryForList("select o.*,p.mode payment_mode,cr.order_id credit_order,coalesce(rb.used_amount,0) rebate_amount,coalesce(rb.returned_amount,0) refunded_rebate,coalesce(f.wallet_amount,0) wallet_amount,coalesce(f.external_amount,o.amount) external_amount,coalesce(f.refunded_wallet,0) refunded_wallet,coalesce(f.refunded_external,o.refunded_amount) refunded_external from shop_order o left join shop_rebate_order rb on rb.order_id=o.id left join shop_credit_order cr on cr.order_id=o.id left join shop_payment p on p.order_id=o.id left join shop_order_funds f on f.order_id=o.id where "+company.orderScope(member)+officialOrder("o.id")+" order by o.id desc");for(Map<String,Object> o:rows)o.put("items",db.queryForList("select * from shop_order_item where order_id=?",o.get("id")));return rows;}
 public Map<String,Object> quote(Map<String,Object> member,JSONObject body,boolean lock){
  String channel=body.getString("channel");check("C".equals(channel)||"B".equals(channel),"订单渠道不正确");check(!"B".equals(channel),"新采购已统一为分销商专属拿货，请从分销商入口申请并通过审核；历史企业订单仍可查看和处理");
  String mode=str(body.getOrDefault("purchaseMode","standard"));check(Arrays.asList("standard","partner").contains(mode),"购买方式不正确");boolean partnerOrder="partner".equals(mode);JSONObject partnerProfile=partnerOrder?partner.require(number(member.get("id"))):null;if(partnerOrder)check("C".equals(channel)&&!body.getBooleanValue("coupon")&&body.getLongValue("couponId")==0&&!body.getBooleanValue("usePoints")&&!body.getBooleanValue("exchange")&&body.getLongValue("campaignId")==0&&!body.getBooleanValue("useRebate")&&!"credit".equals(body.getString("paymentMethod")),"专属拿货独立结算，不叠加优惠券、积分、活动、返利或企业账期");
  if("B".equals(channel))company.purchase(number(member.get("id")));
  JSONArray input=body.getJSONArray("items");check(input!=null&&!input.isEmpty()&&input.size()<=100,"请选择商品");
  List<JSONObject> lines=new ArrayList<>();Set<Long> ids=new HashSet<>();long subtotal=0;
  // Consistent lock order avoids deadlocks when buyers submit the same products in a different order.
  List<JSONObject> sorted=new ArrayList<>();for(int i=0;i<input.size();i++)sorted.add(input.getJSONObject(i));sorted.sort(Comparator.comparingLong(x->x.getLongValue("id")));
  for(JSONObject item:sorted){long id=integer(item.get("id"),1,Long.MAX_VALUE,"商品编号不正确");check(ids.add(id),"请合并重复商品");int qty=(int)integer(item.get("quantity"),1,10000,"商品数量必须是1至10000的整数");
   Map<String,Object> p=one("select * from shop_product where id=?"+(lock?" for update":""),id);check(number(p.get("archived"))==0&&(Boolean.TRUE.equals(p.get("enabled"))||"1".equals(str(p.get("enabled")))),"商品已下架或删除");check(Boolean.TRUE.equals(p.get("C".equals(channel)?"retail_enabled":"wholesale_enabled"))||"1".equals(str(p.get("C".equals(channel)?"retail_enabled":"wholesale_enabled"))),"商品未开放该渠道销售");p.put("stock",warehouse.available(id,channel)+marketing.held(body.getLongValue("campaignId"),id));check(number(p.get("stock"))>=qty,str(p.get("name"))+"当前渠道库存不足");
   if("B".equals(channel))grades.validate(p,number(company.context(number(member.get("id"))).get("certified_member_id")),qty);
   long owner="B".equals(channel)?number(company.context(number(member.get("id"))).get("id")):number(member.get("id"));check(grades.tier(channel,owner)>=number(p.get("minimum_tier")),"该商品需要更高会员或采购等级");Map<String,Object> priceInfo="B".equals(channel)?grades.purchase(p,number(company.context(number(member.get("id"))).get("certified_member_id")),qty):Collections.emptyMap();long price="B".equals(channel)?number(priceInfo.get("price")):grades.retail(p,number(member.get("id")));if(partnerOrder)price=partner.price(p,partnerProfile);JSONObject line=new JSONObject();line.put("standardPrice",p.get("price"));line.put("agreementPrice",Boolean.TRUE.equals(priceInfo.get("agreement")));for(String key:new String[]{"id","name","spec","image","stock","pack"})line.put(key,p.get(key));line.put("quantity",qty);line.put("unitPrice",price);lines.add(line);subtotal+=price*qty;
  }
  JSONObject r=rules();if("B".equals(channel))r.put("purchasePricePriority",grades.settings().get("purchasePriority"));if(partnerOrder){r.put("partner",partnerProfile);r.put("purchaseMode","partner");r.put("businessType","partner");}else subtotal=marketing.apply(number(member.get("id")),body,lines,subtotal,lock,r);long discount="C".equals(channel)&&body.getBooleanValue("coupon")&&subtotal>=r.getLongValue("couponThreshold")?Math.min(subtotal,r.getLongValue("couponDiscount")):0;


 discount=partnerOrder?0:body.getLongValue("campaignId")>0?r.getLongValue("campaignDiscount"):marketing.discount(number(member.get("id")),body,lines,subtotal,discount,r);long promotion=discount;Map<String,Object> coupon=null;long cumulative=0;
 Set<Long> promotionIds=new HashSet<>();if(r.getJSONArray("promotionProducts")!=null)for(Object pid:r.getJSONArray("promotionProducts"))promotionIds.add(number(pid));long eligibleSubtotal=0;for(JSONObject l:lines)if(promotionIds.isEmpty()||promotionIds.contains(l.getLongValue("id")))eligibleSubtotal+=l.getLongValue("unitPrice")*l.getLongValue("quantity");
 for(JSONObject line:lines){long gross=line.getLongValue("unitPrice")*line.getLongValue("quantity"),before=cumulative;if(promotionIds.isEmpty()||promotionIds.contains(line.getLongValue("id")))cumulative+=gross;line.put("netAmount",gross-(ShopCommerce.portion(cumulative,promotion,eligibleSubtotal)-ShopCommerce.portion(before,promotion,eligibleSubtotal)));}
 if(body.getLongValue("couponId")>0){coupon=commerce.quotedCoupon(number(member.get("id")),body.getLongValue("couponId"),channel,lines,subtotal,promotion,lock);discount+=number(coupon.get("applied_discount"));}
 Map<String,Object> result=new HashMap<>();result.put("businessType",partnerOrder?"partner":"B".equals(channel)?"enterprise":"retail");result.put("items",lines);result.put("subtotal",subtotal);result.put("promotionDiscount",promotion);result.put("couponDiscount",discount-promotion);result.put("selectedCoupon",coupon);result.put("discount",discount);result.put("amount",subtotal-discount);result.put("rules",r);points.quote(number(member.get("id")),body,lines,result);if("B".equals(channel))check(number(result.get("amount"))>=r.getLongValue("minimumWholesale"),"优惠后商品金额未达到采购起订金额 ¥"+String.format(java.util.Locale.ROOT,"%.2f",r.getLongValue("minimumWholesale")/100.0)+"，请增加商品数量（运费、余额付款和返利抵扣不计入起订金额）");r.put("loyalty",result.get("pointRules"));if(!partnerOrder)marketing.gift(number(member.get("id")),body,result,lock);delivery.quote(body,result);check(!body.getBooleanValue("useBalance")||r.getBooleanValue("walletPaymentEnabled"),"商家暂未开放余额付款，请关闭使用余额后重试");long walletUsed=body.getBooleanValue("useBalance")?Math.min(number(result.get("amount")),wallet.available(number(member.get("id")))):0;result.put("walletUsed",walletUsed);result.put("externalAmount",number(result.get("amount"))-walletUsed);credit.quote(number(member.get("id")),body,result);rebate.quote(number(member.get("id")),body,result);if(!wechatPay.ready()&&!r.getBooleanValue("simulationPaymentEnabled")&&number(result.get("externalAmount"))>0&&!"credit".equals(body.getString("paymentMethod")))check("B".equals(channel)&&bank.settings().getBooleanValue("enabled")&&number(result.get("walletUsed"))==0&&number(result.get("rebateUsed"))==0,"商家暂未开放在线付款，请选择可用的余额或账期方式，或联系客服");return result;
 }
 @Transactional public Map<String,Object> createOrder(Map<String,Object> m,JSONObject b){
  long uid=number(m.get("id"));String request=b.getString("requestKey");check(request!=null&&request.matches("[a-zA-Z0-9_-]{8,80}"),"请重新提交订单");
  one("select id from shop_member where id=? for update",uid);
  List<Map<String,Object>> exists=db.queryForList("select * from shop_order where member_id=? and request_key=?",uid,request);if(!exists.isEmpty())return exists.get(0);
  String checkoutSource=str(b.getOrDefault("checkoutSource","direct"));check(Arrays.asList("direct","cart").contains(checkoutSource),"结算来源不正确");
  boolean fromCart="cart".equals(checkoutSource);if(fromCart)check(!b.getBooleanValue("exchange")&&b.getLongValue("campaignId")==0,"该购买方式请使用独立结算");
  long cartRevision=fromCart?integer(b.get("cartRevision"),0,Long.MAX_VALUE-1,"请返回购物车重新确认商品"):0;
  Map<String,Object> q=quote(m,b,true);check(integer(b.get("expectedAmount"),0,Long.MAX_VALUE,"订单金额必须为整数分")==number(q.get("amount")),"价格已更新，请重新确认金额");
  check(!b.getBooleanValue("useBalance")||b.getLongValue("expectedWallet")==number(q.get("walletUsed")),"余额已变化，请重新确认支付明细");check(!(b.getBooleanValue("usePoints")||b.getBooleanValue("exchange"))||b.getLongValue("expectedPoints")==number(q.get("pointsUsed")),"积分已变化，请重新确认");JSONObject address=b.getJSONObject("address");check(address!=null&&!str(address.get("name")).trim().isEmpty()&&str(address.get("phone")).matches("1[3-9][0-9]{9}")&&str(address.get("address")).length()>=5,"请填写完整收货信息");
  Map<String,Object> draft=company.prepare(uid,b,q);if(draft!=null)return draft;
  String no="BC"+System.currentTimeMillis()+UUID.randomUUID().toString().substring(0,6);
  db.update("insert into shop_order(order_no,member_id,channel,status,subtotal,discount,amount,address_json,rules_json,request_key) values(?,?,?,'pending',?,?,?,?,?,?)",no,uid,b.getString("channel"),q.get("subtotal"),q.get("discount"),q.get("amount"),address.toJSONString(),JSON.toJSONString(q.get("rules")),request);
  db.update("update shop_order set expires_at=date_add(now(),interval ? minute) where order_no=?",((JSONObject)q.get("pointRules")).getLongValue("orderMinutes"),no);db.update("update shop_order set is_test=? where order_no=?",testData||Boolean.TRUE.equals(m.get("is_test"))||"1".equals(str(m.get("is_test"))),no);Map<String,Object> order=one("select * from shop_order where order_no=?",no);long oid=number(order.get("id"));db.update("update shop_order set business_type=? where id=?",q.get("businessType"),oid);order.put("business_type",q.get("businessType"));if(q.get("selectedCoupon")!=null)commerce.reserve(uid,oid,(Map<String,Object>)q.get("selectedCoupon"));
  delivery.reserve(oid,q);marketing.reserve(uid,oid,q);warehouse.plan(oid,b.getString("channel"),(List<?>)q.get("items"));for(Object obj:(List<?>)q.get("items")){JSONObject p=(JSONObject)obj;int qty=p.getIntValue("quantity");inventory(number(p.get("id")),-qty,"reserve_"+oid,"下单占用库存",str(uid));db.update("insert into shop_order_item(order_id,product_id,name,spec,image,quantity,price) values(?,?,?,?,?,?,?)",oid,p.get("id"),p.get("name"),p.get("spec"),p.get("image"),qty,p.get("unitPrice"));db.update("update shop_order_item set net_amount=?,points_used=?,standard_price=? where order_id=? and product_id=?",p.get("netAmount"),p.getLongValue("pointsUsed"),p.get("standardPrice"),oid,p.get("id"));}
  company.attached(uid,oid,b);rebate.reserve(uid,oid,b,q);distribution.snapshot(oid);credit.reserve(uid,oid,b,q);points.reserve(uid,oid,q);wallet.freeze(uid,oid,number(q.get("walletUsed")),number(q.get("amount"))-number(q.get("rebateUsed")));if(fromCart)order.put("cart",commerce.removeOrdered(uid,b.getString("channel"),b.getJSONArray("items"),cartRevision));event("order",oid,"创建订单并占用库存",str(uid));return order;
 }
 @Transactional public void orderAction(long uid,long id,String action){
  long actor=uid;Map<String,Object> initial=one("select * from shop_order where id=?",id);company.authorize(actor,initial,action);long buyerId=number(initial.get("member_id"));for(long lockId:new TreeSet<Long>(Arrays.asList(actor,buyerId)))one("select id from shop_member where id=? for update",lockId);Map<String,Object> o=one("select * from shop_order where id=? for update",id);company.authorize(actor,o,action);uid=buyerId;String state=str(o.get("status"));
  if("pay".equals(action)){if(Arrays.asList("paid","shipping","shipped","completed","refunded").contains(state)&&o.get("paid_at")!=null)return;check("pending".equals(state),"当前订单不能支付");check(!expired(o),"订单已超时，请返回订单列表刷新");check(!bank.started(id),"订单已选择对公转账，请等待财务核验");check(!wechatPay.started(id),"请使用微信支付入口确认付款状态");boolean walletOnly=wallet.canPay(o),rebateOnly=rebate.canPay(o);check(isDemo()||credit.exists(id)||exchangeOrder(id)||walletOnly||rebateOnly,"在线支付尚未开通，请联系客服");JSONObject saved=JSON.parseObject(str(o.get("rules_json")));check(walletOnly||rebateOnly||credit.exists(id)||exchangeOrder(id)||!saved.containsKey("simulationPaymentEnabled")||saved.getBooleanValue("simulationPaymentEnabled")||number(one("select external_amount from shop_order_funds where order_id=?",id).get("external_amount"))==0,"该订单未开放在线付款，请使用对公核验或联系客服");recordPayment(o,uid,credit.exists(id)?"credit":exchangeOrder(id)?"points_exchange":walletOnly?"wallet":rebateOnly?"rebate":"simulation");
  }else if("cancel".equals(action)){check("pending".equals(state),"只能取消待付款订单");if(!wechatPay.closeBeforeRelease(o))return;rebate.release(id);credit.release(id);bank.close(id);commerce.release(id);wallet.release(id);points.release(id);restoreStock(id);marketing.release(id);db.update("update shop_order set status='cancelled' where id=?",id);
  }else if("receive".equals(action)){check("shipped".equals(state),"订单尚未发货");check(db.queryForObject("select count(*) from shop_case where order_id=? and status not in ('refunded','completed','rejected')",Long.class,id)==0,"请先完成进行中的售后");db.update("update shop_shipment set received=1,received_at=coalesce(received_at,now()) where order_id=?",id);db.update("update shop_order set status='completed' where id=?",id);orderReceived(id);
  }else throw new IllegalArgumentException("不支持的操作");event("order",id,action,str(actor));
 }
 void recordWechatPayment(Map<String,Object> o){recordPayment(o,number(o.get("member_id")),"wechat");}
 private void recordPayment(Map<String,Object> o,long uid,String mode){if(!"wechat".equals(mode))partner.validatePayment(o);long id=number(o.get("id"));db.update("insert into shop_payment(order_id,member_id,amount,mode) values(?,?,?,?)",id,uid,o.get("amount"),mode);db.update("update shop_order set status='paid',paid_at=now(),is_test=if(?,1,is_test) where id=?","simulation".equals(mode),id);finance("payment","pay_"+id,id,number(o.get("amount")),0);commerce.consume(id);wallet.paid(uid,id);points.paid(id);credit.paid(id);rebate.paid(id);marketing.paid(id,"wechat".equals(mode));
   if(distribution.paid(id))return;Map<String,Object> buyer=one("select * from shop_member where id=?",uid);if(buyer.get("referrer_id")!=null&&number(buyer.get("referrer_id"))!=uid&&db.queryForObject("select count(*) from shop_member where id=? and enabled=1 and distributor_state='approved'",Long.class,buyer.get("referrer_id"))>0){JSONObject r=JSON.parseObject(str(o.get("rules_json")));long amount=number(o.get("amount"))*r.getLongValue("commissionPercent")/100;db.update("insert ignore into shop_commission(member_id,order_id,amount,status) values(?,?,?,'pending')",buyer.get("referrer_id"),id,amount);db.update("insert ignore into shop_commission_event(member_id,order_id,delta,event_key,note) values(?,?,?,?,?)",buyer.get("referrer_id"),id,amount,"payment_"+id,"订单付款产生佣金");}
 }
 @Transactional public void bankPayment(long id){Map<String,Object> first=one("select member_id from shop_order where id=?",id);long uid=number(first.get("member_id"));one("select id from shop_member where id=? for update",uid);Map<String,Object> o=one("select * from shop_order where id=? for update",id);check("pending".equals(o.get("status"))&&!expired(o),"当前订单不能核销收款");check(number(one("select confirmed from shop_bank_order where order_id=?",id).get("confirmed"))>=number(one("select external_amount from shop_order_funds where order_id=?",id).get("external_amount")),"到账金额不足");recordPayment(o,uid,"bank_transfer");}
 public void inheritCompanyReferral(long company,long owner){distribution.inherit(company,owner);}
 public void companyCertified(long uid){company.context(uid);}
 public boolean exchangeOrder(long oid){Map<String,Object> o=one("select amount,rules_json from shop_order where id=?",oid);return number(o.get("amount"))==0&&JSON.parseObject(str(o.get("rules_json"))).getBooleanValue("exchange");}
 public boolean creditOrder(long oid){return credit.exists(oid);}
 public long creditRefundExcess(Map<String,Object> o,Map<String,Object> c){return credit.excess(o,c);}
 public boolean bankStarted(long oid){return bank.started(oid);}
 public boolean queueBankRefund(Map<String,Object> c,Map<String,Object> o){return wechatPay.queueRefund(c,o)||bank.queueCase(c,o);}
 public void restoreStock(long id){for(Map<String,Object> i:db.queryForList("select * from shop_order_item where order_id=? order by product_id",id))inventory(number(i.get("product_id")),number(i.get("quantity")),"release_"+id,"订单取消或未发货退款释放库存","系统");}
 public static String maskAccount(String value){String digits=str(value).replaceAll("[^0-9]","");return "收款信息（尾号"+(digits.length()>4?digits.substring(digits.length()-4):digits)+"）";}
 /** Stable audit label for all account types; never persist WeChat OpenID in finance records. */
 public String accountAuditIdentity(long uid){
  List<Map<String,Object>> credentials=db.queryForList("select username from shop_credentials where member_id=?",uid);
  return credentials.isEmpty()?"member:"+uid:str(credentials.get(0).get("username"));
 }
 /** A later password binding must not allow the same account to review its previous submission. */
 public boolean accountMatchesReviewer(long uid,String reviewer){
  return db.queryForObject("select count(*) from shop_credentials where member_id=? and username=?",Long.class,uid,reviewer)>0;
 }
 public long available(long id){long earned=0,spent=0;for(Map<String,Object> c:db.queryForList("select amount from shop_commission where member_id=? and status='available'"+verifiedPayment("shop_commission.order_id")+" order by id for update",id))earned+=number(c.get("amount"));long current=distribution.available(id);for(Map<String,Object> w:db.queryForList("select amount from shop_withdrawal where member_id=? and status in ('pending','unknown','paid')"+(officialDisplay?" and payment_mode<>'simulation'":"")+" order by id for update",id))spent+=number(w.get("amount"));return earned+current-spent;}
 @Transactional public void withdraw(long uid,JSONObject b){check(isDemo(),"提现付款渠道尚未开通");Map<String,Object> m=one("select * from shop_member where id=? for update",uid);check("approved".equals(m.get("distributor_state"))||db.queryForObject("select count(*) from shop_reward where member_id=?",Long.class,uid)>0||db.queryForObject("select count(*) from shop_commission where member_id=?",Long.class,uid)>0,"请先通过分销审核");long amount=integer(b.get("amount"),1,100000000,"提现金额必须为整数分");JSONObject r=rules();check(amount>=r.getLongValue("withdrawMinimum")&&amount>r.getLongValue("withdrawFee"),"未达到提现门槛");String account=b.getString("account");check(account!=null&&account.trim().length()>=6&&account.length()<=300,"请填写收款信息");String key=str(b.get("requestKey"));check(!key.isEmpty(),"请重新提交提现申请");if(!key.isEmpty()){check(key.matches("[a-zA-Z0-9_-]{8,80}"),"提现请求标识不正确");List<Map<String,Object>> prior=db.queryForList("select * from shop_withdraw_request where member_id=? and request_key=?",uid,key);if(!prior.isEmpty()){check(number(prior.get(0).get("amount"))==amount&&hash(account).equals(prior.get(0).get("account_hash")),"请刷新页面后重新填写提现申请");return;}db.update("insert into shop_withdraw_request values(?,?,?,?)",uid,key,amount,hash(account));}check(!distribution.frozen(uid),"佣金暂时冻结，请查看原因或联系客服");check(amount<=available(uid),"可提现余额不足");String submitter=accountAuditIdentity(uid);db.update("insert into shop_withdrawal(member_id,amount,fee,account_info,status,due_at,submitted_account) values(?,?,?,?,'pending',?,?)",uid,amount,r.getLongValue("withdrawFee"),maskAccount(account),workingDeadline(new java.sql.Timestamp(System.currentTimeMillis()),r.getLongValue("withdrawWorkingDays")),submitter);}
 public List<Map<String,Object>> maskWithdrawals(List<Map<String,Object>> rows){for(Map<String,Object> row:rows)row.put("account_info",maskAccount(str(row.get("account_info"))));return rows;}
 public void finance(String kind,String key,long id,long amount,long fee){db.update("insert into shop_finance_event(event_key,kind,object_id,amount,fee,occurred_at) values(?,?,?,?,?,now())",key,kind,id,amount,fee);}
}
