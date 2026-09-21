package com.ruoyi.web.controller.shop;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import static com.ruoyi.web.controller.shop.ShopService.*;
@RestController
public class ShopFilesController {
 @Autowired ShopService s;
 @Value("${shop.asset-path:../runtime/assets}") String assets;
 @Value("${shop.private-path:../runtime/private}") String privatePath;
 private final java.util.Map<String,byte[]> thumbnails=new java.util.concurrent.ConcurrentHashMap<>();
 @Anonymous @GetMapping("/shop-assets/{name}") public ResponseEntity<byte[]> asset(@PathVariable String name,@RequestParam(defaultValue="0") int width)throws IOException {
  check(name.matches("[a-zA-Z0-9_-]+\\.(png|jpg|webp)"),"图片不存在");Path p=Paths.get(assets).resolve(name);if(!Files.exists(p))return ResponseEntity.notFound().build();
  check(width==0||width==160||width==320||width==640||width==960,"图片尺寸不正确");
  if(width>0&&!name.endsWith("webp")){
   String key=name+":"+width+":"+Files.getLastModifiedTime(p).toMillis();byte[] bytes=thumbnails.get(key);
   if(bytes==null){java.awt.image.BufferedImage source=ImageIO.read(p.toFile());check(source!=null,"图片无法读取");int w=Math.min(width,source.getWidth()),h=Math.max(1,(int)((long)source.getHeight()*w/source.getWidth()));java.awt.image.BufferedImage target=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=target.createGraphics();try{g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,w,h);g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(source,0,0,w,h,null);}finally{g.dispose();}ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(target,"jpg",out);bytes=out.toByteArray();if(thumbnails.size()>32)thumbnails.clear();thumbnails.put(key,bytes);}
   return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1,java.util.concurrent.TimeUnit.HOURS)).contentType(MediaType.IMAGE_JPEG).body(bytes);
  }
  return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1,java.util.concurrent.TimeUnit.HOURS)).contentType(MediaType.parseMediaType(name.endsWith("webp")?"image/webp":name.endsWith("jpg")?"image/jpeg":"image/png")).body(Files.readAllBytes(p));
 }
 @Anonymous @PostMapping({"/shop/app/license","/shop/app/transfer-proof","/shop/app/case-proof"}) public AjaxResult upload(HttpServletRequest r,@RequestParam("file")MultipartFile file)throws IOException {long uid=number(s.member(r.getHeader("X-Shop-Token")).get("id"));return privateUpload(uid,file);}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:customer,shop:finance')") @PostMapping("/shop/admin/assistance/proof") public AjaxResult assistanceProof(@RequestParam("orderNo")String no,@RequestParam("file")MultipartFile file)throws IOException {java.util.Map<String,Object> o=s.one("select o.id,o.member_id from shop_order o join shop_member m on m.id=o.member_id where o.order_no=? and m.enabled=0",no);AjaxResult result=privateUpload(number(o.get("member_id")),file);s.event("order",number(o.get("id")),"客服代收受限凭证",com.ruoyi.common.utils.SecurityUtils.getUsername());return result;}
 private AjaxResult privateUpload(long uid,MultipartFile file)throws IOException {check(file.getSize()>0&&file.getSize()<=5*1024*1024,"图片应小于5MB");byte[] bytes=file.getBytes();java.awt.image.BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));check(image!=null&&image.getWidth()<=8000&&image.getHeight()<=8000,"请上传有效的JPG或PNG图片");Path dir=Paths.get(privatePath);Files.createDirectories(dir);String name=UUID.randomUUID().toString()+".png";ImageIO.write(image,"png",dir.resolve(name).toFile());String uri="/shop/admin/license/"+name;s.jdbc().update("insert into shop_private_file(path,member_id) values(?,?)",uri,uid);return AjaxResult.success((Object)uri);}
 @Anonymous @PostMapping("/shop/app/public-image") public AjaxResult publicImage(HttpServletRequest r,@RequestParam("file")MultipartFile file)throws IOException {long uid=number(s.member(r.getHeader("X-Shop-Token")).get("id"));check(file.getSize()>0&&file.getSize()<=5*1024*1024,"图片应小于5MB");java.awt.image.BufferedImage image=ImageIO.read(new ByteArrayInputStream(file.getBytes()));check(image!=null&&image.getWidth()<=8000&&image.getHeight()<=8000,"请上传有效图片");Path dir=Paths.get(assets);Files.createDirectories(dir);String path="/shop-assets/user_"+UUID.randomUUID()+".png";ImageIO.write(image,"png",dir.resolve(path.substring(path.lastIndexOf('/')+1)).toFile());s.jdbc().update("insert into shop_public_file(path,member_id) values(?,?)",path,uid);return AjaxResult.success((Object)path);}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:customer')") @GetMapping("/shop/admin/license/{name}") public ResponseEntity<byte[]> license(@PathVariable String name)throws IOException {check(name.matches("[a-f0-9-]{36}\\.png"),"资料不存在");Path p=Paths.get(privatePath).resolve(name);if(!Files.exists(p))return ResponseEntity.notFound().build();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(Files.readAllBytes(p));}

 @Anonymous @GetMapping("/shop/app/cases/{id}/proofs/{index}") public ResponseEntity<byte[]> ownProof(HttpServletRequest r,@PathVariable long id,@PathVariable int index)throws IOException{java.util.Map<String,Object> c=s.companyCase(number(s.member(r.getHeader("X-Shop-Token")).get("id")),id,"view",false);return proof(c,index);}
 @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:customer,shop:finance')") @GetMapping("/shop/admin/cases/{id}/proofs/{index}") public ResponseEntity<byte[]> adminProof(@PathVariable long id,@PathVariable int index)throws IOException{return proof(s.one("select proofs_json from shop_case where id=?",id),index);}
 private ResponseEntity<byte[]> proof(java.util.Map<String,Object> c,int index)throws IOException{com.alibaba.fastjson2.JSONArray proofs=com.alibaba.fastjson2.JSON.parseArray(str(c.get("proofs_json")).isEmpty()?"[]":str(c.get("proofs_json")));check(index>=0&&index<proofs.size(),"凭证不存在");String uri=proofs.getString(index),name=uri.substring(uri.lastIndexOf('/')+1);check(name.matches("[a-f0-9-]{36}\\.png"),"凭证不存在");Path path=Paths.get(privatePath).resolve(name);if(!Files.exists(path))return ResponseEntity.notFound().build();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(Files.readAllBytes(path));}
}
