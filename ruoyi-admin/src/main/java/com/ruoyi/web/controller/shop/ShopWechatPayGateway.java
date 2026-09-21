package com.ruoyi.web.controller.shop;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;
import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.ruoyi.web.controller.shop.ShopService.*;

/** Direct merchant APIv3. No credentials, request bodies or OpenIDs are logged. */
@Component
public class ShopWechatPayGateway {
 @Value("${shop.wechat-pay.enabled:false}") boolean enabled;
 @Value("${shop.wechat.app-id:}") String appId;
 @Value("${shop.wechat-pay.mch-id:}") String merchantId;
 @Value("${shop.wechat-pay.api-v3-key:}") String apiKey;
 @Value("${shop.wechat-pay.certificate-path:}") String certificatePath;
 @Value("${shop.wechat-pay.private-key-path:}") String privateKeyPath;
 @Value("${shop.wechat-pay.public-key-path:}") String publicKeyPath;
 @Value("${shop.wechat-pay.public-key-id:}") String publicKeyId;
 @Value("${shop.wechat-pay.notify-url:}") String notifyUrl;
 PrivateKey privateKey;
 PublicKey publicKey;
 String serial;
 private boolean configured;

 @PostConstruct public void init() {
  if(!enabled)return;
  try {
   check(appId.matches("wx[0-9a-fA-F]{16}")&&merchantId.matches("[0-9]{8,12}"),"微信支付商户配置不正确");
   check(apiKey.getBytes(StandardCharsets.UTF_8).length==32,"APIv3密钥必须为32字节");
   check(publicKeyId.matches("PUB_KEY_ID_[0-9A-Za-z]+"),"微信支付公钥ID未配置");
   URI uri=URI.create(notifyUrl);
   check("https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null,"请配置HTTPS支付回调地址");
   KeyFactory factory=KeyFactory.getInstance("RSA");
   privateKey=factory.generatePrivate(new PKCS8EncodedKeySpec(pem(privateKeyPath,"PRIVATE KEY")));
   publicKey=factory.generatePublic(new X509EncodedKeySpec(pem(publicKeyPath,"PUBLIC KEY")));
   X509Certificate cert;
   try(InputStream in=Files.newInputStream(Paths.get(certificatePath))){cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);}
   cert.checkValidity();
   javax.naming.ldap.LdapName subject=new javax.naming.ldap.LdapName(cert.getSubjectX500Principal().getName());
   boolean merchant=false;for(javax.naming.ldap.Rdn r:subject.getRdns())if("CN".equalsIgnoreCase(r.getType())&&merchantId.equals(str(r.getValue())))merchant=true;
   check(merchant,"证书所属商户号不一致");
   serial=cert.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);
   Signature proof=Signature.getInstance("SHA256withRSA");proof.initVerify(cert.getPublicKey());proof.update("wechat-pay-key-check".getBytes(StandardCharsets.UTF_8));
   check(proof.verify(Base64.getDecoder().decode(sign("wechat-pay-key-check"))),"商户证书与私钥不匹配");
   configured=true;
  }catch(Exception e){throw new IllegalStateException("微信支付配置校验失败，请检查服务端证书、密钥及公钥ID");}
 }
 private byte[] pem(String path,String label)throws IOException {
  String value=new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.US_ASCII);
  return Base64.getDecoder().decode(value.replace("-----BEGIN "+label+"-----","").replace("-----END "+label+"-----","").replaceAll("\\s", ""));
 }
 public boolean ready(){return enabled&&configured;}
 void requireReady(){check(ready(),"微信支付尚未配置完成，请联系商家");}
 String sign(String message)throws GeneralSecurityException {
  Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(privateKey);signature.update(message.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(signature.sign());
 }
 static String nonce(){return UUID.randomUUID().toString().replace("-","");}
 static String canonical(String method,String path,String timestamp,String nonce,String body){return method+"\n"+path+"\n"+timestamp+"\n"+nonce+"\n"+body+"\n";}
 public void verify(String timestamp,String nonce,String signature,String keyId,String body){
  try {
   check(publicKeyId.equals(keyId),"支付签名公钥ID不匹配");
   check(timestamp!=null&&timestamp.matches("[0-9]{1,12}")&&Math.abs(Instant.now().getEpochSecond()-Long.parseLong(timestamp))<=300,"支付通知时间不正确");
   check(nonce!=null&&!nonce.isEmpty()&&nonce.length()<=128&&signature!=null,"支付签名缺失");
   Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(publicKey);verifier.update((timestamp+"\n"+nonce+"\n"+body+"\n").getBytes(StandardCharsets.UTF_8));
   check(verifier.verify(Base64.getDecoder().decode(signature)),"支付签名验证失败");
  }catch(Exception e){throw new IllegalArgumentException("微信支付签名验证失败");}
 }
 public JSONObject notification(String body,String timestamp,String nonce,String signature,String keyId){
  return decryptNotification(body,timestamp,nonce,signature,keyId,false);
 }
 public JSONObject refundNotification(String body,String timestamp,String nonce,String signature,String keyId){
  return decryptNotification(body,timestamp,nonce,signature,keyId,true);
 }
 public String refundNotifyUrl(){return notifyUrl.substring(0,notifyUrl.lastIndexOf('/')+1)+"refund-notify";}
 private JSONObject decryptNotification(String body,String timestamp,String nonce,String signature,String keyId,boolean refund){
  requireReady();verify(timestamp,nonce,signature,keyId,body);
  try {
   JSONObject envelope=JSON.parseObject(body);
   String event=envelope.getString("event_type");
   check((refund?Arrays.asList("REFUND.SUCCESS","REFUND.CLOSED","REFUND.ABNORMAL").contains(event):"TRANSACTION.SUCCESS".equals(event))&&"encrypt-resource".equals(envelope.getString("resource_type")),"支付通知类型不正确");
   JSONObject resource=envelope.getJSONObject("resource");
   check("AEAD_AES_256_GCM".equals(resource.getString("algorithm"))&&(refund?"refund":"transaction").equals(resource.getString("original_type")),"支付通知算法不正确");
   byte[] iv=resource.getString("nonce").getBytes(StandardCharsets.UTF_8);check(iv.length==12,"支付通知随机数不正确");
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8),"AES"),new GCMParameterSpec(128,iv));
   cipher.updateAAD(str(resource.get("associated_data")).getBytes(StandardCharsets.UTF_8));
   JSONObject decrypted=JSON.parseObject(new String(cipher.doFinal(Base64.getDecoder().decode(resource.getString("ciphertext"))),StandardCharsets.UTF_8));
   if(refund)check(event.equals("REFUND."+decrypted.getString("refund_status")),"退款通知状态不匹配");
   return decrypted;
  }catch(Exception e){throw new IllegalArgumentException("微信支付通知解密失败");}
 }
 public JSONObject paymentParameters(String prepayId){
  requireReady();String timestamp=Long.toString(Instant.now().getEpochSecond()),nonce=nonce(),pack="prepay_id="+prepayId;
  try{JSONObject out=new JSONObject();out.put("timeStamp",timestamp);out.put("nonceStr",nonce);out.put("package",pack);out.put("signType","RSA");out.put("paySign",sign(appId+"\n"+timestamp+"\n"+nonce+"\n"+pack+"\n"));return out;}catch(Exception e){throw new IllegalArgumentException("支付参数生成失败");}
 }
 static class ApiException extends IllegalArgumentException {
  final String code;
  ApiException(String code){super("微信支付请求未完成（"+code+"），请稍后重试或联系商家");this.code=code;}
 }
 public JSONObject call(String method,String path,JSONObject payload){
  requireReady();check(path.startsWith("/v3/")&&!path.contains("\n")&&!path.contains("\r"),"支付路径不正确");
  String body=payload==null?"":payload.toJSONString(),time=Long.toString(Instant.now().getEpochSecond()),nonce=nonce();HttpURLConnection c=null;
  try {
   c=(HttpURLConnection)new URL("https://api.mch.weixin.qq.com"+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
   c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","MeirShop-WeChatPay/1.0");c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Wechatpay-Serial",publicKeyId);
   c.setRequestProperty("Authorization","WECHATPAY2-SHA256-RSA2048 mchid=\""+merchantId+"\",nonce_str=\""+nonce+"\",timestamp=\""+time+"\",serial_no=\""+serial+"\",signature=\""+sign(canonical(method,path,time,nonce,body))+"\"");
   if(payload!=null){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
   int status=c.getResponseCode();String response=read(status>=400?c.getErrorStream():c.getInputStream());
   // Verify even error/empty replies before using their codes to change payment state.
   verify(c.getHeaderField("Wechatpay-Timestamp"),c.getHeaderField("Wechatpay-Nonce"),c.getHeaderField("Wechatpay-Signature"),c.getHeaderField("Wechatpay-Serial"),response);
   JSONObject result=response.isEmpty()?new JSONObject():JSON.parseObject(response);
   if(status<200||status>=300){String code=result.getString("code");throw new ApiException(code!=null&&code.matches("[A-Z_]{1,80}")?code:"API_ERROR");}return result;
  }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("微信支付结果暂未确认，请查询订单后重试");}finally{if(c!=null)c.disconnect();}
 }
 private static String read(InputStream stream)throws IOException {
  if(stream==null)return "";try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){check(out.size()+n<=262144,"支付响应过大");out.write(buf,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}
 }
 public JSONObject query(String outTradeNo){return call("GET","/v3/pay/transactions/out-trade-no/"+outTradeNo+"?mchid="+merchantId,null);}
 public void validateTransaction(JSONObject t,Map<String,Object> payment){
  check("SUCCESS".equals(t.getString("trade_state"))&&"JSAPI".equals(t.getString("trade_type")),"支付状态不正确");
  check(merchantId.equals(t.getString("mchid"))&&appId.equals(t.getString("appid")),"支付商户或应用不匹配");
  check(str(payment.get("out_trade_no")).equals(t.getString("out_trade_no")),"支付订单不匹配");
  JSONObject amount=t.getJSONObject("amount"),payer=t.getJSONObject("payer");
  check(amount!=null&&"CNY".equals(amount.getString("currency"))&&amount.getLongValue("total")==number(payment.get("amount")),"支付金额不匹配");
  check(payer!=null&&str(payment.get("payer_openid")).equals(payer.getString("openid")),"付款微信不匹配");
  check(str(t.get("transaction_id")).matches("[0-9]{20,64}"),"支付交易号不正确");
 }
}
