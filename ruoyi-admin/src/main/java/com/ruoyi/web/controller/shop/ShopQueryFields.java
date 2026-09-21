package com.ruoyi.web.controller.shop;
/** Customer facets are independent of the order channel; enterprise and distributor may overlap. */
public final class ShopQueryFields {
 private ShopQueryFields() {}
 public static String enterprise(String id){return "exists(select 1 from shop_member qm where qm.id="+id+" and (qm.enterprise_state='approved' or exists(select 1 from shop_company_member qcm where qcm.member_id=qm.id and qcm.enabled=1)))";}
 public static String fields(String id){return ", "+enterprise(id)+" query_enterprise, exists(select 1 from shop_member qm where qm.id="+id+" and qm.distributor_state='approved') query_distributor, (select coalesce(qc.name,'') from shop_company_member qcm join shop_company qc on qc.id=qcm.company_id where qcm.member_id="+id+" limit 1) query_company, (select qm.nickname from shop_member qm where qm.id="+id+") query_customer";}
 public static String predicate(String id,String identity){if("personal".equals(identity))return " not "+enterprise(id);if("enterprise".equals(identity))return enterprise(id);if("distributor".equals(identity))return "exists(select 1 from shop_member qm where qm.id="+id+" and qm.distributor_state='approved')";if(!"all".equals(identity))throw new IllegalArgumentException("请选择有效客户身份");return "1=1";}
}
