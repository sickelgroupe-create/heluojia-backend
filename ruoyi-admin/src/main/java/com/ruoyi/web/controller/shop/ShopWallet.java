package com.ruoyi.web.controller.shop;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSONObject;
import static com.ruoyi.web.controller.shop.ShopService.*;

@Service
public class ShopWallet {
 @Autowired @Lazy private ShopService s;
 private void lock(long uid){s.one("select id from shop_member where id=? for update",uid);}
 private Map<String,Object> lockedFunds(long oid){List<Map<String,Object>> owners=s.jdbc().queryForList("select member_id from shop_order_funds where order_id=?",oid);if(owners.isEmpty())return Collections.emptyMap();lock(number(owners.get(0).get("member_id")));return s.one("select * from shop_order_funds where order_id=? for update",oid);}
 // Provenance belongs to the wallet movement, not to a future payment on its order.
 // NULL denotes old data; never promote an unverified positive adjustment to real money.
 private String validOrder(String id){return "exists(select 1 from shop_order wo join shop_member wm on wm.id=wo.member_id where wo.id="+id+" and wo.is_test=0 and wm.is_test=0 and not exists(select 1 from shop_payment wp where wp.order_id=wo.id and wp.mode='simulation'))";}
 private String paidSource(String id){return validOrder(id)+" and exists(select 1 from shop_payment wp where wp.order_id="+id+" and wp.mode<>'simulation')";}
 private String legacyHold(String alias){return alias+".kind='hold' and "+alias+".delta<0 and "+validOrder(alias+".order_id");}
 private String formalLedger(String alias){return "exists(select 1 from shop_member wallet_owner where wallet_owner.id="+alias+".member_id and wallet_owner.is_test=0) and ("+alias+".order_id is null or "+validOrder(alias+".order_id")+") and ("+alias+".verified=1 or ("+alias+".verified is null and (("+paidSource(alias+".order_id")+") or ("+legacyHold(alias)+") or ("+alias+".kind='release' and exists(select 1 from shop_wallet_ledger wh where wh.member_id="+alias+".member_id and wh.order_id="+alias+".order_id and wh.event_key=concat('hold_',"+alias+".order_id) and wh.verified is null and wh.delta=-"+alias+".delta and "+legacyHold("wh")+")))))";}
 private boolean formal(){return !s.isDemo();}
 public long available(long uid){return s.jdbc().queryForObject("select coalesce(sum(l.delta),0) from shop_wallet_ledger l where l.member_id=?"+(formal()?" and "+formalLedger("l"):""),Long.class,uid);}
 public Map<String,Object> account(long uid){
  Map<String,Object> result=new HashMap<>();result.put("available",available(uid));
  result.put("reserved",s.jdbc().queryForObject("select coalesce(sum(f.wallet_amount),0) from shop_order_funds f where f.member_id=? and f.state='reserved'"+(formal()?" and exists(select 1 from shop_wallet_ledger l where l.member_id=f.member_id and l.order_id=f.order_id and l.event_key=concat('hold_',f.order_id) and l.delta=-f.wallet_amount and "+formalLedger("l")+")":""),Long.class,uid));
  result.put("demo",s.isDemo());result.put("ledger",s.jdbc().queryForList("select kind,delta,order_id,note,created_at from shop_wallet_ledger l where member_id=?"+(formal()?" and l.delta<>0 and "+formalLedger("l"):"")+" order by id desc limit 200",uid));return result;
 }
 @Transactional public void recharge(long uid,JSONObject b){
  check(s.isDemo(),"真实充值暂未开放");lock(uid);
  long amount=integer(b.get("amount"),1,1000000,"非实收入账金额需0.01至10000元");String key=str(b.get("requestKey"));check(key.matches("[a-zA-Z0-9_-]{8,80}"),"请重新提交非实收入账");
  List<Map<String,Object>> prior=s.jdbc().queryForList("select delta from shop_wallet_ledger where member_id=? and event_key=?",uid,"recharge_"+key);
  if(!prior.isEmpty()){check(number(prior.get(0).get("delta"))==amount,"充值内容已变更，请重新提交");return;}
  check(available(uid)+amount<=100000000,"非实收余额已达到调试上限");
  entry(uid,"recharge_"+key,amount,"recharge",null,"非实收入账，不发生真实扣款");
 }
 public void entry(long uid,String key,long delta,String kind,Long oid,String note){boolean verified=formal()&&oid!=null&&s.jdbc().queryForObject("select count(*) from shop_order where id=? and "+paidSource("shop_order.id"),Long.class,oid)==1;entry(uid,key,delta,kind,oid,note,verified);}
 private void entry(long uid,String key,long delta,String kind,Long oid,String note,boolean verified){s.jdbc().update("insert into shop_wallet_ledger(member_id,event_key,delta,kind,order_id,note,verified) values(?,?,?,?,?,?,?)",uid,key,delta,kind,oid,note,verified);}
 private boolean verifiedHold(Map<String,Object> f){return s.jdbc().queryForObject("select count(*) from shop_wallet_ledger l where l.member_id=? and l.order_id=? and l.event_key=? and l.kind='hold' and l.delta=? and "+formalLedger("l"),Long.class,f.get("member_id"),f.get("order_id"),"hold_"+f.get("order_id"),-number(f.get("wallet_amount")))==1;}
 public boolean canPay(Map<String,Object> order){if(!formal())return false;Map<String,Object> f=lockedFunds(number(order.get("id")));if(f.isEmpty())return false;return Arrays.asList("reserved","paid").contains(str(f.get("state")))&&number(f.get("member_id"))==number(order.get("member_id"))&&number(f.get("wallet_amount"))>0&&number(f.get("wallet_amount"))==number(order.get("amount"))&&number(f.get("external_amount"))==0&&verifiedHold(f)&&available(number(f.get("member_id")))>=0;}
 public boolean paidByWallet(long oid){return s.jdbc().queryForObject("select count(*) from shop_payment where order_id=? and mode='wallet'",Long.class,oid)==1;}
 public void freeze(long uid,long oid,long amount,long total){
  lock(uid);if(formal()&&amount>0)check(s.jdbc().queryForObject("select count(*) from shop_order where id=? and member_id=? and "+validOrder("shop_order.id"),Long.class,oid,uid)==1,"该订单不允许占用正式余额，请重新结算");check(amount>=0&&amount<=total&&amount<=available(uid),"余额已变化，请重新结算");
  s.jdbc().update("insert into shop_order_funds(order_id,member_id,wallet_amount,external_amount,wallet_verified) values(?,?,?,?,?)",oid,uid,amount,total-amount,formal());
  if(amount>0)entry(uid,"hold_"+oid,-amount,"hold",oid,"下单暂时占用余额，取消后返还",formal());
 }
 public void paid(long uid,long oid){
  Map<String,Object> f=lockedFunds(oid);if(f.isEmpty()||!"reserved".equals(f.get("state")))return;
  s.jdbc().update("update shop_order_funds set state='paid' where order_id=?",oid);
  if(number(f.get("wallet_amount"))>0)entry(number(f.get("member_id")),"pay_"+oid,0,"payment",oid,"余额支付已完成（下单时已占用，不重复扣款）",verifiedHold(f));
 }
 public void release(long oid){
  Map<String,Object> f=lockedFunds(oid);if(f.isEmpty())return;long uid=number(f.get("member_id"));if(!"reserved".equals(f.get("state")))return;
  s.jdbc().update("update shop_order_funds set state='cancelled' where order_id=?",oid);
  if(number(f.get("wallet_amount"))>0)entry(uid,"release_"+oid,number(f.get("wallet_amount")),"release",oid,"订单取消，返还占用余额",verifiedHold(f));
 }
 public void refund(long oid){
  Map<String,Object> f=lockedFunds(oid);if(f.isEmpty())return;long uid=number(f.get("member_id"));check("paid".equals(f.get("state")),"付款状态不允许退款");
  Map<String,Object> o=s.one("select amount,refunded_amount from shop_order where id=?",oid);
  long total=number(o.get("refunded_amount")),wallet=ShopCommerce.portion(total,number(f.get("wallet_amount")),number(o.get("amount"))),delta=wallet-number(f.get("refunded_wallet"));
  check(delta>=0&&wallet<=number(f.get("wallet_amount")),"余额退款金额不正确");
  if(delta>0)entry(uid,"refund_"+oid+"_"+total,delta,"refund",oid,"按原支付组成退回余额",verifiedHold(f));
  s.jdbc().update("update shop_order_funds set refunded_wallet=?,refunded_external=? where order_id=?",wallet,total-wallet-s.rebateReturned(oid),oid);
 }
 public List<Map<String,Object>> adminLedger(){return s.jdbc().queryForList("select l.*,m.nickname"+ShopQueryFields.fields("m.id")+" from shop_wallet_ledger l join shop_member m on m.id=l.member_id where l.delta<>0 and m.is_test=0"+(formal()?" and "+formalLedger("l"):"")+" order by l.id desc");}
}
