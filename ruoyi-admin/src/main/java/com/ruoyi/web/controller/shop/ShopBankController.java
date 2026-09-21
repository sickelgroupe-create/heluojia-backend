package com.ruoyi.web.controller.shop;
import java.nio.file.*;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.*;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import static com.ruoyi.web.controller.shop.ShopService.*;
@RestController
public class ShopBankController {
 @Autowired ShopService s; @Autowired ShopBank bank;
 @Value("${shop.private-path:../runtime/private}") String privatePath;
 private long uid(HttpServletRequest r){return number(s.member(r.getHeader("X-Shop-Token")).get("id"));}
 @Anonymous @GetMapping("/shop/app/orders/{id}/bank") public AjaxResult info(HttpServletRequest r,@PathVariable long id){return AjaxResult.success(bank.info(uid(r),id));}
 @Anonymous @PostMapping("/shop/app/orders/{id}/bank/start") public AjaxResult start(HttpServletRequest r,@PathVariable long id){bank.start(uid(r),id);return AjaxResult.success();}
 @Anonymous @PostMapping("/shop/app/orders/{id}/bank/receipts") public AjaxResult submit(HttpServletRequest r,@PathVariable long id,@RequestBody JSONObject b){bank.submit(uid(r),id,b);return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:finance')") @GetMapping("/shop/admin/bank") public AjaxResult list(){JSONObject r=new JSONObject();r.put("settings",bank.settings());r.put("receipts",bank.receipts());r.put("refunds",bank.refunds());return AjaxResult.success(r);}
 @PreAuthorize("@ss.hasPermi('shop:manage')") @PostMapping("/shop/admin/bank/settings") public AjaxResult settings(@RequestBody JSONObject b){bank.settings(b,SecurityUtils.getUsername());return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:finance')") @PostMapping("/shop/admin/bank/receipts/{id}") public AjaxResult review(@PathVariable long id,@RequestBody JSONObject b){bank.review(id,b,SecurityUtils.getUsername());return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:finance')") @PostMapping("/shop/admin/bank/refunds/{id}") public AjaxResult refund(@PathVariable long id,@RequestBody JSONObject b){bank.refund(id,b,SecurityUtils.getUsername());return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:finance')") @GetMapping("/shop/admin/bank/receipts/{id}/proof") public ResponseEntity<byte[]> proof(@PathVariable long id)throws IOException {String proof=str(s.one("select proof from shop_bank_receipt where id=?",id).get("proof"));String name=proof.substring(proof.lastIndexOf('/')+1);check(name.matches("[a-f0-9-]{36}\\.png"),"凭证不存在");Path p=Paths.get(privatePath).resolve(name);if(!Files.exists(p))return ResponseEntity.notFound().build();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(Files.readAllBytes(p));}
}
