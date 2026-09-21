package com.ruoyi.web.controller.shop;

import com.alibaba.fastjson2.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.transaction.support.*;
import org.springframework.transaction.PlatformTransactionManager;
import com.ruoyi.common.filter.RepeatableFilter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopWechatPayTest {
 ShopWechatPayGateway gateway;
 KeyPair platform;
 @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="wechat.credentials.properties",matches=".+")
 void realMerchantCredentialsAndGatewayPassReadOnlyProbe()throws Exception{
  Properties p=new Properties();try(java.io.Reader in=new java.io.InputStreamReader(new java.io.FileInputStream(System.getProperty("wechat.credentials.properties")),StandardCharsets.UTF_8)){p.load(in);}
  ShopWechatPayGateway g=new ShopWechatPayGateway();g.enabled=true;g.appId=p.getProperty("shop.wechat.app-id");g.merchantId=p.getProperty("shop.wechat-pay.mch-id");g.apiKey=p.getProperty("shop.wechat-pay.api-v3-key");g.certificatePath=p.getProperty("shop.wechat-pay.certificate-path");g.privateKeyPath=p.getProperty("shop.wechat-pay.private-key-path");g.publicKeyPath=p.getProperty("shop.wechat-pay.public-key-path");g.publicKeyId=p.getProperty("shop.wechat-pay.public-key-id");g.notifyUrl=p.getProperty("shop.wechat-pay.notify-url");g.init();assertTrue(g.ready());
  ShopWechatPayGateway.ApiException ex=assertThrows(ShopWechatPayGateway.ApiException.class,()->g.query("WXVERIFY"+System.currentTimeMillis()));assertEquals("ORDER_NOT_EXIST",ex.code);
 }
 @BeforeEach void setup()throws Exception{
  KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);platform=gen.generateKeyPair();KeyPair merchant=gen.generateKeyPair();
  gateway=new ShopWechatPayGateway(){@Override public boolean ready(){return true;}};
  gateway.publicKey=platform.getPublic();gateway.privateKey=merchant.getPrivate();gateway.publicKeyId="PUB_KEY_ID_fixture";
  gateway.appId="wx0123456789abcdef";gateway.merchantId="1234567890";gateway.apiKey="0123456789abcdef0123456789abcdef";
 }
 String signature(String body,String time)throws Exception{Signature sig=Signature.getInstance("SHA256withRSA");sig.initSign(platform.getPrivate());sig.update((time+"\nnonce\n"+body+"\n").getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(sig.sign());}
 @Test void signatureRejectsTamperingUnknownKeysStaleAndMissingHeaders()throws Exception{
  String body="{\n  \"ok\": true\n}\n",time=Long.toString(Instant.now().getEpochSecond()),sig=signature(body,time);
  assertDoesNotThrow(()->gateway.verify(time,"nonce",sig,gateway.publicKeyId,body));
  assertThrows(IllegalArgumentException.class,()->gateway.verify(time,"nonce",sig,gateway.publicKeyId,body.trim()));
  assertThrows(IllegalArgumentException.class,()->gateway.verify(time,"nonce",sig,"PUB_KEY_ID_other",body));
  String old=Long.toString(Instant.now().getEpochSecond()-301),oldSig=signature(body,old);
  assertThrows(IllegalArgumentException.class,()->gateway.verify(old,"nonce",oldSig,gateway.publicKeyId,body));
  assertThrows(IllegalArgumentException.class,()->gateway.verify(null,"nonce",sig,gateway.publicKeyId,body));
  assertThrows(IllegalArgumentException.class,()->gateway.verify(time,"nonce","WECHATPAY/SIGNTEST/fixture",gateway.publicKeyId,body));
 }
 JSONObject transaction(){return JSONObject.parseObject("{\"appid\":\"wx0123456789abcdef\",\"mchid\":\"1234567890\",\"out_trade_no\":\"BCfixture123\",\"trade_state\":\"SUCCESS\",\"trade_type\":\"JSAPI\",\"transaction_id\":\"4200000000000000000000000001\",\"amount\":{\"total\":1234,\"currency\":\"CNY\"},\"payer\":{\"openid\":\"test_openid_123\"}}");}
 Map<String,Object> payment(){Map<String,Object> p=new HashMap<>();p.put("order_id",9L);p.put("out_trade_no","BCfixture123");p.put("amount",1234L);p.put("payer_openid","test_openid_123");p.put("state","READY");return p;}
 @Test void decryptedNotificationRequiresValidAeadTagAndEvent()throws Exception{
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(gateway.apiKey.getBytes(StandardCharsets.UTF_8),"AES"),new GCMParameterSpec(128,"123456789012".getBytes(StandardCharsets.UTF_8)));c.updateAAD("transaction".getBytes(StandardCharsets.UTF_8));
  byte[] encrypted=c.doFinal(transaction().toJSONString().getBytes(StandardCharsets.UTF_8));
  JSONObject resource=new JSONObject();resource.put("algorithm","AEAD_AES_256_GCM");resource.put("original_type","transaction");resource.put("nonce","123456789012");resource.put("associated_data","transaction");resource.put("ciphertext",Base64.getEncoder().encodeToString(encrypted));
  JSONObject envelope=new JSONObject();envelope.put("event_type","TRANSACTION.SUCCESS");envelope.put("resource_type","encrypt-resource");envelope.put("resource",resource);
  String body=envelope.toJSONString(),time=Long.toString(Instant.now().getEpochSecond());assertEquals("SUCCESS",gateway.notification(body,time,"nonce",signature(body,time),gateway.publicKeyId).getString("trade_state"));
  encrypted[0]^=1;resource.put("ciphertext",Base64.getEncoder().encodeToString(encrypted));String bad=envelope.toJSONString(),badSig=signature(bad,time);
  assertThrows(IllegalArgumentException.class,()->gateway.notification(bad,time,"nonce",badSig,gateway.publicKeyId));
 }
 @Test void transactionValidatesAmountCurrencyMerchantAppPayerAndOrder(){
  assertDoesNotThrow(()->gateway.validateTransaction(transaction(),payment()));
  for(String field:Arrays.asList("appid","mchid","out_trade_no","trade_type","trade_state","transaction_id")){JSONObject t=transaction();t.put(field,"wrong");assertThrows(IllegalArgumentException.class,()->gateway.validateTransaction(t,payment()),field);}
  JSONObject t=transaction();t.getJSONObject("amount").put("total",1);final JSONObject amount=t;assertThrows(IllegalArgumentException.class,()->gateway.validateTransaction(amount,payment()));
  t=transaction();t.getJSONObject("amount").put("currency","USD");final JSONObject currency=t;assertThrows(IllegalArgumentException.class,()->gateway.validateTransaction(currency,payment()));
  t=transaction();t.getJSONObject("payer").put("openid","someone_else");final JSONObject payer=t;assertThrows(IllegalArgumentException.class,()->gateway.validateTransaction(payer,payment()));
 }
 @Test void requestAndClientPaymentSignaturesUseExactCanonicalPayload()throws Exception{
  assertEquals("GET\n/v3/test?mchid=123\n100\nabc\n\n",ShopWechatPayGateway.canonical("GET","/v3/test?mchid=123","100","abc",""));
  JSONObject p=gateway.paymentParameters("wx_fixture");Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.RSAPublicKeySpec(((java.security.interfaces.RSAPrivateCrtKey)gateway.privateKey).getModulus(),((java.security.interfaces.RSAPrivateCrtKey)gateway.privateKey).getPublicExponent())));
  verifier.update((gateway.appId+"\n"+p.getString("timeStamp")+"\n"+p.getString("nonceStr")+"\n"+p.getString("package")+"\n").getBytes(StandardCharsets.UTF_8));assertTrue(verifier.verify(Base64.getDecoder().decode(p.getString("paySign"))));assertEquals("RSA",p.getString("signType"));
 }
 @Test void repeatFilterPreservesOriginalNotificationBytes()throws Exception{
  for(String endpoint:Arrays.asList("notify","refund-notify")){
  MockHttpServletRequest request=new MockHttpServletRequest("POST","/shop/wechat-pay/"+endpoint);request.setContentType("application/json");byte[] raw="{\n \"x\" : 1\n}\n".getBytes(StandardCharsets.UTF_8);request.setContent(raw);
  new RepeatableFilter().doFilter(request,new MockHttpServletResponse(),(req,res)->{byte[] actual=org.springframework.util.StreamUtils.copyToByteArray(req.getInputStream());assertArrayEquals(raw,actual);});
  }
 }
 @Test void refundNotificationRequiresMatchingSignedEventAndAead()throws Exception{
  JSONObject refund=JSONObject.parseObject("{\"refund_status\":\"SUCCESS\",\"out_refund_no\":\"WXRF29C22\"}");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(gateway.apiKey.getBytes(StandardCharsets.UTF_8),"AES"),new GCMParameterSpec(128,"123456789012".getBytes(StandardCharsets.UTF_8)));cipher.updateAAD("refund".getBytes(StandardCharsets.UTF_8));
  JSONObject resource=new JSONObject();resource.put("algorithm","AEAD_AES_256_GCM");resource.put("original_type","refund");resource.put("nonce","123456789012");resource.put("associated_data","refund");resource.put("ciphertext",Base64.getEncoder().encodeToString(cipher.doFinal(refund.toJSONString().getBytes(StandardCharsets.UTF_8))));
  JSONObject envelope=new JSONObject();envelope.put("event_type","REFUND.SUCCESS");envelope.put("resource_type","encrypt-resource");envelope.put("resource",resource);
  String time=Long.toString(Instant.now().getEpochSecond()),body=envelope.toJSONString();assertEquals("SUCCESS",gateway.refundNotification(body,time,"nonce",signature(body,time),gateway.publicKeyId).getString("refund_status"));
  assertThrows(IllegalArgumentException.class,()->gateway.notification(body,time,"nonce",signature(body,time),gateway.publicKeyId));
  envelope.put("event_type","REFUND.CLOSED");String mismatch=envelope.toJSONString();assertThrows(IllegalArgumentException.class,()->gateway.refundNotification(mismatch,time,"nonce",signature(mismatch,time),gateway.publicKeyId));
 }
 ShopWechatPay service(Map<String,Object> order,Map<String,Object> p){
  ShopWechatPay pay=new ShopWechatPay();pay.gateway=gateway;pay.s=mock(ShopService.class);pay.s.db=mock(JdbcTemplate.class);
  when(pay.s.jdbc()).thenReturn(pay.s.db);when(pay.s.one("select * from shop_wechat_payment where order_id=? for update",9L)).thenReturn(p);
  when(pay.s.one("select * from shop_order_funds where order_id=? for update",9L)).thenReturn(JSONObject.parseObject("{\"state\":\"reserved\",\"external_amount\":1234}"));return pay;
 }
 @Test void duplicateNotificationDoesNotBookTwiceAndConflictingTransactionFails(){
  Map<String,Object> order=JSONObject.parseObject("{\"id\":9,\"member_id\":1,\"status\":\"pending\"}"),p=payment();ShopWechatPay pay=service(order,p);
  pay.settle(order,transaction());verify(pay.s,times(1)).recordWechatPayment(order);
  p.put("state","SUCCESS");p.put("transaction_id",transaction().getString("transaction_id"));when(pay.s.jdbc().queryForObject("select count(*) from shop_payment where order_id=? and mode='wechat'",Long.class,9L)).thenReturn(1L);
  pay.settle(order,transaction());verify(pay.s,times(1)).recordWechatPayment(order);
  JSONObject different=transaction();different.put("transaction_id","4200000000000000000000000002");assertThrows(IllegalArgumentException.class,()->pay.settle(order,different));
 }
 @Test void mismatchedAmountCannotReachBookkeeping(){Map<String,Object> order=JSONObject.parseObject("{\"id\":9,\"status\":\"pending\"}");ShopWechatPay pay=service(order,payment());JSONObject t=transaction();t.getJSONObject("amount").put("total",1);assertThrows(IllegalArgumentException.class,()->pay.settle(order,t));verify(pay.s,never()).recordWechatPayment(anyMap());}
 @Test void cancellationOfPaidRemoteOrderSettlesWithoutReleasing(){
  Map<String,Object> order=JSONObject.parseObject("{\"id\":9,\"member_id\":1,\"status\":\"pending\"}"),p=payment();ShopWechatPay pay=spy(service(order,p));
  doReturn(true).when(pay).started(9L);doReturn(transaction()).when(pay).query(9L);assertFalse(pay.closeBeforeRelease(order));verify(pay.s).recordWechatPayment(order);
 }
 @Test void unknownPrepayOutcomeCannotReleaseReservations(){Map<String,Object> order=JSONObject.parseObject("{\"id\":9,\"status\":\"pending\"}");ShopWechatPay pay=spy(service(order,payment()));doReturn(true).when(pay).started(9L);doReturn(null).when(pay).query(9L);assertThrows(IllegalArgumentException.class,()->pay.closeBeforeRelease(order));verify(pay.s,never()).recordWechatPayment(anyMap());}
}
