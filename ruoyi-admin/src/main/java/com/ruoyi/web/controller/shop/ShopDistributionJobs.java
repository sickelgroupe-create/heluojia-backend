package com.ruoyi.web.controller.shop;
import java.util.Map;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component public class ShopDistributionJobs {
 @Autowired ShopService s;@Autowired ShopDistribution d;
 @Scheduled(fixedDelay=60000,initialDelay=20000) public void settle(){for(Map<String,Object> r:s.jdbc().queryForList("select distinct order_id from shop_reward where ready_at<=now() or reason='相关商品售后处理中' limit 100"))try{d.refresh(ShopService.number(r.get("order_id")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Reward settlement failed for order {}",r.get("order_id"),e);}}
 @Scheduled(cron="0 30 2 * * *",zone="Asia/Shanghai") public void tiers(){if(d.settings().getBooleanValue("autoTier"))for(Map<String,Object> r:s.jdbc().queryForList("select id from shop_member where distributor_state='approved'"))d.updateTier(ShopService.number(r.get("id")));}
}
