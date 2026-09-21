package com.ruoyi.web.controller.shop;

import com.alibaba.fastjson2.JSONObject;
import java.util.*;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ruoyi.web.controller.shop.ShopService.*;

@Service
public class ShopWechatPay {
 @Autowired @Lazy ShopService s;
 @Autowired ShopWechatPayGateway gateway;
 @Autowired @Lazy ShopWorkflow workflow;
 @Autowired TransactionTemplate tx;

 public boolean ready(){return gateway.ready()&&!s.isDemo();}
 boolean started(long oid){return s.jdbc().queryForObject("select count(*) from shop_wechat_payment where order_id=?",Long.class,oid)>0;}
 public boolean paid(long oid){return s.jdbc().queryForObject("select count(*) from shop_payment where order_id=? and mode='wechat'",Long.class,oid)>0;}
 Map<String,Object> lock(long oid){
  Map<String,Object> first=s.one("select member_id from shop_order where id=?",oid);
  s.one("select id from shop_member where id=? for update",first.get("member_id"));
  return s.one("select * from shop_order where id=? for update",oid);
 }
 private String openid(long uid){
  String prefix="wechat:"+gateway.appId+":";
  List<Map<String,Object>> identities=s.jdbc().queryForList("select identity_key from shop_identity where member_id=? and identity_key like ?",uid,prefix+"%");
  check(identities.size()==1,"请使用已绑定的微信登录后付款");return str(identities.get(0).get("identity_key")).substring(prefix.length());
 }
 public JSONObject prepay(long actor,long oid){
  check(ready(),"微信支付尚未开放");
  // Commit the intent before the network request. An uncertain response must never
  // erase evidence that WeChat may already have accepted this merchant order.
  tx.execute(status->{Map<String,Object> o=lock(oid);s.authorizeOrder(actor,o,"pay");
   check("pending".equals(o.get("status"))&&o.get("paid_at")==null,"订单已支付或已关闭，请刷新订单");
   check(!s.bankStarted(oid)&&!s.creditOrder(oid),"订单已选择其他付款方式");
   s.validateWechatPayment(o);
   for(Map<String,Object> group:s.jdbc().queryForList("select g.status,g.expires_at from shop_campaign_order c join shop_campaign_group g on g.id=c.group_id where c.order_id=?",oid))
    check("open".equals(group.get("status"))&&timestamp(group.get("expires_at")).getTime()>System.currentTimeMillis()+65000,"该拼团已结束或即将到期，请重新选购");
   check(!Boolean.TRUE.equals(o.get("is_test"))&&!"1".equals(str(o.get("is_test"))),"测试订单不能发起真实扣款");
   check(timestamp(o.get("expires_at")).getTime()>System.currentTimeMillis()+65000,"订单即将超时，请重新下单");
   Map<String,Object> funds=s.one("select * from shop_order_funds where order_id=? for update",oid);
   long amount=number(funds.get("external_amount"));check("reserved".equals(funds.get("state"))&&amount>0&&amount<=Integer.MAX_VALUE,"订单无需微信付款或金额不正确");
   String payer=openid(actor);
   s.jdbc().update("insert ignore into shop_wechat_payment(order_id,out_trade_no,payer_openid,amount) values(?,?,?,?)",oid,o.get("order_no"),payer,amount);
   Map<String,Object> p=s.one("select * from shop_wechat_payment where order_id=? for update",oid);
   check(payer.equals(p.get("payer_openid")),"请由首次发起支付的微信继续付款，或取消后重新下单");
   check(amount==number(p.get("amount"))&&!"CLOSED".equals(p.get("state")),"支付单已关闭或金额发生变化，请重新下单");return null;});
  return tx.execute(status->{Map<String,Object> o=lock(oid);s.authorizeOrder(actor,o,"pay");
   check("pending".equals(o.get("status"))&&!s.bankStarted(oid),"订单已支付、关闭或更换付款方式");
   Map<String,Object> p=s.one("select * from shop_wechat_payment where order_id=? for update",oid);
   check(!"CLOSED".equals(p.get("state")),"支付单已关闭，请重新下单");
   check(timestamp(o.get("expires_at")).getTime()>System.currentTimeMillis()+65000,"订单即将超时，请重新下单");
   String prepay=str(p.get("prepay_id"));
   if(prepay.isEmpty()){
    JSONObject body=new JSONObject();body.put("appid",gateway.appId);body.put("mchid",gateway.merchantId);body.put("description","美容商城订单 "+o.get("order_no"));body.put("out_trade_no",p.get("out_trade_no"));body.put("notify_url",gateway.notifyUrl);
    body.put("time_expire",timestamp(o.get("expires_at")).toInstant().atOffset(ZoneOffset.ofHours(8)).toString());
    JSONObject amount=new JSONObject();amount.put("total",p.get("amount"));amount.put("currency","CNY");body.put("amount",amount);
    JSONObject payer=new JSONObject();payer.put("openid",p.get("payer_openid"));body.put("payer",payer);
    prepay=gateway.call("POST","/v3/pay/transactions/jsapi",body).getString("prepay_id");
    check(prepay!=null&&prepay.matches("[a-zA-Z0-9_-]{1,128}"),"微信预支付单返回异常");
    s.jdbc().update("update shop_wechat_payment set state='READY',prepay_id=? where order_id=?",prepay,oid);
   }
   return gateway.paymentParameters(prepay);
  });
 }
 public JSONObject status(long actor,long oid){return tx.execute(status->{Map<String,Object> o=lock(oid);s.authorizeOrder(actor,o,"view");
  if(started(oid)&&"pending".equals(o.get("status"))){JSONObject result=query(oid);if(result!=null&&"SUCCESS".equals(result.getString("trade_state")))settle(o,result);}
  Map<String,Object> current=s.one("select status,paid_at from shop_order where id=?",oid);JSONObject out=new JSONObject();out.put("paid",current.get("paid_at")!=null);out.put("status",current.get("status"));return out;
 });}
 JSONObject query(long oid){Map<String,Object> p=s.one("select * from shop_wechat_payment where order_id=?",oid);try{return gateway.query(str(p.get("out_trade_no")));}catch(ShopWechatPayGateway.ApiException e){if("ORDER_NOT_EXIST".equals(e.code))return null;throw e;}}
 public void notification(JSONObject transaction){
  String no=transaction.getString("out_trade_no");check(no!=null&&no.matches("[a-zA-Z0-9_-]{6,32}"),"支付单号不正确");
  Map<String,Object> p=s.one("select order_id from shop_wechat_payment where out_trade_no=?",no);
  tx.execute(status->{settle(lock(number(p.get("order_id"))),transaction);return null;});
 }
 void settle(Map<String,Object> o,JSONObject transaction){
  long oid=number(o.get("id"));Map<String,Object> p=s.one("select * from shop_wechat_payment where order_id=? for update",oid);
  gateway.validateTransaction(transaction,p);
  if("SUCCESS".equals(p.get("state"))){check(str(p.get("transaction_id")).equals(transaction.getString("transaction_id"))&&paid(oid),"支付交易重复或状态异常");return;}
  check("pending".equals(o.get("status"))&&!s.bankStarted(oid)&&!s.creditOrder(oid),"订单状态与微信付款不一致，请联系财务核实");
  Map<String,Object> funds=s.one("select * from shop_order_funds where order_id=? for update",oid);
  check("reserved".equals(funds.get("state"))&&number(funds.get("external_amount"))==number(p.get("amount")),"订单付款组成不匹配");
  s.jdbc().update("update shop_wechat_payment set state='SUCCESS',transaction_id=? where order_id=?",transaction.getString("transaction_id"),oid);
  s.recordWechatPayment(o);s.event("order",oid,"微信支付验签确认到账","微信支付");
 }
 /** Caller holds member/order locks and owns the transaction. False means it was paid. */
 public boolean closeBeforeRelease(Map<String,Object> o){
  long oid=number(o.get("id"));if(!started(oid))return true;
  Map<String,Object> p=s.one("select * from shop_wechat_payment where order_id=? for update",oid);
  if("CLOSED".equals(p.get("state")))return true;
  JSONObject result=query(oid);
  if(result!=null&&"SUCCESS".equals(result.getString("trade_state"))){settle(o,result);return false;}
  // ORDER_NOT_EXIST is not sufficient after a timed-out prepay request. Preserve
  // the reservation until a signed close succeeds or a signed CLOSED is received.
  if(result==null)throw new IllegalArgumentException("微信支付单结果待核实，暂不能释放库存，请稍后重试或联系商家");
  if(!Arrays.asList("CLOSED","REVOKED","PAYERROR").contains(result.getString("trade_state"))){
   JSONObject body=new JSONObject();body.put("mchid",gateway.merchantId);
   try{gateway.call("POST","/v3/pay/transactions/out-trade-no/"+p.get("out_trade_no")+"/close",body);}
   catch(ShopWechatPayGateway.ApiException e){if(!"ORDERPAID".equals(e.code))throw e;settle(o,gateway.query(str(p.get("out_trade_no"))));return false;}
  }
  s.jdbc().update("update shop_wechat_payment set state='CLOSED' where order_id=?",oid);return true;
 }
 public boolean queueRefund(Map<String,Object> c,Map<String,Object> o){
  long oid=number(o.get("id")),cid=number(c.get("id"));if(!paid(oid))return false;
  Map<String,Object> f=s.one("select * from shop_order_funds where order_id=?",oid);
  long total=number(o.get("refunded_amount"))+number(c.get("amount"));check(total<=number(o.get("amount")),"退款不能超过实付款");
  long wallet=ShopCommerce.portion(total,number(f.get("wallet_amount")),number(o.get("amount")));
  long rebate=ShopCommerce.portion(total,s.rebateUsed(oid),number(o.get("amount")));
  long external=total-wallet-rebate-number(f.get("refunded_external"));
  check(external>=0&&external<=number(f.get("external_amount"))-number(f.get("refunded_external")),"微信退款金额不正确");
  s.jdbc().update("insert into shop_wechat_refund(case_id,order_id,out_refund_no,amount) values(?,?,?,?)",cid,oid,"WXRF"+oid+"C"+cid,external);return true;
 }
 public boolean refundConfirmed(long cid){return s.jdbc().queryForObject("select count(*) from shop_wechat_refund where case_id=? and state='SUCCESS'",Long.class,cid)==1;}
 /** Authenticated refund notifications settle the same ledger as the polling fallback. */
 public void refundNotification(JSONObject result){
  check(gateway.merchantId.equals(result.getString("mchid")),"退款商户不匹配");
  String no=result.getString("out_refund_no");check(no!=null&&no.matches("[a-zA-Z0-9_-]{6,64}"),"退款单号不正确");
  Map<String,Object> first=s.one("select case_id,order_id from shop_wechat_refund where out_refund_no=?",no);
  tx.execute(status->{long oid=number(first.get("order_id")),cid=number(first.get("case_id"));lock(oid);
   Map<String,Object> r=s.one("select * from shop_wechat_refund where case_id=? for update",cid),p=s.one("select * from shop_wechat_payment where order_id=?",oid);
   check(str(p.get("out_trade_no")).equals(result.getString("out_trade_no")),"退款订单不匹配");
   applyRefundResult(cid,r,p,result,true);return null;
  });
 }
 void applyRefundResult(long cid,Map<String,Object> r,Map<String,Object> p,JSONObject result,boolean notification){
  check(str(r.get("out_refund_no")).equals(result.getString("out_refund_no"))&&str(p.get("transaction_id")).equals(result.getString("transaction_id")),"微信退款交易不匹配");
  JSONObject money=result.getJSONObject("amount");
  check(money!=null&&money.containsKey("refund")&&money.containsKey("total")&&money.getLongValue("refund")==number(r.get("amount"))&&money.getLongValue("total")==number(p.get("amount"))&&((notification&&!money.containsKey("currency"))||"CNY".equals(money.getString("currency"))),"微信退款金额不匹配");
  String state=result.getString(notification?"refund_status":"status"),refundId=result.getString("refund_id");
  check(Arrays.asList("SUCCESS","PROCESSING","CLOSED","ABNORMAL").contains(state)&&refundId!=null&&refundId.matches("[0-9]{20,64}"),"微信退款状态异常");
  check(str(r.get("refund_id")).isEmpty()||refundId.equals(r.get("refund_id")),"微信退款编号不匹配");
  if("SUCCESS".equals(r.get("state"))){workflow.finishWechatRefund(cid);return;}
  s.jdbc().update("update shop_wechat_refund set state=?,refund_id=? where case_id=?",state,refundId,cid);
  if("SUCCESS".equals(state)){
   workflow.finishWechatRefund(cid);
   String time=result.getString("success_time");
   if(time!=null&&!time.isEmpty())s.jdbc().update("update shop_finance_event set occurred_at=? where event_key=? and kind='refund'",java.sql.Timestamp.from(java.time.OffsetDateTime.parse(time).toInstant()),"case_"+cid);
  }
 }
 public void processRefund(long cid){tx.execute(status->{Map<String,Object> first=s.one("select order_id from shop_wechat_refund where case_id=?",cid);long oid=number(first.get("order_id"));lock(oid);
  Map<String,Object> r=s.one("select * from shop_wechat_refund where case_id=? for update",cid);
  if("SUCCESS".equals(r.get("state"))){workflow.finishWechatRefund(cid);return null;}
  Map<String,Object> p=s.one("select * from shop_wechat_payment where order_id=?",oid);
  long amount=number(r.get("amount"));
  if(amount>0){
   JSONObject result;
   if("QUEUED".equals(r.get("state"))){JSONObject body=new JSONObject();body.put("transaction_id",p.get("transaction_id"));body.put("out_refund_no",r.get("out_refund_no"));body.put("reason","商城售后退款");body.put("notify_url",gateway.refundNotifyUrl());JSONObject money=new JSONObject();money.put("refund",amount);money.put("total",p.get("amount"));money.put("currency","CNY");body.put("amount",money);result=gateway.call("POST","/v3/refund/domestic/refunds",body);}
   else result=gateway.call("GET","/v3/refund/domestic/refunds/"+r.get("out_refund_no"),null);
   applyRefundResult(cid,r,p,result,false);return null;
  }else s.jdbc().update("update shop_wechat_refund set state='SUCCESS' where case_id=?",cid);
  workflow.finishWechatRefund(cid);return null;
 });}
 public void reconcile(long oid){tx.execute(status->{Map<String,Object> o=lock(oid);if("pending".equals(o.get("status"))){JSONObject t=query(oid);if(t!=null&&"SUCCESS".equals(t.getString("trade_state")))settle(o,t);}return null;});}
}
