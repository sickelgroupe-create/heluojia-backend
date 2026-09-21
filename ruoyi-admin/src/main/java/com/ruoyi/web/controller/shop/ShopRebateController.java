package com.ruoyi.web.controller.shop;
import java.util.*;import javax.servlet.http.HttpServletRequest;import com.alibaba.fastjson2.JSONObject;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import com.ruoyi.common.annotation.Anonymous;import com.ruoyi.common.core.domain.AjaxResult;import com.ruoyi.common.utils.SecurityUtils;import static com.ruoyi.web.controller.shop.ShopService.*;
@RestController public class ShopRebateController {
 @Autowired ShopRebate rebate;@Autowired ShopService s;
 @Anonymous @GetMapping("/shop/app/company/rebate") public AjaxResult own(HttpServletRequest r){return AjaxResult.success(rebate.summary(number(s.member(r.getHeader("X-Shop-Token")).get("id"))));}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:finance')") @GetMapping("/shop/admin/rebates") public AjaxResult admin(){return AjaxResult.success(rebate.adminSummary());}
 @PreAuthorize("@ss.hasPermi('shop:manage')") @PostMapping("/shop/admin/rebates/settings") public AjaxResult save(@RequestBody JSONObject b){rebate.save(b,SecurityUtils.getUsername());return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:finance')") @PostMapping("/shop/admin/rebates/settle") public AjaxResult settle(@RequestBody JSONObject b){String month=str(b.get("period"));check(month.matches("[0-9]{4}-[0-9]{2}"),"请选择月份");rebate.settle(b.getLongValue("companyId"),month);return AjaxResult.success();}
}
