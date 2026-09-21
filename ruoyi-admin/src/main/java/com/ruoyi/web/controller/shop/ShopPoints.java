package com.ruoyi.web.controller.shop;
import java.util.*;
import java.sql.Timestamp;
import com.alibaba.fastjson2.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ruoyi.web.controller.shop.ShopService.*;

@Service
public class ShopPoints {
 @Autowired @Lazy ShopService s;
 public JSONObject rules(){JSONObject r=JSON.parseObject(str(s.one("select content from shop_config where config_key='loyalty'").get("content")));r.putIfAbsent("autoReceive",false);r.putIfAbsent("receiveDays",15);r.putIfAbsent("returnDays",7);return r;}
 public long available(long uid){return s.jdbc().queryForObject("select coalesce(sum(remaining),0) from shop_point_lot where member_id=? and expires_at>now()",Long.class,uid);}
 private void lock(long uid){s.one("select id from shop_member where id=? for update",uid);}
 public Map<String,Object> account(long uid){Map<String,Object> r=new HashMap<>();r.put("available",available(uid));r.put("debt",s.jdbc().queryForObject("select coalesce(sum(amount),0) from shop_point_debt where member_id=?",Long.class,uid));r.put("rules",rules());r.put("ledger",s.jdbc().queryForList("select delta,kind,note,created_at from shop_point_ledger where member_id=? order by id desc limit 200",uid));return r;}
 public void quote(long uid,JSONObject body,List<JSONObject> lines,Map<String,Object> quote){
  JSONObject r=rules();long before=number(quote.get("amount")),used=0,discount=0;
  if(body.getBooleanValue("exchange")){check("C".equals(body.getString("channel")),"积分兑换仅用于个人零售");check(!body.getBooleanValue("coupon")&&body.getLongValue("couponId")==0,"兑换商品不叠加满减和优惠券");for(JSONObject line:lines){Map<String,Object> offer=s.one("select points_cost from shop_point_exchange where product_id=? and enabled=1 and (starts_at is null or starts_at<=now()) and (ends_at is null or ends_at>now())",line.get("id"));long count=number(offer.get("points_cost"))*line.getLongValue("quantity");used+=count;line.put("pointsUsed",count);line.put("netAmount",0);}check(used<=available(uid),"可用积分不足，请减少数量或选择其他商品");r.put("earnPerYuan",0);quote.put("pointsUsed",used);quote.put("pointsDiscount",before);quote.put("pointRules",r);quote.put("amount",0);quote.put("discount",number(quote.get("discount"))+before);quote.put("exchange",true);((JSONObject)quote.get("rules")).put("exchange",true);return;}
  if(body.getBooleanValue("usePoints")&&"C".equals(body.getString("channel"))){long rate=r.getLongValue("pointsPerYuan"),cap=ShopCommerce.portion(before,r.getLongValue("maxPercent"),100);used=Math.min(available(uid),ShopCommerce.portion(cap,rate,100));discount=ShopCommerce.portion(used,100,rate);used=discount==0?0:(discount*rate+99)/100;}
  long cumulative=0;for(JSONObject l:lines){long value=l.getLongValue("netAmount"),old=cumulative;cumulative+=value;l.put("netAmount",value-(ShopCommerce.portion(cumulative,discount,before)-ShopCommerce.portion(old,discount,before)));l.put("pointsUsed",ShopCommerce.portion(cumulative,used,before)-ShopCommerce.portion(old,used,before));}
  quote.put("pointsUsed",used);quote.put("pointsDiscount",discount);quote.put("pointRules",r);quote.put("amount",before-discount);quote.put("discount",number(quote.get("discount"))+discount);
 }
 private void ledger(long uid,String key,long delta,String kind,long oid,String note){s.jdbc().update("insert into shop_point_ledger(member_id,event_key,delta,kind,order_id,note) values(?,?,?,?,?,?)",uid,key,delta,kind,oid,note);}
 public void reserve(long uid,long oid,Map<String,Object> q){
  lock(uid);long count=number(q.get("pointsUsed"));check(count<=available(uid),"积分已变化，请重新结算");
  s.jdbc().update("insert into shop_order_points(order_id,member_id,points_used,discount_amount,rules_json) values(?,?,?,?,?)",oid,uid,count,q.get("pointsDiscount"),JSON.toJSONString(q.get("pointRules")));
  long left=count;for(Map<String,Object> lot:s.jdbc().queryForList("select * from shop_point_lot where member_id=? and remaining>0 and expires_at>now() order by expires_at,id for update",uid)){if(left==0)break;long n=Math.min(left,number(lot.get("remaining")));s.jdbc().update("update shop_point_lot set remaining=remaining-? where id=?",n,lot.get("id"));s.jdbc().update("insert into shop_point_spend values(?,?,?,0)",oid,lot.get("id"),n);left-=n;}
  check(left==0,"积分不足，请重新结算");if(count>0)ledger(uid,"hold_"+oid,-count,"hold",oid,"下单使用积分，未付款取消时返还");
 }
 public void paid(long oid){s.jdbc().update("update shop_order_points set state='paid' where order_id=? and state='reserved'",oid);}
 private void add(long uid,String key,long count,Timestamp expires){
  if(count==0)return;s.jdbc().update("insert ignore into shop_point_debt values(?,0)",uid);long debt=number(s.one("select amount from shop_point_debt where member_id=? for update",uid).get("amount")),repay=Math.min(debt,count);
  s.jdbc().update("update shop_point_debt set amount=amount-? where member_id=?",repay,uid);s.jdbc().update("insert into shop_point_lot(member_id,source_key,remaining,expires_at) values(?,?,?,?)",uid,key,count-repay,expires);
 }
 private void restore(long uid,long oid,long target){
  long previous=number(s.one("select returned_points from shop_order_points where order_id=?",oid).get("returned_points"));long delta=target-previous;if(delta<=0)return;
  long left=delta;for(Map<String,Object> a:s.jdbc().queryForList("select sp.*,l.expires_at from shop_point_spend sp join shop_point_lot l on l.id=sp.lot_id where sp.order_id=? order by l.expires_at,l.id for update",oid)){long n=Math.min(left,number(a.get("amount"))-number(a.get("returned")));if(n<=0)continue;Timestamp expiry=timestamp(a.get("expires_at"));if(expiry.getTime()<=System.currentTimeMillis())expiry=new Timestamp(System.currentTimeMillis()+7L*86400000);add(uid,"restore_"+oid+"_"+a.get("lot_id")+"_"+target,n,expiry);s.jdbc().update("update shop_point_spend set returned=returned+? where order_id=? and lot_id=?",n,oid,a.get("lot_id"));left-=n;if(left==0)break;}
  check(left==0,"积分返还记录不一致，请联系管理员");s.jdbc().update("update shop_order_points set returned_points=? where order_id=?",target,oid);ledger(uid,"restore_"+oid+"_"+target,delta,"return",oid,"按原订单返还抵扣积分");
 }
 public void release(long oid){List<Map<String,Object>> rows=s.jdbc().queryForList("select * from shop_order_points where order_id=? for update",oid);if(rows.isEmpty()||!"reserved".equals(rows.get(0).get("state")))return;Map<String,Object> p=rows.get(0);long uid=number(p.get("member_id"));lock(uid);restore(uid,oid,number(p.get("points_used")));s.jdbc().update("update shop_order_points set state='cancelled' where order_id=?",oid);}
 public void refunded(long oid){
  List<Map<String,Object>> rows=s.jdbc().queryForList("select * from shop_order_points where order_id=? for update",oid);if(rows.isEmpty())return;Map<String,Object> p=rows.get(0);if(!"paid".equals(p.get("state")))return;long uid=number(p.get("member_id"));lock(uid);
  Map<String,Object> o=s.one("select amount,refunded_amount,status,shipping_amount,refunded_shipping_amount from shop_order where id=?",oid);long total=number(o.get("amount"))-number(o.get("shipping_amount")),refunded=number(o.get("refunded_amount"))-number(o.get("refunded_shipping_amount"));
  
  long target=0;
  if("refunded".equals(o.get("status")))target=number(p.get("points_used"));
  else{Map<Long,Long> returned=new HashMap<>();for(Map<String,Object> c:s.jdbc().queryForList("select items_json from shop_case where order_id=? and status='refunded'",oid))for(Object value:JSON.parseArray(str(c.get("items_json")))){JSONObject item=JSON.parseObject(JSON.toJSONString(value));long lid=item.getLongValue("id");returned.put(lid,returned.getOrDefault(lid,0L)+item.getLongValue("quantity"));}
   for(Map<String,Object> line:s.jdbc().queryForList("select id,quantity,points_used from shop_order_item where order_id=?",oid))target+=ShopCommerce.portion(returned.getOrDefault(number(line.get("id")),0L),number(line.get("points_used")),number(line.get("quantity")));
  }restore(uid,oid,target);
  JSONObject saved=JSON.parseObject(str(p.get("rules_json")));long entitlement="refunded".equals(o.get("status"))?0:(total-refunded)/100*saved.getLongValue("earnPerYuan");long expired=s.jdbc().queryForObject("select coalesce(sum(-e.delta),0) from shop_point_lot l join shop_point_ledger e on e.member_id=l.member_id and e.event_key=concat('expire_',l.id) where l.member_id=? and l.source_key=?",Long.class,uid,"earn_"+oid);long revoke=Math.max(0,number(p.get("earned_points"))-entitlement-expired),delta=revoke-number(p.get("revoked_points"));
  if(delta>0){long left=delta;for(Map<String,Object> lot:s.jdbc().queryForList("select * from shop_point_lot where member_id=? and remaining>0 and expires_at>now() order by expires_at,id for update",uid)){long n=Math.min(left,number(lot.get("remaining")));s.jdbc().update("update shop_point_lot set remaining=remaining-? where id=?",n,lot.get("id"));left-=n;if(left==0)break;}if(left>0)s.jdbc().update("insert into shop_point_debt values(?,?) on duplicate key update amount=amount+values(amount)",uid,left);s.jdbc().update("update shop_order_points set revoked_points=? where order_id=?",revoke,oid);ledger(uid,"revoke_"+oid+"_"+revoke,-delta,"revoke",oid,"退款撤回赠送积分，不足部分由后续积分抵扣");}
 }
 public void received(long oid){Map<String,Object> o=s.one("select status from shop_order where id=?",oid);if(!"completed".equals(o.get("status")))return;s.jdbc().update("update shop_order set received_at=coalesce(received_at,now()) where id=?",oid);List<Map<String,Object>> rows=s.jdbc().queryForList("select * from shop_order_points where order_id=?",oid);if(rows.isEmpty())return;JSONObject r=JSON.parseObject(str(rows.get(0).get("rules_json")));s.jdbc().update("update shop_order_points set ready_at=date_add(now(),interval ? day) where order_id=? and ready_at is null",r.getLongValue("settlementDays"),oid);}
 @Transactional public void settle(long oid){
  Map<String,Object> owner=s.one("select member_id from shop_order where id=?",oid);long uid=number(owner.get("member_id"));lock(uid);Map<String,Object> o=s.one("select * from shop_order where id=? for update",oid),p=s.one("select *,ready_at<=now() due from shop_order_points where order_id=? for update",oid);
  if(Boolean.TRUE.equals(p.get("settled"))||"1".equals(str(p.get("settled")))||number(p.get("due"))!=1||!"completed".equals(o.get("status")))return;
  if(s.jdbc().queryForObject("select count(*) from shop_case where order_id=? and status not in ('completed','refunded','rejected')",Long.class,oid)>0)return;
  JSONObject r=JSON.parseObject(str(p.get("rules_json")));long points=(number(o.get("amount"))-number(o.get("shipping_amount"))-number(o.get("refunded_amount"))+number(o.get("refunded_shipping_amount")))/100*r.getLongValue("earnPerYuan");
  add(uid,"earn_"+oid,points,new Timestamp(System.currentTimeMillis()+r.getLongValue("expiryDays")*86400000L));
  s.jdbc().update("update shop_order_points set settled=1,earned_points=? where order_id=?",points,oid);if(points>0)ledger(uid,"earn_"+oid,points,"earn",oid,"收货等待期结束，发放消费积分");
 }
 @Transactional public void expire(long uid){lock(uid);for(Map<String,Object> lot:s.jdbc().queryForList("select * from shop_point_lot where member_id=? and remaining>0 and expires_at<=now() for update",uid)){long count=number(lot.get("remaining"));s.jdbc().update("update shop_point_lot set remaining=0 where id=?",lot.get("id"));ledger(uid,"expire_"+lot.get("id"),-count,"expire",0,"积分已到期");}}
 @Transactional public void saveRules(JSONObject body,String who){JSONObject clean=new JSONObject();for(String key:new String[]{"pointsPerYuan","earnPerYuan","maxPercent","expiryDays","settlementDays","afterSaleDays","orderMinutes","commissionDays"}){long min=Arrays.asList("earnPerYuan","maxPercent").contains(key)?0:1;long max="maxPercent".equals(key)?100:10000;clean.put(key,integer(body.get(key),min,max,"请检查积分比例、期限和订单时限"));}clean.put("autoReceive",body.getBooleanValue("autoReceive"));clean.put("receiveDays",integer(body.getOrDefault("receiveDays",15),1,365,"自动收货天数为1至365"));clean.put("returnDays",integer(body.getOrDefault("returnDays",7),1,365,"寄回天数为1至365"));check(clean.getLongValue("settlementDays")>=clean.getLongValue("afterSaleDays")&&clean.getLongValue("commissionDays")>=clean.getLongValue("afterSaleDays"),"积分和佣金等待期不能短于普通售后窗口");s.jdbc().update("update shop_config set content=? where config_key='loyalty'",clean.toJSONString());s.event("loyalty",0,"更新新订单会员与结算规则",who);}
}
