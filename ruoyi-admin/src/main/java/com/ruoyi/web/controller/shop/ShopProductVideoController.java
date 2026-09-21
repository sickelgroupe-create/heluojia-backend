package com.ruoyi.web.controller.shop;

import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;
import javax.servlet.http.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import static com.ruoyi.web.controller.shop.ShopService.*;

@RestController
public class ShopProductVideoController {
 @Autowired ShopService s;
 @Value("${shop.asset-path:../runtime/assets}") String assets;
 @PostMapping("/shop/admin/products/video") @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:catalog')")
 public AjaxResult upload(@RequestParam("file")MultipartFile file)throws IOException {
  check(file.getSize()>0&&file.getSize()<=ShopProductVideo.MAX_BYTES,"视频需大于0且不超过10 MiB");
  check(str(file.getOriginalFilename()).toLowerCase(Locale.ROOT).endsWith(".mp4"),"仅支持MP4/H.264视频");
  String mime=str(file.getContentType());check(mime.isEmpty()||"video/mp4".equalsIgnoreCase(mime)||"application/octet-stream".equalsIgnoreCase(mime),"仅支持MP4/H.264视频");
  byte[] bytes=file.getBytes();ShopProductVideo.validate(bytes);Path dir=Paths.get(assets).resolve("videos");Files.createDirectories(dir);String name=UUID.randomUUID()+".mp4",uri="/shop/videos/"+name;Path path=dir.resolve(name);
  Files.write(path,bytes,StandardOpenOption.CREATE_NEW);try{s.jdbc().update("insert into shop_product_video(path,size) values(?,?)",uri,bytes.length);}catch(RuntimeException e){Files.deleteIfExists(path);throw e;}
  return AjaxResult.success((Object)uri);
 }
 @Anonymous @GetMapping("/shop/videos/{name}")
 public void video(@PathVariable String name,HttpServletRequest request,HttpServletResponse response)throws IOException {
  if(!name.matches("[a-f0-9-]{36}\\.mp4")||s.jdbc().queryForObject("select count(*) from shop_product_video where path=?",Long.class,"/shop/videos/"+name)!=1){response.setStatus(404);return;}
  Path path=Paths.get(assets).resolve("videos").resolve(name);if(!Files.isRegularFile(path)){response.setStatus(404);return;}long size=Files.size(path),start=0,end=size-1;String etag="\""+name+"\"",range=request.getHeader("Range");response.setHeader("Accept-Ranges","bytes");response.setHeader("ETag",etag);response.setHeader("Cache-Control","public, max-age=3600");response.setHeader("X-Content-Type-Options","nosniff");response.setContentType("video/mp4");
  if(etag.equals(request.getHeader("If-None-Match"))&&range==null){response.setStatus(304);return;}
  if(request.getHeader("If-Range")!=null&&!etag.equals(request.getHeader("If-Range")))range=null;
  if(range!=null){try{check(range.matches("bytes=([0-9]+-[0-9]*|-[0-9]+)"),"无效范围");String[] span=range.substring(6).split("-",-1);if(span[0].isEmpty()){long suffix=Long.parseLong(span[1]);check(suffix>0,"无效范围");start=Math.max(0,size-suffix);}else{start=Long.parseLong(span[0]);if(!span[1].isEmpty())end=Math.min(end,Long.parseLong(span[1]));}check(start>=0&&start<size&&end>=start,"无效范围");}catch(IllegalArgumentException invalid){response.setStatus(416);response.setHeader("Content-Range","bytes */"+size);return;}response.setStatus(206);response.setHeader("Content-Range","bytes "+start+"-"+end+"/"+size);}
  response.setContentLengthLong(end-start+1);if("HEAD".equals(request.getMethod()))return;
  try(RandomAccessFile in=new RandomAccessFile(path.toFile(),"r")){in.seek(start);byte[] buffer=new byte[65536];long remaining=end-start+1;OutputStream out=response.getOutputStream();while(remaining>0){int n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new EOFException("Video changed during transfer");out.write(buffer,0,n);remaining-=n;}}
 }
}
