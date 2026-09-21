package com.ruoyi.web.controller.shop;

import java.util.*;
import java.sql.Timestamp;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ShopOrderProgressTest {
 ShopService service;JdbcTemplate db;ShopOrderProgress progress;
 Map<String,Object> row(Object...pairs){Map<String,Object> r=new HashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
 Timestamp at(String time){return Timestamp.valueOf("2026-09-20 "+time);}
 @BeforeEach void setup(){service=mock(ShopService.class);db=mock(JdbcTemplate.class);when(service.jdbc()).thenReturn(db);progress=new ShopOrderProgress();progress.s=service;}
 @Test void anotherBuyerCannotReadHistory(){
  when(service.companyOrder(2,19,"view",false)).thenThrow(new IllegalArgumentException("无权访问"));
  assertThrows(IllegalArgumentException.class,()->progress.read(2,19));verifyNoInteractions(db);
 }
 @Test void cancelledUnpaidOrderNeverGetsPaymentOrInventedTime(){
  when(service.companyOrder(1,19,"view",false)).thenReturn(row("created_at",at("10:00:00"),"status","cancelled"));
  List<Map<String,Object>> events=progress.read(1,19);
  assertEquals(2,events.size());assertEquals("订单已提交",events.get(0).get("title"));assertEquals("订单已取消",events.get(1).get("title"));assertNull(events.get(1).get("time"));
 }
 @Test void recordsSortByActualTimeAndNeverReturnPrivateAuditText(){
  when(service.companyOrder(1,19,"view",false)).thenReturn(row("created_at",at("10:00:00"),"paid_at",at("10:01:00"),"status","refunded"));
  when(db.queryForList(startsWith("select id,carrier"),eq(19L))).thenReturn(Arrays.asList(row("id",1L,"carrier","顺丰","tracking","SF123","created_at",at("10:03:00"))));
  when(db.queryForList(startsWith("select id,type"),eq(19L))).thenReturn(Arrays.asList(row("id",7L,"type","return","created_at",at("10:10:00"))));
  when(db.queryForList(startsWith("select e.id"),eq(19L),eq(19L))).thenReturn(Arrays.asList(row("id",99L,"object_type","case","action","approve","created_at",at("10:11:00")),row("id",100L,"object_type","order","action","internal private account text","operator_name","secret_operator","created_at",at("10:15:00"))));
  when(db.queryForList(startsWith("select event_key,amount"),eq(19L))).thenReturn(Arrays.asList(row("event_key","case_2","amount",1234L,"occurred_at",at("10:20:00"))));
  List<Map<String,Object>> events=progress.read(1,19);assertEquals(6,events.size());
  assertEquals("付款已确认",events.get(1).get("title"));assertEquals("售后审核通过",events.get(4).get("title"));assertEquals("退款处理完成",events.get(5).get("title"));assertTrue(events.get(5).get("detail").toString().contains("12.34"));
  assertFalse(events.toString().contains("secret_operator"));assertFalse(events.toString().contains("private account"));
 }
 @Test void cancellationUsesRecordedTimeAndHasNoDuplicateFallback(){
  when(service.companyOrder(3,19,"view",false)).thenReturn(row("created_at",at("10:00:00"),"status","cancelled"));
  when(db.queryForList(startsWith("select e.id"),eq(19L),eq(19L))).thenReturn(Arrays.asList(row("id",10L,"object_type","order","action","cancel","created_at",at("10:05:00"))));
  List<Map<String,Object>> events=progress.read(3,19);assertEquals(2,events.size());assertEquals(at("10:05:00").getTime(),events.get(1).get("time"));
 }
 @Test void confirmedWechatRefundDisplaysARealCompletionNode(){
  when(service.companyOrder(1,19,"view",false)).thenReturn(row("created_at",at("10:00:00"),"status","refunded"));
  when(db.queryForList(startsWith("select case_id,state"),eq(19L))).thenReturn(Arrays.asList(row("case_id",7L,"state","SUCCESS","created_at",at("10:04:00"),"updated_at",at("10:05:00"))));
  when(db.queryForList(startsWith("select event_key,amount"),eq(19L))).thenReturn(Arrays.asList(row("event_key","case_7","amount",1L,"occurred_at",at("10:05:00"),"wechat_amount",1L)));
  List<Map<String,Object>> events=progress.read(1,19);assertEquals("微信退款成功",events.get(2).get("title"));assertTrue(events.get(2).get("detail").toString().contains("0.01"));
 }
 @Test void mixedRefundDoesNotOverstateTheWechatAmount(){
  when(service.companyOrder(1,19,"view",false)).thenReturn(row("created_at",at("10:00:00"),"status","refunded"));
  when(db.queryForList(startsWith("select event_key,amount"),eq(19L))).thenReturn(Arrays.asList(row("event_key","case_7","amount",1000L,"occurred_at",at("10:05:00"),"wechat_amount",600L)));
  String detail=progress.read(1,19).get(1).get("detail").toString();assertTrue(detail.contains("微信已原路退回 ¥6.00"));assertTrue(detail.contains("其余 ¥4.00"));
 }
 @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="wechat.test.properties",matches=".+")
 void realDatabaseHistoryUsesExistingTablesAndRefundLedger()throws Exception{
  Properties config=new Properties();try(java.io.Reader reader=new java.io.InputStreamReader(new java.io.FileInputStream(System.getProperty("wechat.test.properties")),java.nio.charset.StandardCharsets.UTF_8)){config.load(reader);}
  String url=config.getProperty("spring.datasource.druid.master.url");assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:")||url.startsWith("jdbc:mysql://localhost:"));
  org.springframework.jdbc.datasource.DriverManagerDataSource ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,config.getProperty("spring.datasource.druid.master.username"),config.getProperty("spring.datasource.druid.master.password"));
  JdbcTemplate actual=new JdbcTemplate(ds);when(service.jdbc()).thenReturn(actual);
  new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)).execute(tx->{try{
   String key=UUID.randomUUID().toString().replace("-","");
   actual.update("insert into shop_member(identity_key,nickname,account_kind,is_test) values(?,?,'wechat',1)","timeline_"+key,"回滚测试");long uid=actual.queryForObject("select last_insert_id()",Long.class);
   actual.update("insert into shop_order(order_no,member_id,channel,status,subtotal,discount,amount,address_json,rules_json,request_key,paid_at,is_test) values(?,?,'C','refunded',13400,0,13400,'{}','{}',?,now(),1)","TL"+key,uid,key);long oid=actual.queryForObject("select last_insert_id()",Long.class);
   actual.update("insert into shop_finance_event(event_key,kind,object_id,amount,fee,occurred_at) values(?,'refund',?,13400,0,now())","timeline_"+key,oid);
   when(service.companyOrder(uid,oid,"view",false)).thenReturn(actual.queryForMap("select * from shop_order where id=?",oid));
   List<Map<String,Object>> events=progress.read(uid,oid);assertEquals(3,events.size());assertTrue(events.stream().anyMatch(e->"退款处理完成".equals(e.get("title"))&&e.get("detail").toString().contains("134.00")));
   return null;
  }finally{tx.setRollbackOnly();}});
 }
}
