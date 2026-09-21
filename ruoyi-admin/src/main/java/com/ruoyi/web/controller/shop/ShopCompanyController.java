package com.ruoyi.web.controller.shop;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import static com.ruoyi.web.controller.shop.ShopService.*;
@RestController
@RequestMapping("/shop/app/company")
public class ShopCompanyController {
 @Autowired ShopService s; @Autowired ShopCompany c; @Autowired ShopAccounts accounts;
 private long uid(HttpServletRequest r){return number(s.member(r.getHeader("X-Shop-Token")).get("id"));}
 @Anonymous @GetMapping public AjaxResult get(HttpServletRequest r){return AjaxResult.success(c.overview(uid(r)));}
 @Anonymous @PostMapping("/handover") public AjaxResult handover(HttpServletRequest r,@RequestBody JSONObject b){long member=uid(r);accounts.rate(r.getRemoteAddr(),"company-handover:"+member);c.handover(member,b);return AjaxResult.success();}
 @Anonymous @PostMapping("/handover/answer") public AjaxResult handoverAnswer(HttpServletRequest r,@RequestBody JSONObject b){c.handoverAnswer(uid(r),b);return AjaxResult.success();}
 @Anonymous @PostMapping("/settings") public AjaxResult settings(HttpServletRequest r,@RequestBody JSONObject b){c.settings(uid(r),b);return AjaxResult.success();}
 @Anonymous @PostMapping("/invite") public AjaxResult invite(HttpServletRequest r,@RequestBody JSONObject b){JSONObject data=new JSONObject();data.put("code",c.invite(uid(r),b));return AjaxResult.success(data);}
 @Anonymous @PostMapping("/accept") public AjaxResult accept(HttpServletRequest r,@RequestBody JSONObject b){c.accept(uid(r),b);return AjaxResult.success();}
 @Anonymous @PostMapping("/members/{id}") public AjaxResult member(HttpServletRequest r,@PathVariable long id,@RequestBody JSONObject b){c.member(uid(r),id,b);return AjaxResult.success();}
 @Anonymous @PostMapping("/drafts/{id}/decision") public AjaxResult decide(HttpServletRequest r,@PathVariable long id,@RequestBody JSONObject b){c.decide(uid(r),id,b);return AjaxResult.success();}
 @Anonymous @GetMapping("/drafts/{id}/quote") public AjaxResult quote(HttpServletRequest r,@PathVariable long id){return AjaxResult.success(c.requote(uid(r),id));}
 @Anonymous @PostMapping("/drafts/{id}/submit") public AjaxResult submit(HttpServletRequest r,@PathVariable long id,@RequestBody JSONObject b){return AjaxResult.success(c.submit(uid(r),id,b));}
}
