package com.ruoyi.web.controller.shop;

import com.alibaba.fastjson2.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.web.controller.shop.ShopService.*;

/** Opt-in local MySQL integration. Every fixture and ledger write is rolled back. */
@EnabledIfSystemProperty(named="wechat.test.properties",matches=".+")
class ShopWechatPayDatabaseTest {
 ShopService s;ShopWechatPay pay;ShopWechatPayGateway gateway;TransactionTemplate tx;long uid,oid;
 @AfterEach void fixtureWasRolledBack(){if(s!=null&&uid>0)assertEquals(0L,s.db.queryForObject("select count(*) from shop_member where id=?",Long.class,uid));}
 @BeforeEach void setup()throws Exception{
  Properties config=new Properties();try(Reader in=new InputStreamReader(new FileInputStream(System.getProperty("wechat.test.properties")),StandardCharsets.UTF_8)){config.load(in);}
  String url=config.getProperty("spring.datasource.druid.master.url");assertTrue(url.startsWith("jdbc:mysql://localhost:")||url.startsWith("jdbc:mysql://127.0.0.1:"),"Only local database is allowed");
  DriverManagerDataSource ds=new DriverManagerDataSource(url,config.getProperty("spring.datasource.druid.master.username"),config.getProperty("spring.datasource.druid.master.password"));
  new ResourceDatabasePopulator(new ClassPathResource("shop-wechat-pay.sql")).execute(ds);
  s=new ShopService();s.db=new JdbcTemplate(ds);s.partner=mock(ShopPartner.class);s.company=mock(ShopCompany.class);s.commerce=mock(ShopCommerce.class);s.points=mock(ShopPoints.class);s.credit=mock(ShopCredit.class);s.rebate=mock(ShopRebate.class);s.marketing=mock(ShopMarketing.class);s.distribution=mock(ShopDistribution.class);s.bank=mock(ShopBank.class);s.invoice=mock(ShopInvoice.class);s.reviews=mock(ShopReviews.class);s.delivery=mock(ShopDelivery.class);
  s.wallet=new ShopWallet();ReflectionTestUtils.setField(s.wallet,"s",s);
  gateway=spy(new ShopWechatPayGateway());gateway.appId="wx0123456789abcdef";gateway.merchantId="1234567890";doReturn(true).when(gateway).ready();doReturn(JSONObject.parseObject("{\"package\":\"prepay_id=fixture\"}")).when(gateway).paymentParameters(anyString());
  tx=new TransactionTemplate(new DataSourceTransactionManager(ds));pay=new ShopWechatPay();pay.gateway=gateway;pay.tx=tx;s.wechatPay=pay;pay.workflow=new ShopWorkflow();
  gateway.notifyUrl="https://example.com/shop/wechat-pay/notify";
  org.springframework.aop.framework.ProxyFactory factory=new org.springframework.aop.framework.ProxyFactory(s);factory.setProxyTargetClass(true);ShopService proxy=(ShopService)factory.getProxy();pay.s=proxy;pay.workflow.s=proxy;
 }
 void fixture(){
  String stamp=UUID.randomUUID().toString().replace("-","");
  s.db.update("insert into shop_member(identity_key,nickname,account_kind,is_test) values(?,?,?,0)","pay-fixture-"+stamp,"微信支付回滚测试","wechat");uid=s.db.queryForObject("select last_insert_id()",Long.class);
  s.db.update("insert into shop_identity(identity_key,member_id) values(?,?)","wechat:"+gateway.appId+":fixture_openid_123",uid);
  s.db.update("insert into shop_order(order_no,member_id,channel,status,subtotal,discount,amount,address_json,rules_json,request_key,expires_at,is_test) values(?,?,'C','pending',1000,0,1000,'{}','{\"commissionPercent\":0}',?,date_add(now(),interval 30 minute),0)","WX"+stamp.substring(0,28),uid,stamp);
  oid=s.db.queryForObject("select last_insert_id()",Long.class);s.wallet.freeze(uid,oid,0,1000);
 }
 JSONObject transaction(){Map<String,Object> p=s.one("select * from shop_wechat_payment where order_id=?",oid);JSONObject t=new JSONObject();t.put("appid",gateway.appId);t.put("mchid",gateway.merchantId);t.put("out_trade_no",p.get("out_trade_no"));t.put("trade_state","SUCCESS");t.put("trade_type","JSAPI");t.put("transaction_id","420000000000000000000000"+oid);t.put("amount",JSONObject.parseObject("{\"total\":1000,\"currency\":\"CNY\"}"));t.put("payer",JSONObject.parseObject("{\"openid\":\"fixture_openid_123\"}"));return t;}
 void prepare(){doReturn(JSONObject.parseObject("{\"prepay_id\":\"wx_fixture\"}")).when(gateway).call(eq("POST"),eq("/v3/pay/transactions/jsapi"),any(JSONObject.class));pay.prepay(uid,oid);}
 @Test void realDatabaseBooksOnceAndRefundsOnlyAfterSignedSuccess(){tx.execute(status->{try{
  fixture();prepare();JSONObject t=transaction();pay.notification(t);pay.notification(t);
  assertEquals("paid",s.one("select status from shop_order where id=?",oid).get("status"));
  assertEquals(1L,s.db.queryForObject("select count(*) from shop_payment where order_id=? and mode='wechat'",Long.class,oid));
  assertEquals(1L,s.db.queryForObject("select count(*) from shop_finance_event where event_key=?",Long.class,"pay_"+oid));
  assertEquals("paid",s.one("select state from shop_order_funds where order_id=?",oid).get("state"));
  s.db.update("insert into shop_case(order_id,member_id,type,status,items_json,amount,reason) values(?,?,'refund','awaiting_refund','[]',1000,'支付回滚测试')",oid,uid);long cid=s.db.queryForObject("select last_insert_id()",Long.class);
  assertTrue(pay.queueRefund(s.one("select * from shop_case where id=?",cid),s.one("select * from shop_order where id=?",oid)));
  String no="WXRF"+oid+"C"+cid;JSONObject result=new JSONObject();result.put("out_refund_no",no);result.put("transaction_id",t.get("transaction_id"));result.put("refund_id","500000000000000000000000"+cid);result.put("status","PROCESSING");result.put("amount",JSONObject.parseObject("{\"refund\":1000,\"total\":1000,\"currency\":\"CNY\"}"));
  doReturn(result).when(gateway).call(eq("POST"),eq("/v3/refund/domestic/refunds"),any(JSONObject.class));pay.processRefund(cid);
  assertEquals(0L,number(s.one("select refunded_amount from shop_order where id=?",oid).get("refunded_amount")));
  result.put("status","SUCCESS");doReturn(result).when(gateway).call("GET","/v3/refund/domestic/refunds/"+no,null);pay.processRefund(cid);pay.processRefund(cid);
  assertEquals("refunded",s.one("select status from shop_case where id=?",cid).get("status"));
  assertEquals(1000L,number(s.one("select refunded_external from shop_order_funds where order_id=?",oid).get("refunded_external")));
  assertEquals(1L,s.db.queryForObject("select count(*) from shop_finance_event where event_key=?",Long.class,"case_"+cid));return null;
 }finally{status.setRollbackOnly();}});}
 @Test void cancellationClosesRemoteBeforeReleasingFundsAndInventory(){tx.execute(status->{try{
  fixture();prepare();JSONObject pending=transaction();pending.put("trade_state","NOTPAY");doReturn(pending).when(gateway).query(anyString());doReturn(new JSONObject()).when(gateway).call(eq("POST"),endsWith("/close"),any(JSONObject.class));
  s.orderAction(uid,oid,"cancel");assertEquals("cancelled",s.one("select status from shop_order where id=?",oid).get("status"));assertEquals("CLOSED",s.one("select state from shop_wechat_payment where order_id=?",oid).get("state"));assertEquals("cancelled",s.one("select state from shop_order_funds where order_id=?",oid).get("state"));return null;
 }finally{status.setRollbackOnly();}});}
 @Test void refundCallbackUpdatesOrderAndHistoryExactlyOnceAndRejectsWrongMoney(){tx.execute(status->{try{
  fixture();prepare();JSONObject payment=transaction();pay.notification(payment);
  s.db.update("insert into shop_case(order_id,member_id,type,status,items_json,amount,reason) values(?,?,'refund','awaiting_refund','[]',1000,'退款通知回滚测试')",oid,uid);long cid=s.db.queryForObject("select last_insert_id()",Long.class);
  pay.queueRefund(s.one("select * from shop_case where id=?",cid),s.one("select * from shop_order where id=?",oid));
  JSONObject r=new JSONObject();r.put("mchid",gateway.merchantId);r.put("out_trade_no",payment.get("out_trade_no"));r.put("transaction_id",payment.get("transaction_id"));r.put("out_refund_no","WXRF"+oid+"C"+cid);r.put("refund_id","500000000000000000000000"+cid);r.put("refund_status","SUCCESS");r.put("success_time","2026-09-20T15:26:19+08:00");r.put("amount",JSONObject.parseObject("{\"total\":1000,\"refund\":999}"));
  assertThrows(IllegalArgumentException.class,()->pay.refundNotification(r));assertEquals(0L,number(s.one("select refunded_amount from shop_order where id=?",oid).get("refunded_amount")));
  r.getJSONObject("amount").put("refund",1000);pay.refundNotification(r);pay.refundNotification(r);
  assertEquals("refunded",s.one("select status from shop_case where id=?",cid).get("status"));
  assertEquals(1L,s.db.queryForObject("select count(*) from shop_finance_event where event_key=?",Long.class,"case_"+cid));
  assertEquals(1000L,number(s.one("select refunded_amount from shop_order where id=?",oid).get("refunded_amount")));
  r.put("refund_status","ABNORMAL");pay.refundNotification(r);assertTrue(pay.refundConfirmed(cid));
  return null;
 }finally{status.setRollbackOnly();}});}
 @Test void cancellationReconcilesAlreadyPaidRemoteOrder(){tx.execute(status->{try{
  fixture();prepare();doReturn(transaction()).when(gateway).query(anyString());s.orderAction(uid,oid,"cancel");
  assertEquals("paid",s.one("select status from shop_order where id=?",oid).get("status"));assertEquals("paid",s.one("select state from shop_order_funds where order_id=?",oid).get("state"));verify(gateway,never()).call(eq("POST"),endsWith("/close"),any(JSONObject.class));return null;
 }finally{status.setRollbackOnly();}});}
}
