package com.ruoyi.web.controller.shop;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.ruoyi.web.controller.shop.ShopService.*;

/** Storefront content is isolated from finance and order settings. */
@RestController
public class ShopStorefrontController {
    @Autowired private ShopService shop;
    private static final String KEY = "storefront_layout";
    private static final String DRAFT = "storefront_layout_draft", PREVIOUS = "storefront_layout_previous";

    @Anonymous @GetMapping("/shop/app/storefront")
    public AjaxResult publicLayout() { return AjaxResult.success(read()); }

    @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:content')")
    @GetMapping("/shop/admin/storefront")
    public AjaxResult adminLayout() { return AjaxResult.success(read()); }

    @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:content')")
    @GetMapping("/shop/admin/storefront/editor")
    public AjaxResult editor() {
        JSONObject live=read(),draft=stored(DRAFT),result=new JSONObject();
        result.put("published",live);result.put("draft",draft==null?null:draft.getJSONObject("layout"));
        result.put("draftRevision",draft==null?0:draft.getIntValue("revision"));
        result.put("previousAvailable",stored(PREVIOUS)!=null);return AjaxResult.success(result);
    }

    private JSONObject stored(String key) {
        List<Map<String,Object>> rows=shop.jdbc().queryForList("select content from shop_config where config_key=?",key);
        return rows.isEmpty()?null:JSON.parseObject(str(rows.get(0).get("content")));
    }
    // Writes lock the live key first, then use current reads for the related snapshots.
    // A repeatable-read snapshot created before waiting for the live lock may be stale.
    private JSONObject locked(String key) {
        List<Map<String,Object>> rows=shop.jdbc().queryForList("select content from shop_config where config_key=? for update",key);
        return rows.isEmpty()?null:JSON.parseObject(str(rows.get(0).get("content")));
    }
    private JSONObject lockLive(JSONObject input) {
        JSONObject live=locked(KEY);
        if(live==null){live=defaults();put(KEY,live);}
        check(input.getIntValue("revision")==live.getIntValue("revision"),"首页已被其他人更新，请重新加载后编辑");return live;
    }
    private int draftRevision(JSONObject input) {
        JSONObject draft=locked(DRAFT);int revision=draft==null?0:draft.getIntValue("revision");
        check(input.getIntValue("draftRevision")==revision,"草稿已被其他人更新，请重新加载后编辑");return revision;
    }
    private void put(String key,JSONObject content) { shop.jdbc().update("insert into shop_config(config_key,content) values(?,?) on duplicate key update content=values(content)",key,content.toJSONString()); }
    private void putDraft(JSONObject layout,int revision) { JSONObject draft=new JSONObject();draft.put("layout",layout);draft.put("revision",revision);put(DRAFT,draft); }
    private JSONObject publish(JSONObject layout,JSONObject old) {
        JSONObject clean=clean(layout);clean.put("revision",old.getIntValue("revision")+1);put(PREVIOUS,old);put(KEY,clean);
        JSONObject draft=locked(DRAFT);putDraft(null,(draft==null?0:draft.getIntValue("revision"))+1);return clean;
    }

    @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:content')")
    @PostMapping("/shop/admin/storefront/draft") @Transactional
    public AjaxResult draft(@RequestBody JSONObject input) {
        JSONObject live=lockLive(input);int revision=draftRevision(input);JSONObject layout=clean(input);layout.put("revision",live.getIntValue("revision"));
        putDraft(layout,revision+1);shop.event("storefront",0,"保存首页布局草稿",SecurityUtils.getUsername());
        JSONObject result=new JSONObject();result.put("layout",layout);result.put("draftRevision",revision+1);return AjaxResult.success(result);
    }
    @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:content')")
    @PostMapping("/shop/admin/storefront/publish") @Transactional
    public AjaxResult publishDraft(@RequestBody JSONObject input) {
        JSONObject live=lockLive(input);draftRevision(input);JSONObject stored=locked(DRAFT),layout=stored==null?null:stored.getJSONObject("layout");
        check(layout!=null,"请先保存草稿");check(layout.getIntValue("revision")==live.getIntValue("revision"),"已发布内容发生变化，请重新加载后编辑");
        JSONObject result=publish(layout,live);shop.event("storefront",0,"发布首页布局草稿",SecurityUtils.getUsername());return AjaxResult.success(result);
    }
    @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:content')")
    @PostMapping("/shop/admin/storefront/restore") @Transactional
    public AjaxResult restore(@RequestBody JSONObject input) {
        JSONObject live=lockLive(input);draftRevision(input);JSONObject previous=locked(PREVIOUS);check(previous!=null,"没有可恢复的上次发布内容");
        JSONObject result=publish(previous,live);shop.event("storefront",0,"恢复上次发布的首页布局",SecurityUtils.getUsername());return AjaxResult.success(result);
    }

    private JSONObject read() {
        List<Map<String,Object>> rows = shop.jdbc().queryForList("select content from shop_config where config_key=?", KEY);
        return rows.isEmpty() ? defaults() : JSON.parseObject(str(rows.get(0).get("content")));
    }

    private JSONObject module(String title) {
        JSONObject o = new JSONObject(); o.put("enabled", true); o.put("title", title); return o;
    }

    private JSONObject defaults() {
        JSONObject result = new JSONObject(); result.put("revision", 0);
        Map<String,Map<String,Object>> media = new HashMap<>();
        for (Map<String,Object> row : shop.publishedMedia("C")) media.put(str(row.get("slot")), row);
        for (String key : Arrays.asList("hero", "story")) {
            JSONObject section = module("hero".equals(key) ? "首页轮播" : "每日呵护");
            JSONArray slides = new JSONArray(); Map<String,Object> row = media.get("home." + key);
            if (row != null && !str(row.get("published_url")).isEmpty()) {
                JSONObject slide = new JSONObject(); JSONObject config = JSON.parseObject(str(row.get("config_live")).isEmpty() ? "{}" : str(row.get("config_live")));
                slide.put("image", row.get("published_url")); slide.put("title", config.getOrDefault("title", row.get("title")));
                slide.put("subtitle", config.getOrDefault("subtitle", row.get("subtitle")));
                slide.put("target", config.getOrDefault("target", "none")); slide.put("productId", config.getOrDefault("productId", 0));
                slide.put("category", config.getOrDefault("category", "")); slides.add(slide);
            }
            section.put("items", slides); result.put(key, section);
        }
        result.put("coupons", module("领券享优惠"));
        JSONObject campaigns = module("限时好物"); campaigns.put("ids", new JSONArray()); result.put("campaigns", campaigns);
        JSONObject products = module("精选推荐"); products.put("ids", new JSONArray()); result.put("products", products);
        JSONObject categories = module("护肤分类"); JSONArray items = new JSONArray();
        String[] names = {"洁面", "精华", "面霜", "面膜", "爽肤水"}; String[] slots = {"cleanser", "serum", "cream", "mask", "toner"};
        for (Object value : shop.rules().getJSONArray("categories")) {
            String name = str(value), image = "";
            for (int i=0; i<names.length; i++) if (names[i].equals(name) && media.containsKey("category." + slots[i])) image = str(media.get("category." + slots[i]).get("published_url"));
            for (Map<String,Object> row : media.values()) if ((name + "分类图").equals(str(row.get("position_name")))) image = str(row.get("published_url"));
            JSONObject item = new JSONObject(); item.put("name", name); item.put("image", image); items.add(item);
        }
        categories.put("items", items); result.put("categories", categories);
        JSONObject support = new JSONObject(); support.put("mode", "wechat"); support.put("name", "联系店铺客服"); support.put("qr", ""); support.put("hours", ""); result.put("support", support);
        return result;
    }

    @PreAuthorize("@ss.hasAnyPermi('shop:manage,shop:content')")
    @PostMapping("/shop/admin/storefront") @Transactional
    public AjaxResult save(@RequestBody JSONObject input) {
        JSONObject old=lockLive(input),draft=locked(DRAFT);
        check(input.containsKey("draftRevision")||draft==null||draft.getJSONObject("layout")==null,"已有保存草稿，请重新加载后发布");
        if(input.containsKey("draftRevision"))draftRevision(input);
        JSONObject result=publish(input,old);shop.event("storefront",0,"更新首页布局与客服入口",SecurityUtils.getUsername());return AjaxResult.success(result);
    }
    private JSONObject clean(JSONObject input) {
        check(input.toJSONString().length() <= 80000, "首页内容过多");
        JSONObject clean = new JSONObject();
        for (String key : Arrays.asList("hero", "coupons", "campaigns", "products", "categories", "story")) {
            JSONObject source = input.getJSONObject(key); check(source != null, "缺少首页模块：" + key);
            JSONObject section = module(text(source, "title", 40)); section.put("enabled", source.getBooleanValue("enabled"));
            if ("hero".equals(key) || "story".equals(key)) {
                JSONArray sourceItems = source.getJSONArray("items"); check(sourceItems != null && sourceItems.size() <= 10, "每组轮播最多10张");
                JSONArray slides = new JSONArray();
                for (int i=0; i<sourceItems.size(); i++) {
                    JSONObject item = sourceItems.getJSONObject(i), slide = new JSONObject(); String target = text(item, "target", 30);
                    check(Arrays.asList("none", "product", "category", "coupons", "campaigns", "business", "distribution", "promo-materials").contains(target), "轮播跳转类型不正确");
                    slide.put("image", image(item, "image")); check(!slide.getString("image").isEmpty(), "请上传轮播图片");
                    slide.put("title", text(item,"title",60)); slide.put("subtitle", text(item,"subtitle",120)); slide.put("target", target);
                    long id = item.getLongValue("productId"); if ("product".equals(target)) check(shop.jdbc().queryForObject("select count(*) from shop_product where id=?", Long.class, id)>0, "请选择轮播关联商品");
                    slide.put("productId", id); slide.put("category", text(item,"category",60)); slides.add(slide);
                }
                section.put("items", slides);
            } else if ("products".equals(key) || "campaigns".equals(key)) {
                JSONArray ids = source.getJSONArray("ids"); check(ids != null && ids.size()<=30, "每个模块最多选择30项");
                Set<Long> unique = new LinkedHashSet<>(); for (int i=0;i<ids.size();i++) { long id=ids.getLongValue(i); check(id>0,"请选择有效商品或活动"); unique.add(id); }
                section.put("ids", unique);
            } else if ("categories".equals(key)) {
                JSONArray values = source.getJSONArray("items"); check(values!=null && values.size()<=30,"最多展示30个分类");
                JSONArray list = new JSONArray(); Set<String> names = new HashSet<>();
                for (int i=0;i<values.size();i++) { JSONObject value=values.getJSONObject(i),item=new JSONObject(); String name=text(value,"name",60); check(!name.isEmpty()&&names.add(name),"分类名称不能为空或重复"); item.put("name",name); item.put("image",image(value,"image")); list.add(item); }
                section.put("items",list);
            }
            clean.put(key,section);
        }
        JSONObject source=input.getJSONObject("support"); check(source!=null,"请配置客服入口");
        String mode=text(source,"mode",20); check(Arrays.asList("wechat","qr").contains(mode),"请选择微信客服或二维码");
        JSONObject support=new JSONObject(); support.put("mode",mode); support.put("qr",image(source,"qr")); support.put("name",text(source,"name",40)); support.put("hours",text(source,"hours",80));
        check(!"qr".equals(mode)||!support.getString("qr").isEmpty(),"请上传客服二维码"); clean.put("support",support);
        return clean;
    }
    private String text(JSONObject o,String key,int max) { String value=str(o.get(key)).trim(); check(value.length()<=max,"内容过长："+key); return value; }
    private String image(JSONObject o,String key) { String value=text(o,key,500); check(value.isEmpty()||value.matches("/(profile/upload|shop-assets)/[^\\s<>]+")||value.matches("https://[^\\s<>]+"),"图片需上传或使用HTTPS地址"); return value; }
}
