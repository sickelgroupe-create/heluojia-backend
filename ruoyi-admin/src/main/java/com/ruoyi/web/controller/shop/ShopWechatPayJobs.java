package com.ruoyi.web.controller.shop;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import static com.ruoyi.web.controller.shop.ShopService.*;
@Component
public class ShopWechatPayJobs {
 @Autowired ShopService s;
 @Autowired ShopWechatPay payment;
 @Scheduled(fixedDelay=60000,initialDelay=30000) public void reconcile(){
  if(!payment.ready())return;
  for(Map<String,Object> p:s.jdbc().queryForList("select p.order_id from shop_wechat_payment p join shop_order o on o.id=p.order_id where o.status='pending' and p.state in ('CREATING','READY') order by p.updated_at limit 50"))try{payment.reconcile(number(p.get("order_id")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("WeChat payment reconciliation pending for order {}",p.get("order_id"));}
  for(Map<String,Object> r:s.jdbc().queryForList("select case_id from shop_wechat_refund where state in ('QUEUED','PROCESSING','ABNORMAL') order by updated_at limit 50"))try{payment.processRefund(number(r.get("case_id")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("WeChat refund reconciliation pending for case {}",r.get("case_id"));}
 }
}
