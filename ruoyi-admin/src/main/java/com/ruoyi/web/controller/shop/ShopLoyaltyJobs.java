package com.ruoyi.web.controller.shop;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class ShopLoyaltyJobs {
 @Autowired ShopWorkflow workflow;@Autowired ShopService s; @Autowired ShopPoints points;
 @Scheduled(fixedDelay=60000,initialDelay=15000) public void settlePoints(){
 for(Map<String,Object> row:s.jdbc().queryForList("select p.id,p.order_id from shop_shipment p join shop_order o on o.id=p.order_id where p.received=0 and JSON_EXTRACT(o.rules_json,'$.loyalty.autoReceive')=true and p.created_at<date_sub(now(),interval 1 day) limit 100"))try{workflow.autoReceive(ShopService.number(row.get("order_id")),ShopService.number(row.get("id")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Auto receipt pending for shipment {}",row.get("id"));}
  for(Map<String,Object> row:s.jdbc().queryForList("select order_id from shop_order_points where settled=0 and state='paid' and ready_at<=now() limit 100"))try{points.settle(ShopService.number(row.get("order_id")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Point settlement failed for order {}",row.get("order_id"));}
  for(Map<String,Object> row:s.jdbc().queryForList("select distinct order_id from shop_commission where status='pending' and ready_at<=now() limit 100"))try{s.settleCommission(ShopService.number(row.get("order_id")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Commission settlement failed for order {}",row.get("order_id"));}
  for(Map<String,Object> row:s.jdbc().queryForList("select distinct member_id from shop_point_lot where remaining>0 and expires_at<=now() limit 100"))try{points.expire(ShopService.number(row.get("member_id")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Point expiration failed for member {}",row.get("member_id"));}
 }
}
