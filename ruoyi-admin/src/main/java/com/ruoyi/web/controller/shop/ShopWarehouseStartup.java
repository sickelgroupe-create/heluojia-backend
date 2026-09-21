package com.ruoyi.web.controller.shop;
import java.util.Map;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.context.event.ApplicationReadyEvent;import org.springframework.context.event.EventListener;import org.springframework.stereotype.Component;
@Component public class ShopWarehouseStartup {
 @Autowired ShopService s;
 @EventListener(ApplicationReadyEvent.class) public void init(){for(Map<String,Object> p:s.jdbc().queryForList("select id from shop_product where id not in (select product_id from shop_warehouse_stock)"))s.warehouseEnsure(ShopService.number(p.get("id")));}
}
