package com.ruoyi.web.controller.shop;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import static com.ruoyi.web.controller.shop.ShopService.*;
@RestController
public class ShopWalletController {
 @Autowired ShopService s; @Autowired ShopWallet wallet;
 private long uid(HttpServletRequest r){return number(s.member(r.getHeader("X-Shop-Token")).get("id"));}
 @Anonymous @GetMapping("/shop/app/wallet") public AjaxResult account(HttpServletRequest r){return AjaxResult.success(wallet.account(uid(r)));}
 @Anonymous @PostMapping("/shop/app/wallet/recharge") public AjaxResult recharge(HttpServletRequest r,@RequestBody JSONObject b){wallet.recharge(uid(r),b);return AjaxResult.success();}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:finance')") @GetMapping("/shop/admin/wallet") public AjaxResult ledger(){return AjaxResult.success(wallet.adminLedger());}
}
