package com.ruoyi.web.controller.shop;

import com.alibaba.fastjson2.JSONObject;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import static com.ruoyi.web.controller.shop.ShopService.*;

@RestController
public class ShopWechatPayController {
 @Autowired ShopService s;
 @Autowired ShopWechatPay payment;
 @Autowired ShopWechatPayGateway gateway;
 @Anonymous @PostMapping("/shop/app/orders/{id}/wechat-prepay")
 public AjaxResult prepay(HttpServletRequest r,@PathVariable long id){return AjaxResult.success(payment.prepay(number(s.member(r.getHeader("X-Shop-Token")).get("id")),id));}
 @Anonymous @PostMapping("/shop/app/orders/{id}/wechat-status")
 public AjaxResult status(HttpServletRequest r,@PathVariable long id){return AjaxResult.success(payment.status(number(s.member(r.getHeader("X-Shop-Token")).get("id")),id));}
 @Anonymous @PostMapping("/shop/wechat-pay/notify")
 public ResponseEntity<?> notify(HttpServletRequest r,@RequestBody byte[] bytes){
  try{
   check(bytes.length<=65536,"支付通知过大");
   JSONObject transaction=gateway.notification(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),r.getHeader("Wechatpay-Timestamp"),r.getHeader("Wechatpay-Nonce"),r.getHeader("Wechatpay-Signature"),r.getHeader("Wechatpay-Serial"));
   payment.notification(transaction);return ResponseEntity.noContent().build();
  }catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("WeChat payment notification rejected: {}",e.getClass().getSimpleName());return ResponseEntity.status(500).body(Collections.singletonMap("code","FAIL"));}
 }
 @Anonymous @PostMapping("/shop/wechat-pay/refund-notify")
 public ResponseEntity<?> refundNotify(HttpServletRequest r,@RequestBody byte[] bytes){
  try{
   check(bytes.length<=65536,"退款通知过大");
   JSONObject refund=gateway.refundNotification(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),r.getHeader("Wechatpay-Timestamp"),r.getHeader("Wechatpay-Nonce"),r.getHeader("Wechatpay-Signature"),r.getHeader("Wechatpay-Serial"));
   payment.refundNotification(refund);return ResponseEntity.noContent().build();
  }catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("WeChat refund notification rejected: {}",e.getClass().getSimpleName());return ResponseEntity.status(500).body(Collections.singletonMap("code","FAIL"));}
 }
}
