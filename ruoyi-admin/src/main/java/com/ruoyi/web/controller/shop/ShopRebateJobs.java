package com.ruoyi.web.controller.shop;
import java.util.Map;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component public class ShopRebateJobs {
 @Autowired ShopService s;@Autowired ShopRebate rebate;
 @Scheduled(cron="0 0 3 * * *",zone="Asia/Shanghai") public void settle(){for(Map<String,Object> r:s.jdbc().queryForList("select distinct r.company_id,date_format(o.paid_at,'%Y-%m') period from shop_rebate_order r join shop_order o on o.id=r.order_id where o.paid_at is not null"))try{rebate.settle(ShopService.number(r.get("company_id")),ShopService.str(r.get("period")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Rebate settlement failed for company {}",r.get("company_id"),e);}}
}
