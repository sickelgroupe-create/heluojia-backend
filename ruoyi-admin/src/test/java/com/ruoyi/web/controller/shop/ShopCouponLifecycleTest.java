package com.ruoyi.web.controller.shop;

import com.alibaba.fastjson2.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Local MySQL only. Normal fixtures roll back; concurrency fixtures are removed by ID. */
@EnabledIfSystemProperty(named="wechat.test.properties",matches=".+")
class ShopCouponLifecycleTest {
 ShopService s;ShopCommerce commerce;TransactionTemplate tx;long uid,cid;
 @BeforeEach void setup()throws Exception{
  Properties config=new Properties();try(Reader in=new InputStreamReader(new FileInputStream(System.getProperty("wechat.test.properties")),StandardCharsets.UTF_8)){config.load(in);}
  String url=config.getProperty("spring.datasource.druid.master.url");assertTrue(url.startsWith("jdbc:mysql://localhost:")||url.startsWith("jdbc:mysql://127.0.0.1:"),"Only local database is allowed");
  DriverManagerDataSource ds=new DriverManagerDataSource(url,config.getProperty("spring.datasource.druid.master.username"),config.getProperty("spring.datasource.druid.master.password"));
  s=new ShopService();s.db=new JdbcTemplate(ds);commerce=new ShopCommerce();ReflectionTestUtils.setField(commerce,"s",s);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  ReflectionTestUtils.invokeMethod(s,"column","shop_coupon_campaign","archived","TINYINT NOT NULL DEFAULT 0");
  ReflectionTestUtils.invokeMethod(s,"column","shop_coupon_campaign","terminated_at","DATETIME NULL");
  ReflectionTestUtils.invokeMethod(s,"column","shop_coupon","terminated_at","DATETIME NULL");
 }
 void fixture(){
  String key="coupon-lifecycle-"+UUID.randomUUID();
  s.db.update("insert into shop_member(identity_key,nickname,account_kind,is_test) values(?,?,'demo',1)",key,key);uid=s.db.queryForObject("select last_insert_id()",Long.class);
  s.db.update("insert into shop_coupon_campaign(name,threshold_amount,discount_amount,stock,claim_limit,valid_days,starts_at,ends_at,product_ids,enabled) values(?,100,10,10,10,30,date_sub(now(),interval 1 day),date_add(now(),interval 1 day),'[]',0)",key);cid=s.db.queryForObject("select last_insert_id()",Long.class);
 }
 long coupon(String state,boolean expired,boolean terminated){
  s.db.update("insert into shop_coupon(campaign_id,member_id,name,threshold_amount,discount_amount,product_ids,state,expires_at,claim_key,terminated_at) values(?,?,'生命周期验证',100,10,'[]',?,date_add(now(),interval ? day),?,?)",cid,uid,state,expired?-1:1,UUID.randomUUID().toString(),terminated?new java.sql.Timestamp(System.currentTimeMillis()):null);
  s.db.update("update shop_coupon_campaign set claimed=claimed+1 where id=?",cid);return s.db.queryForObject("select last_insert_id()",Long.class);
 }
 Map<String,Object> row(){return commerce.campaigns(true).stream().filter(r->((Number)r.get("id")).longValue()==cid).findFirst().get();}
 void rollback(Runnable test){tx.execute(status->{try{fixture();test.run();return null;}finally{status.setRollbackOnly();}});assertEquals(0L,s.db.queryForObject("select count(*) from shop_member where id=?",Long.class,uid));}
 void blocked(String reason){assertEquals(false,row().get("delete_allowed"));assertTrue(str(row().get("delete_reason")).contains(reason));assertTrue(assertThrows(IllegalArgumentException.class,()->commerce.deleteCampaign(cid,"fixture")).getMessage().contains(reason));assertEquals(0L,s.db.queryForObject("select archived from shop_coupon_campaign where id=?",Long.class,cid));}
 String str(Object v){return String.valueOf(v);}
 @Test void enabledCampaignsCannotBeDeletedEvenIfFutureEndedSoldOutOrAllCouponsExpired(){rollback(()->{
  coupon("available",true,false);s.db.update("update shop_coupon_campaign set enabled=1 where id=?",cid);blocked("暂停");
  s.db.update("update shop_coupon_campaign set starts_at=date_add(now(),interval 1 day) where id=?",cid);blocked("暂停");
  s.db.update("update shop_coupon_campaign set ends_at=date_sub(now(),interval 1 day),claimed=stock where id=?",cid);blocked("暂停");
 });}
 @Test void pausedLiveAndUsedCouponsBlockDeletion(){rollback(()->{
  long id=coupon("available",false,false);blocked("未过期");
  s.db.update("update shop_coupon set state='used',return_on_refund=0 where id=?",id);blocked("未过期");
  coupon("available",true,false);coupon("available",false,true);blocked("未过期");
 });}
 @Test void reservedCouponsBlockBothActionsEvenAfterExpiry(){rollback(()->{
  coupon("reserved",true,false);blocked("占用");assertEquals(false,row().get("terminate_allowed"));assertThrows(IllegalArgumentException.class,()->commerce.terminateCampaign(cid,"fixture"));
 });}
 @Test void allExpiredCanBeDeletedWithoutErasingHistoryOrChangingCoupons(){rollback(()->{
  long id=coupon("used",true,false);coupon("available",true,false);assertEquals(true,row().get("delete_allowed"));commerce.deleteCampaign(cid,"fixture");
  assertEquals(2L,s.db.queryForObject("select count(*) from shop_coupon where campaign_id=?",Long.class,cid));assertNull(s.one("select terminated_at from shop_coupon where id=?",id).get("terminated_at"));
  assertFalse(commerce.campaigns(true).stream().anyMatch(r->((Number)r.get("id")).longValue()==cid));assertFalse(commerce.campaigns(false).stream().anyMatch(r->((Number)r.get("id")).longValue()==cid));
  assertThrows(IllegalArgumentException.class,()->commerce.saveCampaign(JSONObject.of("id",cid,"enabled",true),"fixture"));
  assertThrows(IllegalArgumentException.class,()->commerce.claim(uid,cid,JSONObject.of("requestKey","new_claim_after_delete")));
 });}
 @Test void pausedCampaignWithoutClaimsCanBeDeleted(){rollback(()->{assertEquals(true,row().get("delete_allowed"));commerce.deleteCampaign(cid,"fixture");});}
 @Test void explicitTerminationMakesAllCouponsUnusableAndThenAllowsDeletion(){rollback(()->{
  long id=coupon("available",false,false);coupon("used",false,false);Map<String,Object> stale=s.one("select * from shop_coupon where id=?",id);stale.put("applied_discount",10);
  blocked("未过期");commerce.terminateCampaign(cid,"fixture");assertEquals(true,row().get("delete_allowed"));assertEquals(2L,((Number)row().get("terminated_count")).longValue());
  assertTrue(commerce.coupons(uid).stream().allMatch(c->"terminated".equals(c.get("display_state"))));
  assertThrows(IllegalArgumentException.class,()->commerce.quotedCoupon(uid,id,"C",new ArrayList<>(),100,0,false));
  assertThrows(IllegalArgumentException.class,()->commerce.reserve(uid,999999999L,stale));
  assertThrows(IllegalArgumentException.class,()->commerce.saveCampaign(JSONObject.of("id",cid,"enabled",true),"fixture"));
  commerce.deleteCampaign(cid,"fixture");assertEquals(2L,s.db.queryForObject("select count(*) from shop_coupon where campaign_id=?",Long.class,cid));
 });}
 @Test void terminationRequiresExplicitPauseAndUnknownCouponStateFailsClosed(){rollback(()->{
  coupon("unknown",true,false);blocked("未过期");s.db.update("update shop_coupon_campaign set enabled=1 where id=?",cid);assertThrows(IllegalArgumentException.class,()->commerce.terminateCampaign(cid,"fixture"));
 });}
 @Test void refundNeverRestoresTerminatedCouponAndPreservesOrderSnapshot(){rollback(()->{
  long id=coupon("used",false,false);String key=UUID.randomUUID().toString();s.db.update("insert into shop_order(order_no,member_id,channel,status,subtotal,discount,amount,address_json,rules_json,request_key,expires_at,is_test) values(?,?,'C','refunded',100,10,90,'{}','{}',?,now(),1)","CT"+key.substring(0,28),uid,key);long oid=s.db.queryForObject("select last_insert_id()",Long.class);
  s.db.update("update shop_coupon set order_id=?,return_on_refund=1 where id=?",oid,id);s.db.update("insert into shop_order_coupon(order_id,coupon_id,discount_amount,snapshot_json) values(?,?,10,'{\"name\":\"历史券\"}')",oid,id);
  commerce.terminateCampaign(cid,"fixture");commerce.deleteCampaign(cid,"fixture");commerce.refund(oid);commerce.refund(oid);
  assertEquals("used",s.one("select state from shop_coupon where id=?",id).get("state"));assertEquals("terminated",commerce.coupons(uid).get(0).get("display_state"));assertEquals("{\"name\":\"历史券\"}",s.one("select snapshot_json from shop_order_coupon where order_id=?",oid).get("snapshot_json"));
 });}
 @Test void pauseStillAllowsUsingLiveCouponAndCancelAndRefundReturnUnteminatedCoupons(){rollback(()->{
  long id=coupon("available",false,false);List<JSONObject> lines=Arrays.asList(JSONObject.of("id",1001,"quantity",1,"unitPrice",100,"netAmount",100));
  Map<String,Object> c=commerce.quotedCoupon(uid,id,"C",lines,100,0,true);assertEquals(10L,c.get("applied_discount"));commerce.reserve(uid,999999997L,c);blocked("占用");commerce.release(999999997L);assertEquals("available",commerce.coupons(uid).get(0).get("display_state"));
 });}
 @Test void deletionWaitsForConcurrentReservationThenRejectsIt()throws Exception{
  tx.execute(status->{fixture();coupon("available",false,false);return null;});long id=s.db.queryForObject("select id from shop_coupon where campaign_id=?",Long.class,cid);
  CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
  try{
   Future<?> reservation=pool.submit(()->tx.execute(status->{s.db.update("update shop_coupon set state='reserved',order_id=999999996 where id=?",id);locked.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return null;}));assertTrue(locked.await(5,TimeUnit.SECONDS));
   Future<String> deletion=pool.submit(()->tx.execute(status->{try{commerce.deleteCampaign(cid,"fixture");return "deleted";}catch(IllegalArgumentException e){return e.getMessage();}}));
   release.countDown();reservation.get(5,TimeUnit.SECONDS);assertTrue(deletion.get(5,TimeUnit.SECONDS).contains("占用"));assertEquals(0L,s.db.queryForObject("select archived from shop_coupon_campaign where id=?",Long.class,cid));
  }finally{release.countDown();pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);tx.execute(status->{s.db.update("delete from shop_coupon where campaign_id=?",cid);s.db.update("delete from shop_coupon_campaign where id=?",cid);s.db.update("delete from shop_member where id=?",uid);return null;});}
 }
}
