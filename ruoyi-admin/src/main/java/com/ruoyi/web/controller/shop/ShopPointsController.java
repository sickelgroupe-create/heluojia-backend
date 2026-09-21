package com.ruoyi.web.controller.shop;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import static com.ruoyi.web.controller.shop.ShopService.*;
@RestController
public class ShopPointsController {
 @Autowired ShopService s; @Autowired ShopPoints points;
 @Anonymous @GetMapping("/shop/app/points") public AjaxResult account(HttpServletRequest r){long uid=number(s.member(r.getHeader("X-Shop-Token")).get("id"));points.expire(uid);return AjaxResult.success(points.account(uid));}
 @PreAuthorize("@ss.hasPermi('shop:manage')") @GetMapping("/shop/admin/loyalty-settings") public AjaxResult rules(){return AjaxResult.success(points.rules());}
 @PreAuthorize("@ss.hasPermi('shop:manage')") @PostMapping("/shop/admin/loyalty-settings") public AjaxResult save(@RequestBody JSONObject b){points.saveRules(b,SecurityUtils.getUsername());return AjaxResult.success();}
}
