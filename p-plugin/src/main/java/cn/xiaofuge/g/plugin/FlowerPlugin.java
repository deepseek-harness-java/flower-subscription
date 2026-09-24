package cn.xiaofuge.g.plugin;

import cn.xiaofuge.deepseek.harness.domain.model.entity.AbstractTool;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.xiaofuge.deepseek.harness.domain.spi.AbstractHarnessPlugin;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginContext;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginHookResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** AI 鲜花订阅管家插件：把 flower-subscription REST API 注册为 DSH Agent 工具 */
public class FlowerPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "flower-copilot";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public FlowerPlugin() { super(PLUGIN_ID); }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new PlanListTool(),
                new FlowerCareTool(),
                new SubscribeTool(),
                new SubInfoTool(),
                new DeliveryTool(),
                new StatsTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("flower-capabilities", 20, """
                ## AI 鲜花订阅管家（鲜花订阅品牌运营 · 2026-09-25）
                - 查套餐 → plan_list（4 档：轻氧周刊 99/缤纷双周 168/浪漫周享 228/尊享定制 399；报频次/花量/订阅人数）
                - 花材养护 → flower_care（name 可空：花语/养护技巧；玫瑰/向日葵/郁金香/洋桔梗/尤加利叶）
                - 新建订阅 → subscribe（customer/phone/planId/address 必填；报订阅编号/价格/首束配送时间；
                  下单前必须复述套餐、价格、配送频次与地址请客户确认）
                - 订阅查询 → sub_info（subId：套餐/地址/配送时段/下次配送/状态/历史配送）
                - 配送列表 → deliveries（本期花束/收花人/时段/状态）
                - 问运营 → stats（生效订阅/暂停数/总订阅人/月流水 MRR/分套餐分布/运营建议）
                - 回答要求：
                  1) 订阅前必须复述要素（套餐/价格/频次/地址）请客户确认
                  2) 订阅结果必报订阅编号与首束配送时间
                  3) 推荐套餐时结合场景（自用/送礼/家居），不夸大宣传
                  4) 花语与养护只转述工具返回，不编造花材库存与疗效
                """);
        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__" + PLUGIN_ID + "__")) {
                return PluginHookResult.context("audit: flower tool call.");
            }
            return null;
        });
    }

    private String get(String path, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path)).GET().build());
    }

    private String post(String path, String jsonBody, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build());
    }

    private String baseUrl(Map<String, Object> args) {
        Object override = args == null ? null : args.get("appBaseUrl");
        return override == null || String.valueOf(override).isBlank()
                ? System.getenv().getOrDefault("FLOWER_APP_BASE_URL", "http://127.0.0.1:18105")
                : String.valueOf(override);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return "{\"error\":true,\"status\":" + resp.statusCode() + "}";
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private String json(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private class PlanListTool extends AbstractTool {
        @Override public String name() { return "plan_list"; }
        @Override public String description() {
            return "订阅套餐列表：4 档套餐的价格/频次/花量/订阅人数/在售状态。推荐套餐、订阅前必查。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/plans", args));
        }
    }

    private class FlowerCareTool extends AbstractTool {
        @Override public String name() { return "flower_care"; }
        @Override public String description() {
            return "花材花语与养护：name 可空。在售花材：玫瑰/向日葵/郁金香/洋桔梗/尤加利叶。"
                    + "返回花语与养护技巧。何时必须调用：客户问花语、问怎么养花。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("name", stringSchema("花材名，可空则返回全部在售花材"))
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String q = str(args, "name").isBlank() ? "" : "?name=" + java.net.URLEncoder.encode(str(args, "name"), StandardCharsets.UTF_8);
            return ok(get("/api/flowers" + q, args));
        }
    }

    private class SubscribeTool extends AbstractTool {
        @Override public String name() { return "subscribe"; }
        @Override public String description() {
            return "新建订阅：customer（订阅人）/phone（手机号）/planId（P01-P04）/address（含门牌号）必填。"
                    + "必须先复述套餐、价格、频次与地址经客户确认后才能调用。成功返回订阅编号与首束配送时间。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("customer", stringSchema("订阅人姓名"))
                    .prop("phone", stringSchema("手机号"))
                    .prop("planId", stringSchema("套餐编号 P01-P04"))
                    .prop("address", stringSchema("配送地址，含门牌号"))
                    .required("customer", "phone", "planId", "address")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String body = "{\"customer\":\"" + json(str(args, "customer"))
                    + "\",\"phone\":\"" + json(str(args, "phone"))
                    + "\",\"planId\":\"" + json(str(args, "planId"))
                    + "\",\"address\":\"" + json(str(args, "address")) + "\"}";
            return ok(post("/api/subscribe", body, args));
        }
    }

    private class SubInfoTool extends AbstractTool {
        @Override public String name() { return "sub_info"; }
        @Override public String description() {
            return "订阅查询：subId 必填（S5001 格式）。返回套餐/地址/配送时段/下次配送/状态/历史配送。"
                    + "何时必须调用：客户问订阅、问下次送花时间。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("subId", stringSchema("订阅编号，如 S5001"))
                    .required("subId")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/sub?subId=" + java.net.URLEncoder.encode(str(args, "subId"), StandardCharsets.UTF_8), args));
        }
    }

    private class DeliveryTool extends AbstractTool {
        @Override public String name() { return "deliveries"; }
        @Override public String description() {
            return "配送列表：本期花束/收花人/配送时段/状态（待配送、配送中、已送达）。"
                    + "何时必须调用：问配送安排、问花束内容。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/deliveries", args));
        }
    }

    private class StatsTool extends AbstractTool {
        @Override public String name() { return "stats"; }
        @Override public String description() {
            return "运营统计：生效订阅/暂停数/总订阅人数/月流水 MRR/分套餐分布/待配送数/运营建议。"
                    + "何时必须调用：问运营情况、问订阅数与流水。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stats", args));
        }
    }
}
