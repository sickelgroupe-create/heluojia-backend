package com.ruoyi.web.controller.shop;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.*;
import java.util.Map;
@Component @EnableScheduling
public class ShopOrderExpiry {
 @Autowired ShopService service;
 @Scheduled(fixedDelay=60000,initialDelay=10000) public void closeUnpaid(){for(Map<String,Object> row:service.jdbc().queryForList("select id from shop_order where status='pending' and expires_at<=now() limit 100")){try{service.expireOrder(ShopService.number(row.get("id")));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Unpaid order close failed for {}",row.get("id"));}}}
}
