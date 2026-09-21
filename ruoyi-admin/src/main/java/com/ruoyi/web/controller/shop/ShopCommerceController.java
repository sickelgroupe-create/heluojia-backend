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
public class ShopCommerceController {
 @Autowired ShopService s; @Autowired ShopCommerce commerce;
 private long uid(HttpServletRequest r){return number(s.member(r.getHeader("X-Shop-Token")).get("id"));}
 @Anonymous @GetMapping("/shop/app/cart/{channel}") public AjaxResult cart(HttpServletRequest r,@PathVariable String channel){return AjaxResult.success(commerce.cart(uid(r),channel));}
 @Anonymous @PutMapping("/shop/app/cart/{channel}") public AjaxResult cartSave(HttpServletRequest r,@PathVariable String channel,@RequestBody JSONObject b){return AjaxResult.success(commerce.saveCart(uid(r),channel,b));}
 @Anonymous @GetMapping("/shop/app/coupons") public AjaxResult coupons(HttpServletRequest r){return AjaxResult.success(commerce.coupons(uid(r)));}
 @Anonymous @GetMapping("/shop/app/coupon-campaigns") public AjaxResult campaigns(){return AjaxResult.success(commerce.campaigns(false));}
 @Anonymous @PostMapping("/shop/app/coupon-campaigns/{id}/claim") public AjaxResult claim(HttpServletRequest r,@PathVariable long id,@RequestBody JSONObject b){return AjaxResult.success(commerce.claim(uid(r),id,b));}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:catalog')") @GetMapping("/shop/admin/coupons") public AjaxResult admin(){return AjaxResult.success(commerce.campaigns(true));}
 @PreAuthorize("@ss.hasPermi('shop:manage')") @PostMapping("/shop/admin/coupons") public AjaxResult save(@RequestBody JSONObject b){commerce.saveCampaign(b,SecurityUtils.getUsername());return AjaxResult.success();}
 @PreAuthorize("@ss.hasPermi('shop:manage')") @DeleteMapping("/shop/admin/coupons/{id}") public AjaxResult delete(@PathVariable long id){commerce.deleteCampaign(id,SecurityUtils.getUsername());return AjaxResult.success();}
 @PreAuthorize("@ss.hasPermi('shop:manage')") @PostMapping("/shop/admin/coupons/{id}/terminate") public AjaxResult terminate(@PathVariable long id){commerce.terminateCampaign(id,SecurityUtils.getUsername());return AjaxResult.success();}
}
