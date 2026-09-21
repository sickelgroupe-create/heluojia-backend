package com.ruoyi.web.controller.shop;
import javax.servlet.http.HttpServletRequest;import com.alibaba.fastjson2.JSONObject;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import com.ruoyi.common.annotation.Anonymous;import com.ruoyi.common.core.domain.AjaxResult;import com.ruoyi.common.utils.SecurityUtils;import static com.ruoyi.web.controller.shop.ShopService.*;
@RestController public class ShopMarketingController {
 @Autowired ShopMarketing marketing;@Autowired ShopService s;
 @Anonymous @GetMapping("/shop/app/campaigns") public AjaxResult list(@RequestParam(defaultValue="C") String channel){check("C".equals(channel)||"B".equals(channel),"渠道不正确");return AjaxResult.success(marketing.list(false,channel));}
 @Anonymous @GetMapping("/shop/app/orders/{id}/campaign") public AjaxResult order(HttpServletRequest r,@PathVariable long id){return AjaxResult.success(marketing.info(number(s.member(r.getHeader("X-Shop-Token")).get("id")),id));}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:catalog')") @GetMapping("/shop/admin/campaigns") public AjaxResult admin(){return AjaxResult.success(marketing.list(true,"C"));}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:catalog')") @GetMapping("/shop/admin/campaigns/grades") public AjaxResult grades(){return AjaxResult.success(marketing.gradeOptions());}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:catalog')") @PostMapping("/shop/admin/campaigns") public AjaxResult save(@RequestBody JSONObject b){JSONObject r=new JSONObject();r.put("id",marketing.save(b,SecurityUtils.getUsername()));return AjaxResult.success(r);}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:catalog')") @PostMapping("/shop/admin/campaigns/{id}/{action}") public AjaxResult action(@PathVariable long id,@PathVariable String action){if("publish".equals(action))marketing.publish(id,SecurityUtils.getUsername());else if("close".equals(action))marketing.close(id,SecurityUtils.getUsername());else throw new IllegalArgumentException("不支持的活动操作");return AjaxResult.success();}
}
