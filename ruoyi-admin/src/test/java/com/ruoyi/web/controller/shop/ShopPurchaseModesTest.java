package com.ruoyi.web.controller.shop;

import com.alibaba.fastjson2.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Local MySQL boundary tests; account, config and order fixtures always roll back. */
@EnabledIfSystemProperty(named="wechat.test.properties",matches=".+")
class ShopPurchaseModesTest {
 ShopService s;ShopPartner partner;TransactionTemplate tx;long uid;
 @BeforeEach void setup()throws Exception{
  Properties config=new Properties();try(Reader in=new InputStreamReader(new FileInputStream(System.getProperty("wechat.test.properties")),StandardCharsets.UTF_8)){config.load(in);}
  String url=config.getProperty("spring.datasource.druid.master.url");assertTrue(url.startsWith("jdbc:mysql://localhost:")||url.startsWith("jdbc:mysql://127.0.0.1:"));
  DriverManagerDataSource ds=new DriverManagerDataSource(url,config.getProperty("spring.datasource.druid.master.username"),config.getProperty("spring.datasource.druid.master.password"));s=new ShopService();s.db=new JdbcTemplate(ds);partner=new ShopPartner();partner.s=s;ReflectionTestUtils.setField(partner,"includeTestOrders",true);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
 }
 void fixture(){String key="purchase19-"+UUID.randomUUID();s.db.update("insert into shop_member(identity_key,nickname,account_kind,is_test,distributor_state) values(?,?,'demo',1,'approved')",key,key);uid=s.db.queryForObject("select last_insert_id()",Long.class);s.db.update("update shop_config set content=? where config_key='partner'","{\"tiers\":[{\"name\":\"3.8折客户\",\"rate\":3800,\"referenceAmount\":1980000},{\"name\":\"2.8折客户\",\"rate\":2800,\"referenceAmount\":4980000},{\"name\":\"共建合伙人\",\"rate\":2200,\"referenceAmount\":0}],\"excludedProducts\":[],\"catalogGroups\":[]}");}
 void rollback(Runnable test){tx.execute(status->{try{fixture();test.run();return null;}finally{status.setRollbackOnly();}});assertEquals(0L,s.db.queryForObject("select count(*) from shop_member where id=?",Long.class,uid));}
 long order(String channel,String type,String state,long amount,long shipping,long refunded,long refundedShipping,boolean paid){String key=UUID.randomUUID().toString();s.db.update("insert into shop_order(order_no,member_id,channel,business_type,status,subtotal,discount,amount,shipping_amount,refunded_amount,refunded_shipping_amount,paid_at,address_json,rules_json,request_key,expires_at,is_test) values(?,?,?,?,?,?,0,?,?,?,?,?,'{}','{}',?,now(),1)","P19"+key.substring(0,25),uid,channel,type,state,amount-shipping,amount,shipping,refunded,refundedShipping,paid?new java.sql.Timestamp(System.currentTimeMillis()):null,key);return s.db.queryForObject("select last_insert_id()",Long.class);}
 @Test void approvedMemberStartsAtFirstTierWithNoSpending(){rollback(()->{JSONObject p=partner.profile(uid);assertTrue(p.getBooleanValue("enabled"));assertEquals(1,p.getIntValue("tier"));assertEquals(3800,p.getIntValue("rate"));assertEquals(0,p.getLongValue("netSpend"));assertEquals(4980000,p.getLongValue("remainingAmount"));assertEquals(0,partner.settings().getJSONArray("tiers").getJSONObject(0).getLongValue("referenceAmount"));});}
 @Test void spendIncludesRetailAndPartnerNetGoodsButNotLegacyCancelledUnpaidOrShipping(){rollback(()->{
  order("C","retail","paid",103000,3000,22000,2000,true); // 80,000 goods net
  order("C","partner","completed",51000,1000,10000,0,true); // 40,000 goods net
  order("B","enterprise","completed",9000000,0,0,0,true);
  order("C","enterprise","completed",9000000,0,0,0,true);
  order("C","retail","cancelled",9000000,0,0,0,true);
  order("C","retail","pending",9000000,0,0,0,false);
  order("C","retail","refunded",100000,0,100000,0,true);
  assertEquals(120000,partner.profile(uid).getLongValue("netSpend"));
 });}
 @Test void exactUpgradeBoundaryAndRefundRecalculationDoNotRewriteOrderPrice(){rollback(()->{
  long id=order("C","retail","paid",4980000,0,0,0,true);assertEquals(2,partner.profile(uid).getIntValue("tier"));
  s.db.update("update shop_order set refunded_amount=1 where id=?",id);JSONObject p=partner.profile(uid);assertEquals(1,p.getIntValue("tier"));assertEquals(1,p.getLongValue("remainingAmount"));assertEquals(4980000L,s.db.queryForObject("select amount from shop_order where id=?",Long.class,id));
 });}
 @Test void unapprovedAccountsCannotBeAssignedAnActiveTierEvenWithEnoughSpending(){rollback(()->{
  order("C","retail","paid",9000000,0,0,0,true);
  for(String state:Arrays.asList("none","pending","rejected")){s.db.update("update shop_member set distributor_state=? where id=?",state,uid);assertFalse(partner.profile(uid).getBooleanValue("enabled"));for(String mode:Arrays.asList("auto","fixed"))assertThrows(IllegalArgumentException.class,()->partner.assign(JSONObject.of("memberId",uid,"mode",mode,"tier",3),"fixture"));}
 });}
 @Test void explicitTierWinsAndBlockedModeStopsPurchases(){rollback(()->{
  order("C","retail","paid",9000000,0,0,0,true);partner.assign(JSONObject.of("memberId",uid,"mode","fixed","tier",1),"fixture");assertEquals(1,partner.require(uid).getIntValue("tier"));assertFalse(partner.profile(uid).containsKey("remainingAmount"));
  partner.assign(JSONObject.of("memberId",uid,"mode","fixed","tier",3),"fixture");assertEquals(2200,partner.require(uid).getIntValue("rate"));
  partner.assign(JSONObject.of("memberId",uid,"mode","blocked"),"fixture");assertThrows(IllegalArgumentException.class,()->partner.require(uid));
  partner.assign(JSONObject.of("memberId",uid,"mode","auto"),"fixture");assertEquals(2,partner.require(uid).getIntValue("tier"));
 });}
 @Test void oldClientsCannotReintroduceFirstTierThreshold(){rollback(()->{
  JSONObject config=partner.settings();config.getJSONArray("tiers").getJSONObject(0).put("referenceAmount",1980000);partner.save(config,"fixture");assertEquals(0,partner.settings().getJSONArray("tiers").getJSONObject(0).getLongValue("referenceAmount"));
  config.getJSONArray("tiers").getJSONObject(1).put("referenceAmount",0);assertThrows(IllegalArgumentException.class,()->partner.save(config,"fixture"));
 });}
}
