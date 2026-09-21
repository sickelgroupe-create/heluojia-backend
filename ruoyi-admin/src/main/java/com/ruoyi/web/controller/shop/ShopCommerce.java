package com.ruoyi.web.controller.shop;

import com.alibaba.fastjson2.*;
import java.util.*;
import java.sql.Timestamp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ruoyi.web.controller.shop.ShopService.*;

/** Account-owned carts and coupon lifecycle. All amounts are integer cents. */
@Service
public class ShopCommerce {
 @Autowired @Lazy private ShopService s;

 private void channel(long uid,String channel){
  check(Arrays.asList("C","B").contains(channel),"请选择零售或采购购物车");
  if("B".equals(channel))s.companyAccess(uid);
 }
 public Map<String,Object> cart(long uid,String channel){
  channel(uid,channel);
  List<Map<String,Object>> rows=s.jdbc().queryForList("select revision,items_json from shop_cart where member_id=? and channel=?",uid,channel);
  Map<String,Object> result=new HashMap<>();
  result.put("revision",rows.isEmpty()?0:rows.get(0).get("revision"));
  result.put("items",rows.isEmpty()?new JSONArray():JSON.parseArray(str(rows.get(0).get("items_json"))));
  return result;
 }
 @Transactional public Map<String,Object> saveCart(long uid,String channel,JSONObject body){
  channel(uid,channel);if("B".equals(channel))s.companyPurchase(uid);s.one("select id from shop_member where id=? for update",uid);
  JSONArray items=body.getJSONArray("items");check(items!=null&&items.size()<=100,"购物车最多保留100种商品");
  JSONArray clean=new JSONArray();Set<Long> seen=new HashSet<>();
  for(Object value:items){JSONObject i=JSON.parseObject(JSON.toJSONString(value));long id=integer(i.get("id"),1,Long.MAX_VALUE,"商品编号不正确");
   check(seen.add(id),"请合并重复商品");long qty=integer(i.get("quantity"),1,10000,"商品数量需为1至10000的整数");
   Map<String,Object> p=s.one("select id,name,spec,image from shop_product where id=?",id);
   JSONObject row=new JSONObject(p);row.put("quantity",qty);row.put("checked",!Boolean.FALSE.equals(i.getBoolean("checked")));clean.add(row);
  }
  long revision=integer(body.get("revision"),0,Long.MAX_VALUE-1,"请刷新购物车后重试");
  s.jdbc().update("insert ignore into shop_cart(member_id,channel,revision,items_json) values(?,?,0,'[]')",uid,channel);
  check(s.jdbc().update("update shop_cart set items_json=?,revision=revision+1 where member_id=? and channel=? and revision=?",clean.toJSONString(),uid,channel,revision)==1,"购物车已在其他页面更新，请刷新后重试");
  return cart(uid,channel);
 }
 public Map<String,Object> removeOrdered(long uid,String channel,JSONArray ordered,long revision){
  Map<String,Object> cart=cart(uid,channel);JSONArray items=(JSONArray)cart.get("items");
  check(number(cart.get("revision"))==revision,"购物车已更新，请返回购物车重新确认商品");
  Map<Long,Long> quantities=new HashMap<>();for(Object obj:ordered){JSONObject row=JSON.parseObject(JSON.toJSONString(obj));quantities.put(row.getLongValue("id"),row.getLongValue("quantity"));}
  JSONArray remaining=new JSONArray();
  for(Object obj:items){JSONObject row=JSON.parseObject(JSON.toJSONString(obj));Long quantity=quantities.remove(row.getLongValue("id"));
   if(quantity!=null){check(row.getBooleanValue("checked")&&row.getLongValue("quantity")>=quantity,"购物车商品已更新，请返回购物车重新选择");row.put("quantity",row.getLongValue("quantity")-quantity);}
   if(row.getLongValue("quantity")>0)remaining.add(row);
  }
  check(quantities.isEmpty(),"购物车商品已移除，请返回购物车重新选择");
  check(s.jdbc().update("update shop_cart set items_json=?,revision=revision+1 where member_id=? and channel=? and revision=?",remaining.toJSONString(),uid,channel,revision)==1,"购物车已更新，请返回购物车重新确认商品");
  return cart(uid,channel);
 }
 public List<Map<String,Object>> campaigns(boolean admin){
  if(!admin)return s.jdbc().queryForList("select * from shop_coupon_campaign where archived=0 and terminated_at is null and enabled=1 and starts_at<=now() and ends_at>now() and claimed<stock order by id desc");
  List<Map<String,Object>> rows=s.jdbc().queryForList("select c.*,coalesce(q.issued_count,0) issued_count,coalesce(q.live_count,0) live_count,coalesce(q.reserved_count,0) reserved_count,coalesce(q.terminated_count,0) terminated_count from shop_coupon_campaign c left join (select campaign_id,count(*) issued_count,sum(case when "+LIVE_COUPON+" then 1 else 0 end) live_count,sum(case when state='reserved' then 1 else 0 end) reserved_count,sum(case when terminated_at is not null then 1 else 0 end) terminated_count from shop_coupon group by campaign_id) q on q.campaign_id=c.id where c.archived=0 order by c.id desc");
  for(Map<String,Object> row:rows)deletionEligibility(row);
  return rows;
 }
 // Used coupons also block deletion until expired/terminated: a full refund may return them.
 private static final String LIVE_COUPON="terminated_at is null and (expires_at>now() or expires_at is null or state not in ('available','reserved','used','expired'))";
 private boolean enabled(Map<String,Object> c){return Boolean.TRUE.equals(c.get("enabled"))||"1".equals(str(c.get("enabled")));}
 private void deletionEligibility(Map<String,Object> c){
  String reason=enabled(c)?"请先暂停领取":number(c.get("reserved_count"))>0?"有优惠券被待付款订单占用，请先等待订单付款或关闭":number(c.get("live_count"))>0?"还有 "+c.get("live_count")+" 张未过期且未终止的已领券":"";
  c.put("delete_allowed",reason.isEmpty());c.put("delete_reason",reason);
  String terminateReason=enabled(c)?"请先暂停领取":number(c.get("reserved_count"))>0?"有优惠券被待付款订单占用，暂不能终止":number(c.get("issued_count"))==number(c.get("terminated_count"))?"没有需要终止的已领券":"";
  c.put("terminate_allowed",terminateReason.isEmpty());c.put("terminate_reason",terminateReason);
 }
 private Map<String,Object> lockedCampaign(long id){
  Map<String,Object> c=s.one("select * from shop_coupon_campaign where id=? for update",id);
  check(number(c.get("archived"))==0,"该优惠券已删除，请刷新列表");
  // Claim/toggle serialize on the campaign; redemption/refund serialize on these coupon rows.
  final long[] counts=new long[4];
  s.jdbc().query("select state,terminated_at,case when "+LIVE_COUPON+" then 1 else 0 end live from shop_coupon where campaign_id=? order by id for update",rs->{counts[0]++;counts[1]+=rs.getLong("live");if("reserved".equals(rs.getString("state")))counts[2]++;if(rs.getObject("terminated_at")!=null)counts[3]++;},id);
  c.put("issued_count",counts[0]);c.put("live_count",counts[1]);c.put("reserved_count",counts[2]);c.put("terminated_count",counts[3]);deletionEligibility(c);return c;
 }
 @Transactional public void deleteCampaign(long id,String who){
  Map<String,Object> c=lockedCampaign(id);check(Boolean.TRUE.equals(c.get("delete_allowed")),str(c.get("delete_reason")));
  s.jdbc().update("update shop_coupon_campaign set archived=1 where id=?",id);
  s.event("couponCampaign",id,"删除优惠券活动（保留历史领券及订单记录）",who);
 }
 @Transactional public void terminateCampaign(long id,String who){
  Map<String,Object> c=lockedCampaign(id);check(Boolean.TRUE.equals(c.get("terminate_allowed")),str(c.get("terminate_reason")));
  s.jdbc().update("update shop_coupon set terminated_at=now() where campaign_id=? and terminated_at is null",id);
  s.jdbc().update("update shop_coupon_campaign set terminated_at=now() where id=?",id);
  s.event("couponCampaign",id,"终止全部已领券，禁止继续使用及退款返还",who);
 }
 public List<Map<String,Object>> coupons(long uid){
  return s.jdbc().queryForList("select *,case when terminated_at is not null then 'terminated' when state='available' and expires_at<=now() then 'expired' else state end display_state from shop_coupon where member_id=? order by id desc",uid);
 }
 @Transactional public Map<String,Object> claim(long uid,long id,JSONObject body){
  s.one("select id from shop_member where id=? for update",uid);
  String key=str(body.get("requestKey"));check(key.matches("[a-zA-Z0-9_-]{8,80}"),"请重新点击领取");
  List<Map<String,Object>> prior=s.jdbc().queryForList("select * from shop_coupon where member_id=? and claim_key=?",uid,key);
  if(!prior.isEmpty()){check(number(prior.get(0).get("campaign_id"))==id,"领取信息已改变，请重试");return prior.get(0);}
  Map<String,Object> c=s.one("select *, (archived=0 and terminated_at is null and enabled=1 and starts_at<=now() and ends_at>now()) usable from shop_coupon_campaign where id=? for update",id);
  check(number(c.get("usable"))==1,"活动尚未开始或已结束");check(number(c.get("claimed"))<number(c.get("stock")),"优惠券已领完");
  check(s.jdbc().queryForObject("select count(*) from shop_coupon where campaign_id=? and member_id=?",Long.class,id,uid)<number(c.get("claim_limit")),"你已领取过本活动可领取的优惠券");
  if("new".equals(c.get("audience")))check(s.jdbc().queryForObject("select count(*) from shop_order where member_id=? and paid_at is not null and status<>'refunded'",Long.class,uid)==0,"此券仅限尚未完成首单的新客户领取");
  s.jdbc().update("insert into shop_coupon(campaign_id,member_id,name,threshold_amount,discount_amount,product_ids,expires_at,claim_key,return_on_refund) values(?,?,?,?,?,?,date_add(now(),interval ? day),?,?)",id,uid,c.get("name"),c.get("threshold_amount"),c.get("discount_amount"),c.get("product_ids"),c.get("valid_days"),key,c.get("return_on_refund"));
  s.jdbc().update("update shop_coupon_campaign set claimed=claimed+1 where id=?",id);
  s.event("coupon",id,"领取优惠券",str(uid));return s.one("select * from shop_coupon where member_id=? and claim_key=?",uid,key);
 }
 public Map<String,Object> quotedCoupon(long uid,long id,String channel,List<JSONObject> lines,long subtotal,long promotion,boolean lock){
  check("C".equals(channel),"当前优惠券仅用于零售订单");
  Map<String,Object> c=s.one("select *,expires_at>now() valid from shop_coupon where id=? and member_id=?"+(lock?" for update":""),id,uid);
  check(c.get("terminated_at")==null&&"available".equals(c.get("state"))&&number(c.get("valid"))==1,"优惠券已使用、被其他订单占用、已终止或已过期，请重新选择");
  JSONArray scope=JSON.parseArray(str(c.get("product_ids")));long eligible=0,cumulative=0;
  for(JSONObject line:lines){long amount=line.getLongValue("unitPrice")*line.getLongValue("quantity");long before=cumulative;cumulative+=amount;
   long net=line.getLongValue("netAmount");
   boolean applies=scope.isEmpty();for(Object p:scope)if(number(p)==line.getLongValue("id"))applies=true;if(applies)eligible+=net;
  }
  check(eligible>=number(c.get("threshold_amount"))&&eligible>0,"所选商品未达到这张优惠券的使用门槛");
  
 long discount=Math.min(eligible,number(c.get("discount_amount"))),used=0;
 for(JSONObject line:lines){boolean applies=scope.isEmpty();for(Object p:scope)if(number(p)==line.getLongValue("id"))applies=true;
 if(applies){long net=line.getLongValue("netAmount"),before=used;used+=net;line.put("netAmount",net-(portion(used,discount,eligible)-portion(before,discount,eligible)));}}
 c.put("applied_discount",discount);return c;
 }
 static long portion(long a,long b,long total){return total==0?0:java.math.BigInteger.valueOf(a).multiply(java.math.BigInteger.valueOf(b)).divide(java.math.BigInteger.valueOf(total)).longValueExact();}
 public void reserve(long uid,long oid,Map<String,Object> coupon){
  check(s.jdbc().update("update shop_coupon set state='reserved',order_id=? where id=? and member_id=? and state='available' and expires_at>now() and terminated_at is null",oid,coupon.get("id"),uid)==1,"优惠券已不可用，请重新结算");
  s.jdbc().update("insert into shop_order_coupon(order_id,coupon_id,discount_amount,snapshot_json) values(?,?,?,?)",oid,coupon.get("id"),coupon.get("applied_discount"),JSON.toJSONString(coupon));
 }
 public void consume(long oid){s.jdbc().update("update shop_coupon set state='used' where order_id=? and state='reserved'",oid);}
 public void release(long oid){s.jdbc().update("update shop_coupon set state=case when expires_at>now() then 'available' else 'expired' end,order_id=null where order_id=? and state='reserved'",oid);}
 public void refund(long oid){
  Map<String,Object> o=s.one("select amount,refunded_amount,status from shop_order where id=?",oid);
  if(!"refunded".equals(o.get("status")))return;
  s.jdbc().update("update shop_coupon set state=case when expires_at>now() then 'available' else 'expired' end,order_id=null where order_id=? and state='used' and return_on_refund=1 and terminated_at is null",oid);
  s.jdbc().update("update shop_order_coupon set refunded=1 where order_id=?",oid);
 }
 @Transactional public void saveCampaign(JSONObject b,String who){
  long id=b.getLongValue("id");
  if(id>0){Map<String,Object> c=s.one("select archived,terminated_at from shop_coupon_campaign where id=? for update",id);check(number(c.get("archived"))==0,"该优惠券已删除，请刷新列表");check(!b.getBooleanValue("enabled")||c.get("terminated_at")==null,"已终止的优惠券不能重新开放领取，请新建活动");s.jdbc().update("update shop_coupon_campaign set enabled=? where id=?",b.getBooleanValue("enabled"),id);s.event("couponCampaign",id,b.getBooleanValue("enabled")?"开放领取":"暂停领取",who);return;}
  String name=str(b.get("name")).trim();check(name.length()>0&&name.length()<=80,"请填写80字以内的优惠券名称");
  long threshold=integer(b.get("threshold"),0,100000000,"使用门槛不正确"),discount=integer(b.get("discount"),1,100000000,"优惠金额必须大于0");
  check(threshold==0||discount<=threshold,"优惠金额不能超过使用门槛");
  long stock=integer(b.get("stock"),1,1000000,"发放数量需1至100万张"),limit=integer(b.getOrDefault("claimLimit",1),1,100,"每人限领需1至100张"),days=integer(b.getOrDefault("validDays",30),1,3650,"有效期需1至3650天");
  Timestamp start=parseTime(b.get("startsAt")),end=parseTime(b.get("endsAt"));check(end.after(start),"结束时间应晚于开始时间");
  String audience=str(b.getOrDefault("audience","all"));check(Arrays.asList("all","new").contains(audience),"请选择全部客户或新客户");
  JSONArray scope=b.getJSONArray("productIds");if(scope==null)scope=new JSONArray();check(scope.size()<=100,"指定商品最多100种");for(Object pid:scope)s.one("select id from shop_product where id=?",integer(pid,1,Long.MAX_VALUE,"指定商品不正确"));
  s.jdbc().update("insert into shop_coupon_campaign(name,threshold_amount,discount_amount,stock,claim_limit,valid_days,starts_at,ends_at,product_ids,audience,enabled,return_on_refund) values(?,?,?,?,?,?,?,?,?,?,?,?)",name,threshold,discount,stock,limit,days,start,end,scope.toJSONString(),audience,b.getBooleanValue("enabled"),!b.containsKey("returnOnRefund")||b.getBooleanValue("returnOnRefund"));
  s.event("couponCampaign",0,"创建优惠券活动",who);
 }
 private Timestamp parseTime(Object value){try{return Timestamp.valueOf(str(value).replace('T',' '));}catch(Exception ex){throw new IllegalArgumentException("请填写完整的活动开始和结束时间");}}
}
