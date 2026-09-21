package com.ruoyi.web.controller.shop;
import java.util.*;
import java.security.SecureRandom;
import com.alibaba.fastjson2.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ruoyi.web.controller.shop.ShopService.*;

@Service
public class ShopCompany {
 @Autowired @Lazy ShopService s;
 @Autowired @Lazy ShopWechat wechat;
 private boolean yes(Object v){return Boolean.TRUE.equals(v)||"1".equals(str(v));}
 @Transactional public Map<String,Object> context(long uid){
  s.one("select id from shop_member where id=? for update",uid);
  List<Map<String,Object>> members=s.jdbc().queryForList("select * from shop_company_member where member_id=?",uid);
  if(members.isEmpty()){
   Map<String,Object> m=s.one("select * from shop_member where id=?",uid);
   check("approved".equals(m.get("enterprise_state")),"请先通过企业认证，或接受企业负责人的邀请");
   s.jdbc().update("insert ignore into shop_company(owner_id,certified_member_id,name) values(?,?,?)",uid,uid,JSON.parseObject(str(m.get("enterprise_data")).isEmpty()?"{}":str(m.get("enterprise_data"))).getOrDefault("company","我的企业"));
   long company=number(s.one("select id from shop_company where owner_id=?",uid).get("id"));
   s.inheritCompanyReferral(company,uid);s.jdbc().update("insert into shop_company_member(member_id,company_id,role) values(?,?,'owner')",uid,company);s.jdbc().update("update shop_order set company_id=? where channel='B' and member_id=? and company_id is null",company,uid);
  }
  Map<String,Object> c=s.one("select c.*,m.role,m.enabled member_enabled from shop_company c join shop_company_member m on m.company_id=c.id where m.member_id=?",uid);
  check(yes(c.get("member_enabled")),"企业采购权限已暂停，请联系负责人");
  Map<String,Object> owner=s.one("select m.enabled,certified.enterprise_state from shop_member m join shop_member certified on certified.id=? where m.id=?",c.get("certified_member_id"),c.get("owner_id"));
  check(yes(owner.get("enabled"))&&"approved".equals(owner.get("enterprise_state")),"企业采购资格已暂停，历史业务请联系负责人或客服处理");
  return c;
 }
 public String orderScope(long uid){return "((o.channel='C' and o.member_id="+uid+") or (o.channel='B' and o.company_id is null and o.member_id="+uid+") or (o.channel='B' and exists(select 1 from shop_company_member cm where cm.company_id=o.company_id and cm.member_id="+uid+" and cm.enabled=1 and (cm.role in ('owner','finance') or o.member_id="+uid+"))))";}
 public void authorize(long uid,Map<String,Object> o,String action){
  if(!"B".equals(o.get("channel"))||o.get("company_id")==null){check(number(o.get("member_id"))==uid,"订单不属于当前账号");return;}
  Map<String,Object> m=s.one("select role from shop_company_member where company_id=? and member_id=? and enabled=1",o.get("company_id"),uid);String role=str(m.get("role"));
  if("pay".equals(action)||"invoice".equals(action))check(Arrays.asList("owner","finance").contains(role),"请由企业负责人或财务完成付款");
  else if("cancel".equals(action)||"receive".equals(action)||"afterSale".equals(action))check("owner".equals(role)||number(o.get("member_id"))==uid,"请由采购员或负责人处理该订单");
  else check(Arrays.asList("owner","finance").contains(role)||number(o.get("member_id"))==uid,"没有此订单的查看权限");
 }
 @Transactional public void purchase(long uid){check(!"finance".equals(context(uid).get("role")),"财务账号可付款和对账，采购请交给采购员或负责人");}
 @Transactional public Map<String,Object> historyContext(long uid){List<Map<String,Object>> rows=s.jdbc().queryForList("select c.*,m.role,m.enabled member_enabled from shop_company c join shop_company_member m on m.company_id=c.id where m.member_id=?",uid);if(rows.isEmpty())return context(uid);Map<String,Object> c=rows.get(0);check(yes(c.get("member_enabled")),"企业权限已暂停，请联系负责人处理历史业务");return c;}
 @Transactional public Map<String,Object> overview(long uid){
  Map<String,Object> c=historyContext(uid);c.put("viewer_id",uid);boolean owner=number(c.get("owner_id"))==uid;
  if(owner)c.put("handoverVerification",s.jdbc().queryForObject("select count(*) from shop_credentials where member_id=?",Long.class,uid)>0?"password":"wechat");
  c.put("members",owner?s.jdbc().queryForList("select m.member_id,m.role,m.enabled,a.nickname from shop_company_member m join shop_member a on a.id=m.member_id where company_id=? order by m.joined_at",c.get("id")):Collections.emptyList());
  c.put("drafts",s.jdbc().queryForList("select id,member_id,amount,status,note,order_id,created_at,payload_json from shop_procurement_draft where company_id=?"+(owner?"":" and member_id="+uid)+" order by id desc limit 100",c.get("id")));
  for(Object value:(List<?>)c.get("drafts")){Map<String,Object> draft=(Map<String,Object>)value;JSONObject payload=JSON.parseObject(str(draft.remove("payload_json")));draft.put("items",payload.containsKey("_approvalQuote")?payload.get("_approvalQuote"):payload.get("items"));draft.put("address",payload.get("address"));}
  c.put("handover",s.jdbc().queryForList("select h.*,m.nickname target_name from shop_company_handover h join shop_member m on m.id=h.to_member where h.company_id=? and h.status='pending' and h.expires_at>now() and (h.to_member=? or h.from_member=?)",c.get("id"),uid,uid));
  return c;
 }
 private Map<String,Object> owner(long uid){Map<String,Object> c=context(uid);check(number(c.get("owner_id"))==uid,"仅企业负责人可以操作");Map<String,Object> current=s.one("select owner_id from shop_company where id=? for update",c.get("id"));check(number(current.get("owner_id"))==uid,"负责人已变化，请刷新后重试");return c;}
 @Transactional public void settings(long uid,JSONObject b){Map<String,Object> c=owner(uid);String name=str(b.get("name")).trim();check(!name.isEmpty()&&name.length()<=100,"请填写100字内企业名称");s.jdbc().update("update shop_company set name=?,approval_required=? where id=?",name,b.getBooleanValue("approvalRequired"),c.get("id"));s.event("company",number(c.get("id")),"修改企业采购设置",str(uid));}
 @Transactional public String invite(long uid,JSONObject b){Map<String,Object> c=owner(uid);String role=b.getString("role");check(Arrays.asList("buyer","finance").contains(role),"请选择采购员或财务");byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);s.jdbc().update("insert into shop_company_invite(token_hash,company_id,role,expires_at) values(?,?,?,date_add(now(),interval 7 day))",s.hash(token),c.get("id"),role);s.event("company",number(c.get("id")),"生成7天有效的员工邀请",str(uid));return token;}
 @Transactional public void accept(long uid,JSONObject b){
  s.one("select id from shop_member where id=? for update",uid);
  Map<String,Object> i=s.one("select * from shop_company_invite where token_hash=? and expires_at>now() for update",s.hash(str(b.get("code"))));
  if(i.get("accepted_by")!=null){check(number(i.get("accepted_by"))==uid,"邀请已被使用");return;}
  check(s.jdbc().queryForObject("select count(*) from shop_company_member where member_id=?",Long.class,uid)==0,"账号已经归属一个企业，不能重复加入");
  Map<String,Object> c=s.one("select * from shop_company where id=?",i.get("company_id"));
  Map<String,Object> m=s.one("select owner.enabled,certified.enterprise_state from shop_member owner join shop_member certified on certified.id=? where owner.id=?",c.get("certified_member_id"),c.get("owner_id"));check(yes(m.get("enabled"))&&"approved".equals(m.get("enterprise_state")),"邀请企业暂不可用");
  s.jdbc().update("insert into shop_company_member(member_id,company_id,role) values(?,?,?)",uid,i.get("company_id"),i.get("role"));s.jdbc().update("update shop_company_invite set accepted_by=? where id=?",uid,i.get("id"));s.event("company",number(i.get("company_id")),"员工接受邀请",str(uid));
 }
 @Transactional public void member(long uid,long target,JSONObject b){Map<String,Object> c=owner(uid);check(target!=uid,"不能暂停负责人本人");String role=b.getString("role");check(Arrays.asList("buyer","finance").contains(role),"请选择采购员或财务");check(s.jdbc().update("update shop_company_member set role=?,enabled=? where company_id=? and member_id=?",role,b.getBooleanValue("enabled"),c.get("id"),target)==1,"员工不属于本企业");s.event("company",number(c.get("id")),"调整员工权限 "+target,str(uid));}
 @Transactional public Map<String,Object> prepare(long uid,JSONObject b,Map<String,Object> q){
  if(!"B".equals(b.getString("channel")))return null;
  purchase(uid);Map<String,Object> c=context(uid);long draftId=b.getLongValue("approvalId");
  if(draftId>0){Map<String,Object> d=s.one("select * from shop_procurement_draft where id=? and member_id=? and company_id=? for update",draftId,uid,c.get("id"));check("approved".equals(d.get("status")),"采购申请尚未批准或已使用");JSONObject saved=JSON.parseObject(str(d.get("payload_json")));for(String key:Arrays.asList("items","address","useBalance","usePoints","couponId","coupon","paymentMethod","useRebate","deliveryMethod","pickupPoint","campaignId","groupId","bundles"))check(Objects.equals(saved.get(key),b.get(key)),"采购内容改变，请重新提交审批");return null;}
  if(!yes(c.get("approval_required"))||number(c.get("owner_id"))==uid)return null;
  b.put("_approvalQuote",q.get("items"));int created=s.jdbc().update("insert ignore into shop_procurement_draft(company_id,member_id,request_key,payload_json,amount) values(?,?,?,?,?)",c.get("id"),uid,b.getString("requestKey"),b.toJSONString(),q.get("amount"));
  Map<String,Object> d=s.one("select * from shop_procurement_draft where member_id=? and request_key=?",uid,b.getString("requestKey"));if(created==1)s.event("procurement",number(d.get("id")),"submitted",str(uid));Map<String,Object> r=new HashMap<>();r.put("approvalRequired",true);r.put("id",d.get("id"));return r;
 }
 @Transactional public void decide(long uid,long id,JSONObject b){Map<String,Object> c=owner(uid);String action=b.getString("action");check(Arrays.asList("approved","rejected").contains(action),"请选择同意采购或退回修改");String note=str(b.get("note"));check(note.length()<=300&&(!"rejected".equals(action)||!note.trim().isEmpty()),"退回时请填写300字内原因");Map<String,Object>d=s.one("select * from shop_procurement_draft where id=? and company_id=? for update",id,c.get("id"));if(action.equals(d.get("status")))return;check("pending".equals(d.get("status")),"该申请已处理");s.jdbc().update("update shop_procurement_draft set status=?,note=? where id=?",action,note,id);s.event("procurement",id,action,str(uid));}
 @Transactional public Map<String,Object> requote(long uid,long id){Map<String,Object> c=context(uid);Map<String,Object>d=s.one("select * from shop_procurement_draft where id=? and member_id=? and company_id=?",id,uid,c.get("id"));check("approved".equals(d.get("status")),"采购申请尚未批准");JSONObject b=JSON.parseObject(str(d.get("payload_json")));return s.quote(s.one("select * from shop_member where id=?",uid),b,false);}
 @Transactional public Map<String,Object> submit(long uid,long id,JSONObject confirm){
  context(uid);s.one("select id from shop_member where id=? for update",uid);Map<String,Object>d=s.one("select * from shop_procurement_draft where id=? and member_id=? for update",id,uid);
  if(d.get("order_id")!=null)return s.one("select * from shop_order where id=?",d.get("order_id"));
  check("approved".equals(d.get("status")),"采购申请尚未批准");JSONObject b=JSON.parseObject(str(d.get("payload_json")));b.put("approvalId",id);for(String k:Arrays.asList("expectedAmount","expectedWallet","expectedPoints","expectedRebate"))b.put(k,confirm.get(k));
  Map<String,Object> result=s.createOrder(s.one("select * from shop_member where id=?",uid),b);return result;
 }
 @Transactional public void handover(long uid,JSONObject b){Map<String,Object> c=owner(uid);long target=integer(b.get("memberId"),1,Long.MAX_VALUE,"请选择新负责人");check(target!=uid,"请选择其他员工");s.one("select member_id from shop_company_member where company_id=? and member_id=? and enabled=1",c.get("id"),target);List<Map<String,Object>> credentials=s.jdbc().queryForList("select password_hash from shop_credentials where member_id=?",uid);if(credentials.isEmpty())wechat.verifyBoundIdentity(uid,b.getString("code"));else check(com.ruoyi.common.utils.SecurityUtils.matchesPassword(str(b.get("password")),str(credentials.get(0).get("password_hash"))),"当前密码不正确");s.jdbc().update("insert into shop_company_handover(company_id,from_member,to_member,status,expires_at) values(?,?,?,'pending',date_add(now(),interval 7 day)) on duplicate key update from_member=values(from_member),to_member=values(to_member),status='pending',expires_at=values(expires_at)",c.get("id"),uid,target);s.event("company",number(c.get("id")),"发起负责人转交，等待新负责人接受",str(uid));}
 @Transactional public void handoverAnswer(long uid,JSONObject b){Map<String,Object> c=context(uid);s.one("select id from shop_company where id=? for update",c.get("id"));Map<String,Object> h=s.one("select * from shop_company_handover where company_id=? for update",c.get("id"));String action=b.getString("action");check(Arrays.asList("accept","cancel").contains(action),"请选择接受或取消转交");if("cancel".equals(action)){check(number(h.get("from_member"))==uid||number(h.get("to_member"))==uid,"无权处理该转交");check("pending".equals(h.get("status")),"转交已处理");s.jdbc().update("update shop_company_handover set status='cancelled' where company_id=?",c.get("id"));return;}check(number(h.get("to_member"))==uid,"只能由指定的新负责人接受");if("accepted".equals(h.get("status"))&&number(c.get("owner_id"))==uid)return;check("pending".equals(h.get("status"))&&timestamp(h.get("expires_at")).getTime()>System.currentTimeMillis(),"转交邀请已失效");Map<String,Object> current=s.one("select owner_id from shop_company where id=?",c.get("id"));check(number(current.get("owner_id"))==number(h.get("from_member")),"企业负责人已变化，请重新发起");s.jdbc().update("update shop_company_member set role='buyer' where company_id=? and member_id=?",c.get("id"),h.get("from_member"));s.jdbc().update("update shop_company_member set role='owner' where company_id=? and member_id=?",c.get("id"),uid);s.jdbc().update("update shop_company set owner_id=? where id=?",uid,c.get("id"));s.jdbc().update("update shop_company_handover set status='accepted' where company_id=?",c.get("id"));s.event("company",number(c.get("id")),"新负责人接受转交，企业订单与认证资料保持归属",str(uid));}
 public void attached(long uid,long oid,JSONObject b){if(!"B".equals(b.getString("channel")))return;Map<String,Object> c=context(uid);s.jdbc().update("update shop_order set company_id=? where id=?",c.get("id"),oid);long draftId=b.getLongValue("approvalId");if(draftId>0&&s.jdbc().update("update shop_procurement_draft set status='ordered',order_id=? where id=? and status='approved'",oid,draftId)==1)s.event("procurement",draftId,"ordered",str(uid));}
}
