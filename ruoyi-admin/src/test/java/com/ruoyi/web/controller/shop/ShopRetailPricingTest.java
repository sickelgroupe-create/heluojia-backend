package com.ruoyi.web.controller.shop;

import java.util.*;
import com.alibaba.fastjson2.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ShopRetailPricingTest {
 ShopService service;JdbcTemplate db;ShopGrades grades;ShopPartner partner;
 Map<String,Object> row(Object...pairs){Map<String,Object> r=new HashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
 @BeforeEach void setup(){
  service=mock(ShopService.class);db=mock(JdbcTemplate.class);when(service.jdbc()).thenReturn(db);
  grades=new ShopGrades();grades.s=service;partner=new ShopPartner();partner.s=service;
  when(service.one("select content from shop_config where config_key='grades'")).thenReturn(row("content","{\"names\":[\"基础\",\"银卡\",\"金卡\"],\"discounts\":[5000,6000,3000],\"silver\":500000,\"gold\":2000000}"));
  when(service.one("select content from shop_config where config_key='partner'")).thenReturn(row("content","{\"tiers\":[{\"rate\":3800,\"referenceAmount\":1980000},{\"rate\":2800,\"referenceAmount\":4980000},{\"rate\":2200}],\"excludedProducts\":[]}"));
 }
 @Test void sevenRetailPricesAreIndependentOfBuyerAndOldDiscounts(){
  for(long price:new long[]{31800,32800,26800,34800,39800,23800,39800,1})for(long uid:new long[]{0,1,2,99})assertEquals(price,grades.retail(row("price",price),uid));
  verifyNoInteractions(db);
 }
 @Test void guestAndAllMemberCatalogsHaveIdenticalRetailPrices(){
  when(service.products()).thenAnswer(call->new ArrayList<>(Arrays.asList(row("id",1003L,"price",26800L,"wholesale",1234L,"pricing_json","{}"))));
  for(long uid:new long[]{0,1,2,99}){Map<String,Object> p=grades.catalog(uid,0).get(0);assertEquals(26800L,p.get("price"));assertEquals(26800L,p.get("standardPrice"));assertFalse(p.containsKey("wholesale"));}
  assertEquals(JSONArray.of(10000,10000,10000),grades.settings().getJSONArray("discounts"));
 }
 JSONObject profile(String state,String mode,int tier,long spend){
  when(service.one(startsWith("select m.distributor_state"),any(),any(),any())).thenReturn(row("distributor_state",state,"mode",mode,"override_tier",tier,"net_spend",spend,"account_enabled",1));return partner.profile(1);
 }
 @Test void spendAndManualTierNeverBypassDistributorApproval(){
  for(String state:new String[]{"none","pending","rejected"})for(String mode:new String[]{"auto","fixed"}){JSONObject p=profile(state,mode,3,99999999);assertFalse(p.getBooleanValue("enabled"));assertEquals(0,p.getIntValue("tier"));assertThrows(IllegalArgumentException.class,()->partner.require(1));}
 }
 @Test void approvedDistributorUsesIndependentTierPrices(){
  JSONObject p=profile("approved","auto",0,0);assertTrue(p.getBooleanValue("enabled"));assertEquals(1,p.getIntValue("tier"));assertEquals(10184,partner.price(row("id",1003L,"price",26800),p));
  p=profile("approved","auto",0,4980000);assertEquals(2,p.getIntValue("tier"));assertEquals(7504,partner.price(row("id",1003L,"price",26800),p));
  p=profile("approved","fixed",3,0);assertEquals(3,p.getIntValue("tier"));assertEquals(5896,partner.price(row("id",1003L,"price",26800),p));
  assertFalse(profile("approved","blocked",0,99999999).getBooleanValue("enabled"));
 }
 @Test void approvalReplacesOldFirstThresholdAndManualModeHasNoAutomaticProgress(){
  JSONObject fresh=profile("approved","auto",0,0);assertEquals(1,fresh.getIntValue("tier"));assertEquals(0,fresh.getJSONArray("thresholds").getLongValue(0));assertEquals(4980000,fresh.getLongValue("remainingAmount"));
  JSONObject manual=profile("approved","fixed",1,6000000);assertEquals(1,manual.getIntValue("tier"));assertEquals(2,manual.getIntValue("automaticTier"));assertFalse(manual.containsKey("remainingAmount"));assertFalse(manual.containsKey("nextAmount"));
  assertFalse(profile("approved","blocked",0,0).containsKey("remainingAmount"));
  JSONObject boundary=profile("approved","auto",0,4979999);assertEquals(1,boundary.getIntValue("tier"));assertEquals(1,boundary.getLongValue("remainingAmount"));assertEquals(2,profile("approved","auto",0,4980000).getIntValue("tier"));
 }
 @Test void partnerCatalogDoesNotOverwriteRetailPrice(){
  profile("approved","auto",0,0);Map<String,Object> product=row("id",1003L,"price",26800L,"standardPrice",26800L,"retail_enabled",1);partner.catalogPrices(1,Arrays.asList(product));assertEquals(26800L,product.get("price"));assertEquals(10184L,product.get("partnerPrice"));
 }
}
