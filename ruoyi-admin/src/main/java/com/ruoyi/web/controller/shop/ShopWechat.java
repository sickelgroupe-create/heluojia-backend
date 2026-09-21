package com.ruoyi.web.controller.shop;
import java.util.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.*;import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSONObject;import com.alibaba.fastjson2.JSON;import static com.ruoyi.web.controller.shop.ShopService.*;
@Service public class ShopWechat {
 @Autowired ShopService s;@Autowired ShopAccounts accounts;
 @Value("${shop.wechat.app-id:}") String appId;
 @Value("${shop.wechat.app-secret:}") String secret;
 @Value("${shop.wechat.only:false}") boolean only;
 public boolean only(){return only;}
 public boolean ready(){return appId.matches("wx[0-9a-fA-F]{16}")&&secret.matches("[0-9a-fA-F]{32}");}
 String identity(String code){
  check(ready(),"微信登录尚未配置完成，请联系商家");check(code!=null&&code.matches("[a-zA-Z0-9_-]{5,256}"),"微信登录凭证不正确，请重新点击登录");
  HttpURLConnection connection=null;
  try{
   URL url=new URL("https://api.weixin.qq.com/sns/jscode2session?appid="+appId+"&secret="+secret+"&js_code="+URLEncoder.encode(code,"UTF-8")+"&grant_type=authorization_code");
   connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);
   check(connection.getResponseCode()==200,"微信服务暂不可用，请稍后重试");ByteArrayOutputStream out=new ByteArrayOutputStream();
   try(InputStream in=connection.getInputStream()){byte[] chunk=new byte[2048];int n;while((n=in.read(chunk))!=-1){check(out.size()+n<=32768,"微信服务返回异常，请稍后重试");out.write(chunk,0,n);}}
   JSONObject data=JSON.parseObject(new String(out.toByteArray(),StandardCharsets.UTF_8));int error=data.getIntValue("errcode");
   if(error==40029||error==40163)throw new IllegalArgumentException("微信登录凭证已过期，请重新点击登录");
   if(error==40013||error==40125)throw new IllegalArgumentException("微信登录配置不正确，请联系商家");
   if(error==45011)throw new IllegalArgumentException("微信登录过于频繁，请稍后重试");
   check(error==0,"微信登录暂不可用，请稍后重试");String openid=data.getString("openid");check(openid!=null&&openid.matches("[a-zA-Z0-9_-]{10,128}"),"微信身份校验失败，请重新登录");
   return "wechat:"+appId+":"+openid;
  }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("连接微信服务失败，请稍后重试");}finally{if(connection!=null)connection.disconnect();}
 }
 void lockIdentity(){s.jdbc().update("insert ignore into shop_config(config_key,content) values('wechat_identity_lock','{}')");s.one("select content from shop_config where config_key='wechat_identity_lock' for update");}
 @Transactional public Map<String,Object> login(JSONObject body){
  check(body.getBooleanValue("agreed"),"请阅读并同意用户协议与隐私说明");String identity=identity(body.getString("code"));lockIdentity();
  if(body.getBooleanValue("bindExisting")){
   Map<String,Object> auth=accounts.login(body);long uid=number(((Map<?,?>)auth.get("member")).get("id"));s.bindIdentity(uid,identity,null);return auth;
  }
  return s.login(identity,"wechat",null,referral(body.get("referrer")));
 }
 @Transactional public void bind(long uid,JSONObject body){check(body.getBooleanValue("agreed"),"请同意绑定当前微信");String identity=identity(body.getString("code"));lockIdentity();s.bindIdentity(uid,identity,null);}
 public boolean bound(long uid){return s.jdbc().queryForObject("select count(*) from shop_identity where member_id=? and identity_key like ?",Long.class,uid,"wechat:"+appId+":%")>0;}
 /** Re-authenticate an existing member without creating, merging or binding any account. */
 public void verifyBoundIdentity(long uid,String code){
  String verified=identity(code);
  check(s.jdbc().queryForObject("select count(*) from shop_identity where member_id=? and identity_key=?",Long.class,uid,verified)==1,"当前微信与负责人账号不一致，请使用本人已绑定的微信验证");
 }
 @Value("${shop.wechat.code-version:trial}") String codeVersion;
 @Value("${ruoyi.profile}") String profilePath;
 private String accessToken="";private long tokenExpires=0;
 private byte[] postWechat(String path,JSONObject body)throws IOException{
  HttpURLConnection c=(HttpURLConnection)new URL("https://api.weixin.qq.com/"+path).openConnection();
  try{c.setConnectTimeout(5000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));}check(c.getResponseCode()==200,"微信推广服务暂不可用，请稍后重试");ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){check(out.size()+n<=2*1024*1024,"微信推广码响应异常");out.write(chunk,0,n);}}return out.toByteArray();}catch(IOException e){throw new IllegalArgumentException("连接微信推广服务失败，请稍后重试");}finally{c.disconnect();}
 }
 private synchronized String stableToken()throws IOException{
  if(System.currentTimeMillis()<tokenExpires&&!accessToken.isEmpty())return accessToken;
  JSONObject body=new JSONObject();body.put("grant_type","client_credential");body.put("appid",appId);body.put("secret",secret);body.put("force_refresh",false);
  JSONObject result=JSON.parseObject(new String(postWechat("cgi-bin/stable_token",body),StandardCharsets.UTF_8));
  check(result.getIntValue("errcode")==0&&result.getString("access_token")!=null,"微信推广码授权失败（"+result.getIntValue("errcode")+"），请在微信后台检查接口权限及服务器IP白名单");
  accessToken=result.getString("access_token");tokenExpires=System.currentTimeMillis()+Math.max(60,result.getIntValue("expires_in")-300)*1000L;return accessToken;
 }
 public synchronized java.awt.image.BufferedImage promotionCode(long uid,long product,String channel,boolean recruit)throws IOException{
  check(ready(),"微信推广码尚未配置");check(Arrays.asList("trial","release","develop").contains(codeVersion),"推广码版本配置不正确");
  String scene="r"+Long.toString(uid,36)+"."+Long.toString(recruit?0:product,36)+"."+channel;check(scene.length()<=32,"推广参数过长");
  java.nio.file.Path directory=java.nio.file.Paths.get(profilePath,"wechat-promo-codes").toAbsolutePath().normalize();java.nio.file.Files.createDirectories(directory);
  java.nio.file.Path file=directory.resolve(s.hash(appId+":"+codeVersion+":"+scene)+".png");
  if(java.nio.file.Files.isRegularFile(file)){java.awt.image.BufferedImage cached=javax.imageio.ImageIO.read(file.toFile());if(cached!=null)return cached;}
  JSONObject body=new JSONObject();body.put("scene",scene);body.put("page",recruit?"pages/distributor-apply/index":"pages/product/index");body.put("check_path","release".equals(codeVersion));body.put("env_version",codeVersion);body.put("width",430);
  byte[] bytes=postWechat("wxa/getwxacodeunlimit?access_token="+URLEncoder.encode(stableToken(),"UTF-8"),body);
  java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
  if(image==null){JSONObject error=JSON.parseObject(new String(bytes,StandardCharsets.UTF_8));int code=error.getIntValue("errcode");if(code==40001||code==42001)tokenExpires=0;throw new IllegalArgumentException("微信推广码生成失败（"+code+"），请检查微信发布版本和接口权限");}
  javax.imageio.ImageIO.write(image,"png",file.toFile());return image;
 }
}
