package com.ruoyi.web.controller.shop;
import javax.servlet.http.HttpServletRequest;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.web.bind.annotation.*;import com.alibaba.fastjson2.JSONObject;import com.ruoyi.common.annotation.Anonymous;import com.ruoyi.common.core.domain.AjaxResult;import static com.ruoyi.web.controller.shop.ShopService.*;
@RestController @RequestMapping("/shop/app/notices") public class ShopNoticeController {
 @Autowired ShopService s;@Autowired ShopNotices notices;private long uid(HttpServletRequest r){return number(s.member(r.getHeader("X-Shop-Token")).get("id"));}
 @Anonymous @GetMapping public AjaxResult list(HttpServletRequest r,@RequestParam(defaultValue="1") int page){return AjaxResult.success(notices.list(uid(r),page));}
 @Anonymous @PostMapping("/{id}/read") public AjaxResult read(HttpServletRequest r,@PathVariable long id){notices.read(uid(r),id);return AjaxResult.success();}
 @Anonymous @PostMapping("/read-all") public AjaxResult all(HttpServletRequest r){notices.readAll(uid(r));return AjaxResult.success();}
 @Anonymous @PostMapping("/preferences") public AjaxResult preferences(HttpServletRequest r,@RequestBody JSONObject b){notices.preferences(uid(r),b);return AjaxResult.success();}
}
