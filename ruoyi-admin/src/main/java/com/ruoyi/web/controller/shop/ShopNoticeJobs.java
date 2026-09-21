package com.ruoyi.web.controller.shop;
import java.util.Map;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component public class ShopNoticeJobs {
 @Autowired ShopService s;@Autowired ShopNotices notices;
 @Scheduled(fixedDelay=5000,initialDelay=15000) public void deliver(){for(Map<String,Object> r:s.jdbc().queryForList("select event_id from shop_notice_outbox where processed_at is null order by event_id limit 100"))try{notices.process(ShopService.number(r.get("event_id")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Notice delivery failed for event {}",r.get("event_id"),e);}}
 @Scheduled(cron="0 10 3 * * *",zone="Asia/Shanghai") public void cleanQueue(){s.jdbc().update("delete from shop_notice_outbox where processed_at<date_sub(now(),interval 7 day)");}
}
