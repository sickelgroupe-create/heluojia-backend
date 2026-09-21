package com.ruoyi.web.controller.shop;
import java.util.Map;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component public class ShopChatJobs {
 @Autowired ShopService s;@Autowired ShopChat chat;@Autowired ShopChatRetention retention;
 @Scheduled(fixedDelay=60000,initialDelay=20000) public void closeIdle(){s.jdbc().update("update shop_chat set status='closed' where status='open' and last_message_at<date_sub(now(),interval ? day)",chat.settings().getLongValue("idleDays"));}
 @Scheduled(cron="0 20 3 * * *",zone="Asia/Shanghai") public void clean(){for(Map<String,Object> row:s.jdbc().queryForList("select id from shop_chat where protected=0 order by id"))try{retention.purge(ShopService.number(row.get("id")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Chat retention deferred for {}",row.get("id"),e);}for(Map<String,Object> row:s.jdbc().queryForList("select path from shop_chat_file_gc limit 1000"))try{retention.deleteFile(ShopService.str(row.get("path")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Chat file cleanup deferred",e);}}
}
