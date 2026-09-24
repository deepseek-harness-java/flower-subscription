package cn.xiaofuge.g.app;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/** 鲜花订阅数据中心：套餐/订阅/配送/花材养护/统计 */
@Component
public class GStore {

    /** 花材花语：名称/花语/养护 */
    static final Map<String, Object[]> FLOWERS = new LinkedHashMap<>();
    static {
        FLOWERS.put("玫瑰", new Object[]{"热恋与真爱", "45°斜剪根，深水养护，2 天换水"});
        FLOWERS.put("向日葵", new Object[]{"沉默的爱与向阳而生", "浅水养护，去大叶防腐烂"});
        FLOWERS.put("郁金香", new Object[]{"优雅与祝福", "少水养护，避阳光直射可延长花期"});
        FLOWERS.put("洋桔梗", new Object[]{"真诚不变的爱", "斜剪根，花瓣忌喷水"});
        FLOWERS.put("尤加利叶", new Object[]{"恩赐与回忆", "可自然风干做干花"});
    }

    public static class Plan {
        public String id; public String name; public double price; public String freq;
        public String desc; public int subscribers; public String status;
    }

    public static class Sub {
        public String id; public String customer; public String phone; public String planId;
        public String plan; public String address; public String slot; public String nextDelivery;
        public String status; // 生效中 / 已暂停 / 已取消
    }

    public static class Delivery {
        public String id; public String customer; public String bouquet;
        public String date; public String slot; public String status; // 待配送 / 配送中 / 已送达
    }

    public final List<Plan> plans = new ArrayList<>();
    public final List<Sub> subs = new ArrayList<>();
    public final List<Delivery> deliveries = new ArrayList<>();
    private int subSeq = 5001;
    private int dlvSeq = 8001;

    public GStore() { seed(); }

    private void seed() {
        plans.add(pl("P01", "轻氧周刊", 99.0, "每周一束", "每周 1 束，单束 5-7 支，时令混搭", 128, "在售"));
        plans.add(pl("P02", "缤纷双周", 168.0, "两周一束", "每两周 1 束，单束 9-11 支，进口花材混搭", 86, "在售"));
        plans.add(pl("P03", "浪漫周享", 228.0, "每周一束", "每周 1 束，单束 12-15 支，含尤加利叶配草", 54, "在售"));
        plans.add(pl("P04", "尊享定制", 399.0, "每周一束", "每周 1 束，花艺师定制主题，附手写卡", 21, "在售"));

        subs.add(s("陈小姐", "138****7011", "P01", "徐汇区樱花大道 88 弄 3-201", "每周六 09:00-12:00", "本周六 09:00-12:00", "生效中"));
        subs.add(s("吴先生", "139****7022", "P03", "静安区南京西路 1200 号 18F", "每周日 14:00-18:00", "本周日 14:00-18:00", "生效中"));
        subs.add(s("苏女士", "137****7033", "P04", "浦东新区芳甸路 300 号 2F", "每周五 09:00-12:00", "本周五 09:00-12:00", "已暂停"));

        deliveries.add(d("陈小姐", "玫瑰 9 支 + 洋桔梗 3 支 + 尤加利", "本周六 09:00-12:00", "待配送"));
        deliveries.add(d("吴先生", "郁金香 15 支混搭", "本周日 14:00-18:00", "待配送"));
        deliveries.add(d("苏女士", "花艺师定制主题花束", "本周五 09:00-12:00", "配送中"));
    }

    private Plan pl(String id, String name, double price, String freq, String desc, int subscribers, String status) {
        Plan x = new Plan(); x.id = id; x.name = name; x.price = price; x.freq = freq;
        x.desc = desc; x.subscribers = subscribers; x.status = status; return x;
    }

    private Sub s(String customer, String phone, String planId, String address, String slot, String next, String status) {
        Sub x = new Sub(); x.id = "S" + subSeq++; x.customer = customer; x.phone = phone;
        x.planId = planId;
        Plan p = plans.stream().filter(pp -> pp.id.equals(planId)).findFirst().orElse(null);
        x.plan = p != null ? p.name : planId;
        x.address = address; x.slot = slot; x.nextDelivery = next; x.status = status; return x;
    }

    private Delivery d(String customer, String bouquet, String slot, String status) {
        Delivery x = new Delivery(); x.id = "D" + dlvSeq++; x.customer = customer; x.bouquet = bouquet;
        x.date = slot.split(" ")[0]; x.slot = slot; x.status = status; return x;
    }

    /** 套餐列表 */
    public Map<String, Object> planList() {
        List<Map<String, Object>> list = plans.stream()
                .map(x -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", x.id); m.put("name", x.name); m.put("price", x.price);
                    m.put("freq", x.freq); m.put("desc", x.desc); m.put("subscribers", x.subscribers);
                    m.put("status", x.status); return m; })
                .collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", list.size()); r.put("plans", list);
        return r;
    }

    /** 花材与养护 */
    public Map<String, Object> flowerCare(String name) {
        if (name == null || name.isBlank()) {
            List<Map<String, Object>> list = new ArrayList<>();
            FLOWERS.forEach((k, v) -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", k); m.put("language", v[0]); m.put("care", v[1]); list.add(m); });
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("ok", true); r.put("flowers", list);
            r.put("msg", "本期在售花材 " + list.size() + " 种，可指定单种查询花语与养护");
            return r;
        }
        Object[] rule = FLOWERS.get(name);
        if (rule == null) return Map.of("ok", false, "msg", "花材 " + name + " 本期未上架，在售：" + String.join("/", FLOWERS.keySet()));
        return Map.of("ok", true, "name", name, "language", rule[0], "care", rule[1],
                "msg", name + " 花语：" + rule[0] + "；养护：" + rule[1]);
    }

    /** 新建订阅：套餐校验 */
    public synchronized Map<String, Object> subscribe(String customer, String phone, String planId, String address) {
        if (customer == null || customer.isBlank() || phone == null || phone.isBlank())
            return Map.of("ok", false, "msg", "请提供订阅人姓名和手机号");
        if (address == null || address.isBlank())
            return Map.of("ok", false, "msg", "请提供配送地址（含门牌号）");
        Plan p = plans.stream().filter(x -> x.id.equalsIgnoreCase(planId)).findFirst().orElse(null);
        if (p == null) return Map.of("ok", false, "msg", "套餐 " + planId + " 不存在，可选 P01-P04");
        Sub x = new Sub(); x.id = "S" + subSeq++; x.customer = customer; x.phone = phone;
        x.planId = p.id; x.plan = p.name; x.address = address;
        x.slot = "每周六 09:00-12:00"; x.nextDelivery = "下周六 09:00-12:00"; x.status = "生效中";
        p.subscribers++;
        subs.add(0, x);
        deliveries.add(0, d(customer, p.name.contains("定制") ? "花艺师定制主题花束" : p.name + " 首束", "下周六 09:00-12:00", "待配送"));
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("subId", x.id); r.put("plan", p.name); r.put("price", p.price);
        r.put("slot", x.slot); r.put("nextDelivery", x.nextDelivery);
        r.put("msg", "订阅成功，编号 " + x.id + "，" + p.name + " ¥" + p.price + "，首束将于 " + x.nextDelivery + " 送达");
        return r;
    }

    /** 订阅查询 */
    public Map<String, Object> subInfo(String subId) {
        Sub x = subs.stream().filter(s -> s.id.equalsIgnoreCase(subId)).findFirst().orElse(null);
        if (x == null) return Map.of("ok", false, "msg", "订阅 " + subId + " 不存在，当前共 " + subs.size() + " 个订阅");
        List<Map<String, Object>> my = deliveries.stream().filter(dd -> dd.customer.equals(x.customer))
                .map(dd -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", dd.id); m.put("bouquet", dd.bouquet); m.put("date", dd.date);
                    m.put("slot", dd.slot); m.put("status", dd.status); return m; })
                .collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("subId", x.id); r.put("customer", x.customer); r.put("plan", x.plan);
        r.put("address", x.address); r.put("slot", x.slot); r.put("nextDelivery", x.nextDelivery);
        r.put("status", x.status); r.put("deliveries", my);
        return r;
    }

    /** 配送列表 */
    public Map<String, Object> deliveryList() {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", deliveries.size()); r.put("items", deliveries);
        r.put("msg", "本期配送任务 " + deliveries.size() + " 单，其中配送中 " +
                deliveries.stream().filter(dd -> "配送中".equals(dd.status)).count() + " 单");
        return r;
    }

    /** 运营统计 */
    public Map<String, Object> stats() {
        Map<String, Object> byPlan = new LinkedHashMap<String, Object>();
        for (Plan p : plans) byPlan.put(p.name, "订阅 " + p.subscribers + " 人 · ¥" + p.price);
        double mrr = plans.stream().mapToDouble(p -> p.price * p.subscribers).sum();
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("activeSubs", subs.stream().filter(x -> "生效中".equals(x.status)).count());
        r.put("pausedSubs", subs.stream().filter(x -> "已暂停".equals(x.status)).count());
        r.put("totalSubscribers", plans.stream().mapToInt(p -> p.subscribers).sum());
        r.put("mrr", mrr);
        r.put("byPlan", byPlan);
        r.put("pendingDeliveries", deliveries.stream().filter(d -> "待配送".equals(d.status)).count());
        r.put("advice", "尊享定制订阅基数小但客单价高，可推升级活动；苏女士订单已暂停需回访原因；周六为配送高峰建议提前备花");
        return r;
    }
}
