package com.ruoyi.web.controller.shop;
import java.util.*;import java.time.*;import com.alibaba.fastjson2.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.context.annotation.Lazy;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import static com.ruoyi.web.controller.shop.ShopService.*;
@Service public class ShopRebate {
 @Autowired @Lazy ShopService s;
 public JSONObject settings(){return JSON.parseObject(str(s.one("select content from shop_config where config_key='rebate'").get("content")));}
 private long company(long uid){Map<String,Object> m=s.one("select company_id from shop_company_member where member_id=? and enabled=1",uid);return number(m.get("company_id"));}
 private void lock(long id){s.one("select id from shop_company where id=? for update",id);}
 private boolean formal(){return !s.isDemo();}
 private String validOrder(String oid){return "exists(select 1 from shop_order ro join shop_member rm on rm.id=ro.member_id where ro.id="+oid+" and ro.is_test=0 and rm.is_test=0 and not exists(select 1 from shop_payment rp where rp.order_id=ro.id and rp.mode='simulation'))";}
 private String paidSource(String oid){return formal()?" and "+validOrder(oid)+" and exists(select 1 from shop_payment rp where rp.order_id="+oid+" and rp.mode<>'simulation')":"";}
 private String ledgerScope(String alias){return formal()?" and (("+alias+".period is not null and "+alias+".order_id is null and exists(select 1 from shop_rebate_period p where p.company_id="+alias+".company_id and p.period="+alias+".period)) or ("+alias+".order_id is not null and "+validOrder(alias+".order_id")+"))":"";}
 // Recalculate old credited periods before spending: switching out of demo mode
 // must not turn its settled rebate into real purchasing power. Keep correction entries.
 @Transactional public void reconcile(long cid){if(!formal())return;lock(cid);for(Map<String,Object> r:s.jdbc().queryForList("select period from shop_rebate_period where company_id=? order by period",cid))settle(cid,str(r.get("period")));}
 @Transactional public long available(long cid){lock(cid);reconcile(cid);long sum=0;for(Map<String,Object> r:s.jdbc().queryForList("select l.delta from shop_rebate_ledger l where l.company_id=?"+ledgerScope("l")+" order by l.id for update",cid))sum+=number(r.get("delta"));return sum;}
 public boolean canPay(Map<String,Object> order){
  if(!formal()||number(order.get("amount"))<=0)return false;
  long oid=number(order.get("id"));List<Map<String,Object>> rows=s.jdbc().queryForList("select company_id from shop_rebate_order where order_id=?",oid);if(rows.isEmpty())return false;
  long cid=number(rows.get(0).get("company_id"));lock(cid);Map<String,Object> r=s.one("select * from shop_rebate_order where order_id=? for update",oid);
  if(!"reserved".equals(r.get("state"))||number(r.get("used_amount"))!=number(order.get("amount"))||number(r.get("returned_amount"))!=0||cid!=number(order.get("company_id")))return false;
  if(s.jdbc().queryForObject("select count(*) from shop_order where id=? and "+validOrder("shop_order.id"),Long.class,oid)!=1)return false;
  if(s.jdbc().queryForObject("select count(*) from shop_order_funds where order_id=? and member_id=? and state='reserved' and wallet_amount=0 and external_amount=0",Long.class,oid,order.get("member_id"))!=1)return false;
  if(s.jdbc().queryForObject("select count(*) from shop_rebate_ledger where company_id=? and order_id=? and event_key=? and delta=? and period is null",Long.class,cid,oid,"reserve_"+oid,-number(r.get("used_amount")))!=1)return false;
  return available(cid)>=0;
 }
 public boolean paidByRebate(long oid){return s.jdbc().queryForObject("select count(*) from shop_payment where order_id=? and mode='rebate'",Long.class,oid)==1;}
 @Transactional public void quote(long uid,JSONObject b,Map<String,Object> q){q.put("rebateUsed",0);if(!b.getBooleanValue("useRebate"))return;check("B".equals(b.get("channel")),"采购返利只能用于企业订单");check(!"credit".equals(b.get("paymentMethod"))&&!b.getBooleanValue("useBalance"),"返利抵扣请选择普通付款，并关闭个人余额");long cid=company(uid);lock(cid);long used=Math.min(Math.max(0,available(cid)),number(q.get("goodsAmount")));q.put("rebateUsed",used);q.put("externalAmount",number(q.get("amount"))-used);}
 public void reserve(long uid,long oid,JSONObject b,Map<String,Object> q){if(!"B".equals(b.get("channel")))return;long cid=company(uid),used=number(q.get("rebateUsed"));lock(cid);check(!b.getBooleanValue("useRebate")||b.getLongValue("expectedRebate")==used,"企业返利余额有变化，请重新确认");check(used<=Math.max(0,available(cid)),"企业可用返利不足，请重新确认");s.jdbc().update("insert into shop_rebate_order(order_id,company_id,rules_json,used_amount) values(?,?,?,?)",oid,cid,settings().toJSONString(),used);if(used>0)entry(cid,"reserve_"+oid,-used,oid,null,"采购下单占用企业返利");}
 private void entry(long cid,String key,long delta,Long oid,String period,String note){s.jdbc().update("insert into shop_rebate_ledger(company_id,event_key,delta,order_id,period,note) values(?,?,?,?,?,?)",cid,key,delta,oid,period,note);}
 public void paid(long oid){s.jdbc().update("update shop_rebate_order set state='paid' where order_id=? and state='reserved'",oid);}
 public void release(long oid){List<Map<String,Object>> rows=s.jdbc().queryForList("select * from shop_rebate_order where order_id=?",oid);if(rows.isEmpty())return;Map<String,Object> r=rows.get(0);long cid=number(r.get("company_id"));lock(cid);r=s.one("select * from shop_rebate_order where order_id=? for update",oid);if(!"reserved".equals(r.get("state")))return;if(number(r.get("used_amount"))>0)entry(cid,"release_"+oid,number(r.get("used_amount")),oid,null,"订单关闭返还企业返利");s.jdbc().update("update shop_rebate_order set state='cancelled' where order_id=?",oid);}
 public long used(long oid){List<Map<String,Object>> r=s.jdbc().queryForList("select used_amount from shop_rebate_order where order_id=?",oid);return r.isEmpty()?0:number(r.get(0).get("used_amount"));}
 public void refresh(long oid){List<Map<String,Object>> r=s.jdbc().queryForList("select o.paid_at,r.company_id from shop_order o join shop_rebate_order r on r.order_id=o.id where o.id=? and o.paid_at is not null",oid);if(!r.isEmpty())settle(number(r.get(0).get("company_id")),timestamp(r.get(0).get("paid_at")).toLocalDateTime().toLocalDate().toString().substring(0,7));}
 public long returned(long oid){List<Map<String,Object>> r=s.jdbc().queryForList("select returned_amount from shop_rebate_order where order_id=?",oid);return r.isEmpty()?0:number(r.get(0).get("returned_amount"));}
 public void refund(long oid){List<Map<String,Object>> rows=s.jdbc().queryForList("select * from shop_rebate_order where order_id=?",oid);if(rows.isEmpty())return;Map<String,Object> r=rows.get(0);long cid=number(r.get("company_id"));lock(cid);Map<String,Object> o=s.one("select amount,refunded_amount from shop_order where id=?",oid);long total=ShopCommerce.portion(number(o.get("refunded_amount")),number(r.get("used_amount")),number(o.get("amount"))),delta=total-number(r.get("returned_amount"));if(delta>0){entry(cid,"refund_"+oid+"_"+total,delta,oid,null,"按原支付组成返还企业返利");s.jdbc().update("update shop_rebate_order set returned_amount=? where order_id=?",total,oid);}}
 @Transactional public void settle(long cid,String month){
  LocalDate start=YearMonth.parse(month).atDay(1),end=start.plusMonths(1);lock(cid);
  List<Map<String,Object>> orders=s.jdbc().queryForList("select o.*,r.rules_json rebate_rules from shop_order o join shop_rebate_order r on r.order_id=o.id where r.company_id=? and o.paid_at>=? and o.paid_at<?"+paidSource("o.id"),cid,java.sql.Timestamp.valueOf(start.atStartOfDay()),java.sql.Timestamp.valueOf(end.atStartOfDay()));
  List<Map<String,Object>> periods=s.jdbc().queryForList("select amount from shop_rebate_period where company_id=? and period=? for update",cid,month);
  if(orders.isEmpty()&&periods.isEmpty())return;
  long oldAmount=periods.isEmpty()?0:number(periods.get(0).get("amount")),net=0,quantity=0;boolean blocked=false,ready=true;
  for(Map<String,Object> o:orders){
   net+=netAmount(o);long oid=number(o.get("id"));quantity+=netQty(oid);JSONObject rule=JSON.parseObject(str(o.get("rebate_rules")));
   if(LocalDate.now().isBefore(end.minusDays(1).plusDays(rule.getLongValue("settleDays"))))ready=false;
   if(s.jdbc().queryForObject("select count(*) from shop_case where order_id=? and status not in ('refunded','completed','rejected')",Long.class,oid)>0||s.jdbc().queryForObject("select count(*) from shop_after_sale where order_id=? and status='pending'",Long.class,oid)>0)blocked=true;
  }
  List<Map<String,Object>> first=s.jdbc().queryForList("select o.id from shop_order o where o.company_id=? and o.paid_at is not null and o.amount-o.shipping_amount>o.refunded_amount-o.refunded_shipping_amount"+paidSource("o.id")+" order by o.paid_at,o.id limit 1",cid);
  long firstId=first.isEmpty()?0:number(first.get(0).get("id"));java.math.BigInteger numerator=java.math.BigInteger.ZERO;
  for(Map<String,Object> o:orders){
   JSONObject rule=JSON.parseObject(str(o.get("rebate_rules")));
   if(!rule.getBooleanValue("enabled")||rule.getBooleanValue("firstOnly")&&number(o.get("id"))!=firstId)continue;
   JSONArray audience=rule.getJSONArray("companies");boolean applies=audience==null||audience.isEmpty();if(audience!=null)for(Object id:audience)if(number(id)==cid)applies=true;if(!applies)continue;
   long metric="quantity".equals(rule.getString("metric"))?quantity:net,rate=0;
   for(Object obj:rule.getJSONArray("steps")){JSONObject step=JSON.parseObject(JSON.toJSONString(obj));if(metric>=step.getLongValue("threshold"))rate=step.getLongValue("rate");}
   numerator=numerator.add(java.math.BigInteger.valueOf(netAmount(o)).multiply(java.math.BigInteger.valueOf(rate)));
  }
  long amount=numerator.divide(java.math.BigInteger.valueOf(10000)).longValueExact();
  // Hold new earnings during an open after-sale, but never keep an overstated old credit.
  if(!ready||blocked)amount=Math.min(oldAmount,amount);
  s.jdbc().update("insert ignore into shop_rebate_period(company_id,period) values(?,?)",cid,month);
  long delta=amount-oldAmount;
  if(delta!=0)entry(cid,"settle_"+cid+"_"+month+"_"+UUID.randomUUID().toString(),delta,null,month,delta>0?"月度净采购返利入账":"采购退款或付款来源校验重新核算扣回返利");
  String status=blocked?"held":!ready?"waiting":"settled",note=blocked?"相关采购售后未完成，暂挂新增返利":!ready?"等待月底后的结算日":"按该月净有效采购和各订单原规则核算";
  s.jdbc().update("update shop_rebate_period set amount=?,status=?,note=? where company_id=? and period=?",amount,status,note,cid,month);
 }
 private long netAmount(Map<String,Object> o){return Math.max(0,number(o.get("amount"))-number(o.get("shipping_amount"))-number(o.get("refunded_amount"))+number(o.get("refunded_shipping_amount")));}
 private long netQty(long oid){long qty=s.jdbc().queryForObject("select coalesce(sum(quantity),0) from shop_order_item where order_id=?",Long.class,oid);for(Map<String,Object> c:s.jdbc().queryForList("select items_json from shop_case where order_id=? and status='refunded'",oid))for(Object obj:JSON.parseArray(str(c.get("items_json"))))qty-=(JSON.parseObject(JSON.toJSONString(obj))).getLongValue("quantity");if("refunded".equals(s.one("select status from shop_order where id=?",oid).get("status")))return 0;return Math.max(0,qty);}
 @Transactional public Map<String,Object> summary(long uid){long cid=company(uid);Map<String,Object> r=new HashMap<>();r.put("available",available(cid));r.put("periods",s.jdbc().queryForList("select * from shop_rebate_period where company_id=? order by period desc",cid));r.put("ledger",s.jdbc().queryForList("select l.* from shop_rebate_ledger l where l.company_id=?"+ledgerScope("l")+" order by l.id desc limit 500",cid));return r;}
 @Transactional public Map<String,Object> adminSummary(){
  List<Map<String,Object>> companies=s.jdbc().queryForList("select id,name from shop_company order by id");
  for(Map<String,Object> c:companies)reconcile(number(c.get("id")));
  Map<String,Object> out=new HashMap<>();out.put("settings",settings());out.put("companies",companies);
  out.put("periods",s.jdbc().queryForList("select r.*,c.name"+ShopQueryFields.fields("c.owner_id")+" from shop_rebate_period r join shop_company c on c.id=r.company_id order by r.period desc"));
  out.put("ledger",s.jdbc().queryForList("select r.*,c.name"+ShopQueryFields.fields("c.owner_id")+" from shop_rebate_ledger r join shop_company c on c.id=r.company_id where 1=1"+ledgerScope("r")+" order by r.id desc"));
  return out;
 }
 @Transactional public void save(JSONObject b,String who){JSONObject r=new JSONObject();r.put("enabled",b.getBooleanValue("enabled"));String metric=b.getString("metric");check(Arrays.asList("amount","quantity").contains(metric),"请选择按金额或数量阶梯");r.put("metric",metric);r.put("firstOnly",b.getBooleanValue("firstOnly"));r.put("settleDays",integer(b.get("settleDays"),1,90,"月底后1至90天结算"));JSONArray companies=b.getJSONArray("companies");if(companies==null)companies=new JSONArray();check(companies.size()<=1000,"指定企业最多1000家");for(Object id:companies)s.one("select id from shop_company where id=?",integer(id,1,Long.MAX_VALUE,"企业不正确"));r.put("companies",companies);JSONArray steps=b.getJSONArray("steps");check(steps!=null&&!steps.isEmpty()&&steps.size()<=20,"请填写1至20档返利");long previous=-1;for(Object obj:steps){JSONObject step=JSON.parseObject(JSON.toJSONString(obj));long threshold=integer(step.get("threshold"),0,100000000000L,"门槛需为非负整数"),rate=integer(step.get("rate"),0,5000,"返利比例为0至50%");check(threshold>previous,"阶梯门槛须从小到大且不能重复");previous=threshold;}r.put("steps",steps);s.jdbc().update("update shop_config set content=? where config_key='rebate'",r.toJSONString());s.event("rebate",0,"更新新订单返利规则",who);}
}
