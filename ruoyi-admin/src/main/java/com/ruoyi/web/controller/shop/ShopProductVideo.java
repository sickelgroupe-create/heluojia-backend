package com.ruoyi.web.controller.shop;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.ruoyi.web.controller.shop.ShopService.check;

/** Bounded ISO-BMFF validation for ordinary, self-contained H.264 product clips. */
final class ShopProductVideo {
 static final int MAX_BYTES=10*1024*1024;
 private final byte[] bytes;
 private static final class Box {
  final String type; final int payload,end;
  Box(String type,int payload,int end){this.type=type;this.payload=payload;this.end=end;}
 }
 private ShopProductVideo(byte[] bytes){this.bytes=bytes;}
 static void validate(byte[] bytes){check(bytes!=null&&bytes.length>0&&bytes.length<=MAX_BYTES,"视频需大于0且不超过10 MiB");new ShopProductVideo(bytes).validate();}
 private long uint(int p){check(p>=0&&p+4<=bytes.length,"MP4文件已损坏");return ((bytes[p]&255L)<<24)|((bytes[p+1]&255L)<<16)|((bytes[p+2]&255L)<<8)|(bytes[p+3]&255L);}
 private int ushort(int p){return ((bytes[p]&255)<<8)|(bytes[p+1]&255);}
 private String type(int p){return new String(bytes,p,4,StandardCharsets.US_ASCII);}
 private List<Box> boxes(int start,int end){List<Box> out=new ArrayList<>();for(int p=start;p<end;){check(end-p>=8,"MP4文件不完整");long size=uint(p);int header=8;if(size==1){check(end-p>=16&&uint(p+8)==0,"视频容器大小无效");size=uint(p+12);header=16;}else if(size==0)size=end-p;check(size>=header&&size<=end-p,"MP4文件不完整");check(out.size()<10000,"视频容器过于复杂");out.add(new Box(type(p+4),p+header,p+(int)size));p+=(int)size;}return out;}
 private Box one(List<Box> boxes,String type){Box found=null;for(Box b:boxes)if(type.equals(b.type)){check(found==null,"视频包含重复的"+type+"结构");found=b;}check(found!=null,"视频缺少"+type+"结构");return found;}
 private List<Box> children(Box box){return boxes(box.payload,box.end);}
 private void validate(){
  List<Box> top=boxes(0,bytes.length);Box ftyp=one(top,"ftyp"),moov=one(top,"moov");
  check(ftyp.payload==8&&ftyp.end-ftyp.payload>=8&&(ftyp.end-ftyp.payload)%4==0,"请上传有效的MP4视频");
  boolean brand=false;for(int p=ftyp.payload;p+4<=ftyp.end;p+=4)if(p!=ftyp.payload+4&&Arrays.asList("isom","iso2","mp41","mp42","avc1","M4V ").contains(type(p)))brand=true;
  check(brand,"请使用标准MP4/H.264格式");List<Box> data=new ArrayList<>();for(Box b:top){check(!"moof".equals(b.type),"请导出完整MP4文件后上传");if("mdat".equals(b.type)&&b.end>b.payload)data.add(b);}check(!data.isEmpty(),"视频没有媒体数据");
  List<Box> movie=children(moov);one(movie,"mvhd");boolean video=false;
  for(Box track:movie)if("trak".equals(track.type)){
   Box media=one(children(track),"mdia");List<Box> mdia=children(media);Box handler=one(mdia,"hdlr");check(handler.end-handler.payload>=12,"视频轨道信息不完整");String kind=type(handler.payload+8);
   if(!"vide".equals(kind))continue;
   video=true;Box table=one(children(one(mdia,"minf")),"stbl");List<Box> sample=children(table);Box description=one(sample,"stsd");check(description.end-description.payload>=8&&uint(description.payload+4)==1,"视频需使用单一H.264编码");List<Box> entries=boxes(description.payload+8,description.end);check(entries.size()==1,"视频编码信息无效");Box avc=entries.get(0);check("avc1".equals(avc.type)&&avc.end-avc.payload>=78,"请将视频转为H.264编码的MP4");int width=ushort(avc.payload+24),height=ushort(avc.payload+26);check(width>0&&height>0&&width<=4096&&height<=4096,"视频分辨率需在4096×4096以内");
   Box config=one(boxes(avc.payload+78,avc.end),"avcC");validateAvc(config);
   Box sizes=one(sample,"stsz");check(sizes.end-sizes.payload>=12,"视频样本信息不完整");long fixed=uint(sizes.payload+4),count=uint(sizes.payload+8);check(count>0&&count<=1000000,"视频没有有效画面");long total=fixed*count;if(fixed==0){check(count*4==sizes.end-sizes.payload-12,"视频样本大小表不完整");for(int p=sizes.payload+12;p<sizes.end;p+=4)total+=uint(p);}long mediaBytes=0;for(Box d:data)mediaBytes+=d.end-d.payload;check(total>0&&total<=mediaBytes,"视频媒体数据不完整");
   Box chunks=null;for(Box b:sample)if("stco".equals(b.type)||"co64".equals(b.type)){check(chunks==null,"视频块偏移表重复");chunks=b;}check(chunks!=null&&chunks.end-chunks.payload>=8,"视频缺少媒体块索引");int step="co64".equals(chunks.type)?8:4;long chunksCount=uint(chunks.payload+4);check(chunksCount>0&&chunksCount*step==chunks.end-chunks.payload-8,"视频媒体块索引不完整");for(int p=chunks.payload+8;p<chunks.end;p+=step){if(step==8)check(uint(p)==0,"视频块偏移无效");long offset=uint(p+step-4);boolean inside=false;for(Box d:data)if(offset>=d.payload&&offset<d.end)inside=true;check(inside,"视频媒体块超出文件范围");}
  }
  check(video,"文件中没有H.264视频轨道");
 }
 private void validateAvc(Box config){int p=config.payload;check(config.end-p>=7&&bytes[p]==1,"视频H.264配置不完整");check((bytes[p+4]&3)==3,"请使用标准H.264四字节视频帧格式");int sps=bytes[p+5]&31;check(sps>0,"视频缺少H.264参数");p+=6;for(int i=0;i<sps;i++){check(p+2<=config.end,"视频参数不完整");int n=ushort(p);p+=2;check(n>0&&p+n<=config.end&&(bytes[p]&31)==7,"视频SPS参数无效");p+=n;}check(p<config.end,"视频缺少PPS参数");int pps=bytes[p++]&255;check(pps>0,"视频缺少PPS参数");for(int i=0;i<pps;i++){check(p+2<=config.end,"视频参数不完整");int n=ushort(p);p+=2;check(n>0&&p+n<=config.end&&(bytes[p]&31)==8,"视频PPS参数无效");p+=n;}}
}
