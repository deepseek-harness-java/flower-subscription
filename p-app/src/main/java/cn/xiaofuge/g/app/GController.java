package cn.xiaofuge.g.app;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 鲜花订阅管家 REST 接口。
 * 提供：套餐列表 / 花材养护 / 新建订阅 / 订阅查询 / 配送列表 / 运营统计。
 */
@RestController
@RequestMapping("/api")
public class GController {

    private final GStore store;

    public GController(GStore store) {
        this.store = store;
    }

    /** 套餐列表 */
    @GetMapping("/plans")
    public Map<String, Object> plans() {
        return store.planList();
    }

    /** 花材花语与养护（可指定名称） */
    @GetMapping("/flowers")
    public Map<String, Object> flowers(@RequestParam(required = false) String name) {
        return store.flowerCare(name == null ? "" : name);
    }

    /** 新建订阅 */
    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(@RequestBody Map<String, String> body) {
        return store.subscribe(body.getOrDefault("customer", ""), body.getOrDefault("phone", ""),
                body.getOrDefault("planId", ""), body.getOrDefault("address", ""));
    }

    /** 订阅查询 */
    @GetMapping("/sub")
    public Map<String, Object> sub(@RequestParam(required = false) String subId) {
        return store.subInfo(subId == null ? "" : subId);
    }

    /** 配送列表 */
    @GetMapping("/deliveries")
    public Map<String, Object> deliveries() {
        return store.deliveryList();
    }

    /** 运营统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return store.stats();
    }
}
