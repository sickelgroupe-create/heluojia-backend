package com.ruoyi.web.controller.shop;
import java.nio.file.*;import java.io.IOException;import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.*;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.http.*;
import com.alibaba.fastjson2.JSONObject;import com.ruoyi.common.annotation.Anonymous;import com.ruoyi.common.core.domain.AjaxResult;import com.ruoyi.common.utils.SecurityUtils;import static com.ruoyi.web.controller.shop.ShopService.*;
@RestController public class ShopCreditController {
 @Autowired ShopService s;@Autowired ShopCredit credit;@Value("${shop.private-path:../runtime/private}") String privatePath;
 private long uid(HttpServletRequest r){return number(s.member(r.getHeader("X-Shop-Token")).get("id"));}
 @Anonymous @GetMapping("/shop/app/company/credit") public AjaxResult account(HttpServletRequest r){return AjaxResult.success(credit.account(uid(r)));}
 @Anonymous @PostMapping("/shop/app/company/credit/repay") public AjaxResult repay(HttpServletRequest r,@RequestBody JSONObject b){credit.repay(uid(r),b);return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:finance')") @GetMapping("/shop/admin/credit") public AjaxResult list(){JSONObject r=new JSONObject();r.put("companies",credit.admin());r.put("repayments",credit.repayments());return AjaxResult.success(r);}
 @PreAuthorize("@ss.hasPermi('shop:manage')") @PostMapping("/shop/admin/credit/{id}") public AjaxResult configure(@PathVariable long id,@RequestBody JSONObject b){credit.configure(id,b,SecurityUtils.getUsername());return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:finance')") @PostMapping("/shop/admin/credit/repayments/{id}") public AjaxResult verify(@PathVariable long id,@RequestBody JSONObject b){credit.verify(id,b,SecurityUtils.getUsername());return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:finance')") @GetMapping("/shop/admin/credit/repayments/{id}/proof") public ResponseEntity<byte[]> proof(@PathVariable long id)throws IOException {String proof=str(s.one("select proof from shop_credit_repayment where id=?",id).get("proof"));String name=proof.substring(proof.lastIndexOf('/')+1);check(name.matches("[a-f0-9-]{36}\\.png"),"凭证不存在");Path p=Paths.get(privatePath).resolve(name);if(!Files.exists(p))return ResponseEntity.notFound().build();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(Files.readAllBytes(p));}
}
