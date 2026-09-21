package com.ruoyi.web.controller.shop;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ruoyi.web.controller.shop.ShopService.*;

/** Customer-visible history from recorded business facts, never fabricated status times. */
@Service
public class ShopOrderProgress {
 @Autowired ShopService s;

 @Transactional(readOnly=true)
 public List<Map<String,Object>> read(long uid,long oid){
  // Authorize before querying any history, including enterprise orders.
  Map<String,Object> order=s.companyOrder(uid,oid,"view",false);
  List<Map<String,Object>> events=new ArrayList<>();
  add(events,"created","订单已提交",order.get("created_at"),"等待完成付款");
  add(events,"paid","付款已确认",order.get("paid_at"),"订单已进入后续处理");
  for(Map<String,Object> p:s.jdbc().queryForList("select id,carrier,tracking,created_at from shop_shipment where order_id=? order by id",oid)){
   boolean pickup="门店自提".equals(p.get("carrier"));
   add(events,"shipment_"+p.get("id"),pickup?"门店自提已核销":"商品已发出",p.get("created_at"),pickup?"门店已确认交付":str(p.get("carrier"))+" · "+str(p.get("tracking")));
  }
  for(Map<String,Object> c:s.jdbc().queryForList("select id,type,created_at from shop_case where order_id=? order by id",oid)){
   String type=str(c.get("type"));
   add(events,"case_"+c.get("id"),"售后申请已提交",c.get("created_at"),"return".equals(type)?"退货退款":"exchange".equals(type)?"换货":"resend".equals(type)?"补发":"退款");
  }
  // Do not expose operator accounts or raw internal audit descriptions to customers.
  List<Map<String,Object>> rows=s.jdbc().queryForList("select e.id,e.object_type,e.action,e.created_at from shop_event e where (e.object_type='order' and e.object_id=?) or (e.object_type='case' and e.object_id in (select id from shop_case where order_id=?)) order by e.created_at,e.id",oid,oid);
  for(Map<String,Object> row:rows){
   String title=eventTitle(str(row.get("object_type")),str(row.get("action")));
   if(title!=null)add(events,"event_"+row.get("id"),title,row.get("created_at"),"");
  }
  for(Map<String,Object> r:s.jdbc().queryForList("select case_id,state,created_at,updated_at from shop_wechat_refund where order_id=? order by created_at",oid)){
   add(events,"refund_pending_"+r.get("case_id"),"已提交退款处理",r.get("created_at"),"等待微信支付确认退款结果");
   if(Arrays.asList("ABNORMAL","CLOSED").contains(r.get("state")))add(events,"refund_attention_"+r.get("case_id"),"退款需要商家核实",r.get("updated_at"),"请通过售后进度联系商家处理");
  }
  for(Map<String,Object> r:s.jdbc().queryForList("select event_key,amount,occurred_at,(select r.amount from shop_wechat_refund r where concat('case_',r.case_id)=f.event_key and r.state='SUCCESS' limit 1) as wechat_amount from shop_finance_event f where kind='refund' and object_id=? order by occurred_at,event_key",oid)){
   long total=number(r.get("amount")),wechat=number(r.get("wechat_amount"));
   String detail=wechat>0?"微信已原路退回 ¥"+BigDecimal.valueOf(wechat,2).toPlainString()+(total>wechat?"；其余 ¥"+BigDecimal.valueOf(total-wechat,2).toPlainString()+" 已返还对应抵扣账户":"，可核对微信支付到账通知"):"已处理 ¥"+BigDecimal.valueOf(total,2).toPlainString()+"，请核对原支付渠道或抵扣账户";
   add(events,"refund_"+r.get("event_key"),wechat>0?"微信退款成功":"退款处理完成",r.get("occurred_at"),detail);
  }
  if("cancelled".equals(order.get("status"))&&!events.stream().anyMatch(e->str(e.get("title")).contains("关闭")||"订单已取消".equals(e.get("title")))){
   Map<String,Object> missing=new LinkedHashMap<>();missing.put("id","cancelled_legacy");missing.put("title","订单已取消");missing.put("time",null);missing.put("detail","历史记录未保留取消时间");events.add(missing);
  }
  events.sort(Comparator.comparingLong(e->e.get("time")==null?Long.MAX_VALUE:((Number)e.get("time")).longValue()));
  return events;
 }
 private static void add(List<Map<String,Object>> events,String id,String title,Object time,String detail){
  if(time==null)return;
  long instant=time instanceof Date?((Date)time).getTime():(time instanceof java.time.LocalDateTime?(java.time.LocalDateTime)time:java.sql.Timestamp.valueOf(str(time).replace('T',' ')).toLocalDateTime()).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
  Map<String,Object> event=new LinkedHashMap<>();event.put("id",id);event.put("title",title);event.put("time",instant);event.put("detail",detail);events.add(event);
 }
 static String eventTitle(String type,String action){
  if("order".equals(type)){
   switch(action){
    case "cancel":return "订单已取消";
    case "超时关闭并释放库存":return "超时未付款，订单已关闭";
    case "receive":return "已确认收货";
    case "确认批次收货":return "已确认本批次收货";
    case "按原订单设置自动确认该批次收货":return "本批次已自动确认收货";
    case "自提商品备货完成":return "备货完成，等待到店自提";
    default:return null;
   }
  }
  if("case".equals(type)){
   switch(action){
    case "approve":return "售后审核通过";
    case "reject":return "售后申请已驳回";
    case "extend":return "寄回期限已延长";
    case "received":return "商家已收到退回商品";
    case "inspect":return "退回商品验收完成";
    case "reship":return "换货或补发商品已发出";
    case "确认换补发收货":return "换货或补发商品已确认收货";
    default:return null;
   }
  }
  return null;
 }
}
