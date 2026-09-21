package com.ruoyi.web.controller.shop;
import javax.servlet.http.HttpServletRequest;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import com.alibaba.fastjson2.JSONObject;import com.ruoyi.common.annotation.Anonymous;import com.ruoyi.common.core.domain.AjaxResult;import com.ruoyi.common.utils.SecurityUtils;import static com.ruoyi.web.controller.shop.ShopService.*;
@RestController public class ShopReviewController {
 @Autowired ShopService s;@Autowired ShopReviews reviews;private long uid(HttpServletRequest r){return number(s.member(r.getHeader("X-Shop-Token")).get("id"));}
 @Anonymous @GetMapping("/shop/app/reviews") public AjaxResult mine(HttpServletRequest r){return AjaxResult.success(reviews.mine(uid(r)));}
 @Anonymous @GetMapping("/shop/app/products/{id}/reviews") public AjaxResult product(@PathVariable long id,@RequestParam(defaultValue="1") int page){return AjaxResult.success(reviews.product(id,page));}
 @Anonymous @PostMapping("/shop/app/reviews") public AjaxResult post(HttpServletRequest r,@RequestBody JSONObject b){reviews.post(uid(r),b);return AjaxResult.success();}
 @Anonymous @PostMapping("/shop/app/reviews/{id}/followup") public AjaxResult followup(HttpServletRequest r,@PathVariable long id,@RequestBody JSONObject b){reviews.followup(uid(r),id,b);return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:customer')") @GetMapping("/shop/admin/reviews") public AjaxResult admin(){JSONObject r=new JSONObject();r.put("rows",reviews.admin());r.put("settings",reviews.settings());return AjaxResult.success(r);}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:customer')") @PostMapping("/shop/admin/reviews/{id}") public AjaxResult moderate(@PathVariable long id,@RequestBody JSONObject b){reviews.moderate(id,b,SecurityUtils.getUsername());return AjaxResult.success();}
 @PreAuthorize("@ss.hasPermi('shop:manage')") @PostMapping("/shop/admin/review-settings") public AjaxResult settings(@RequestBody JSONObject b){reviews.settings(b,SecurityUtils.getUsername());return AjaxResult.success();}
}
