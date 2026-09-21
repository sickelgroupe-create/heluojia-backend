package com.ruoyi.web.controller.shop;
import java.util.Map;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component public class ShopGradeJobs {
 @Autowired ShopService s;@Autowired ShopGrades g;
 @Scheduled(cron="0 40 2 * * *",zone="Asia/Shanghai") public void update(){g.recalculate("系统");}
}
