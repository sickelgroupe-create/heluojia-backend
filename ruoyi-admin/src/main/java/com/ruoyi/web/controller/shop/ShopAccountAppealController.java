package com.ruoyi.web.controller.shop;
import javax.servlet.http.HttpServletRequest;import com.alibaba.fastjson2.JSONObject;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import com.ruoyi.common.annotation.Anonymous;import com.ruoyi.common.core.domain.AjaxResult;import com.ruoyi.common.utils.SecurityUtils;
@RestController public class ShopAccountAppealController {
 @Autowired ShopAccountAppeal appeal;@Autowired ShopAccounts accounts;
 @Anonymous @PostMapping("/shop/app/account-appeals") public AjaxResult submit(HttpServletRequest r,@RequestBody JSONObject b){accounts.rate(r.getRemoteAddr(),"appeal");return AjaxResult.success(appeal.submit(b));}
 @Anonymous @PostMapping("/shop/app/account-appeals/status") public AjaxResult status(HttpServletRequest r,@RequestBody JSONObject b){accounts.rate(r.getRemoteAddr(),"appeal-query");return AjaxResult.success(appeal.status(b.getString("key")));}
 @Anonymous @PostMapping("/shop/app/account-appeals/supplement") public AjaxResult supplement(HttpServletRequest r,@RequestBody JSONObject b){accounts.rate(r.getRemoteAddr(),"appeal");appeal.supplement(b.getString("key"),b);return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:customer')") @GetMapping("/shop/admin/account-appeals") public AjaxResult list(){return AjaxResult.success(appeal.admin());}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:customer')") @PostMapping("/shop/admin/account-appeals/{id}") public AjaxResult review(@PathVariable long id,@RequestBody JSONObject b){appeal.review(id,b,SecurityUtils.getUsername(),SecurityUtils.hasPermi("shop:manage"));return AjaxResult.success();}
}
