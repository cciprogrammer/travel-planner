package com.dgcz.travel.service;

import com.dgcz.travel.common.BusinessException;
import com.dgcz.travel.config.DeepSeekProperties;
import com.dgcz.travel.dto.Itinerary;
import com.dgcz.travel.dto.PlanGenerateRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型服务：负责 Prompt 构造、调用 DeepSeek、解析结构化行程
 */
@Slf4j
@Service
public class DeepSeekService {

    private static final String SYSTEM_PROMPT =
            "你是一名资深中文旅行规划师，擅长制定可落地的行程方案。"
                    + "你必须且只能输出一个合法的 JSON 对象，不要输出任何解释文字，不要使用 Markdown 代码块包裹。";

    private final DeepSeekProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DeepSeekService(DeepSeekProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int seconds = props.getTimeoutSeconds() == null ? 120 : props.getTimeoutSeconds();
        factory.setConnectTimeout(Duration.ofSeconds(seconds));
        factory.setReadTimeout(Duration.ofSeconds(seconds));
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 启动时打印实际生效的大模型配置。
     * 排查「Key 明明换了却不生效」这类问题时，先看这行日志——它能直接告诉你进程用的是哪个 Key、哪个模型。
     */
    @PostConstruct
    public void logEffectiveConfig() {
        log.info("DeepSeek 生效配置：base-url={} model={} key={} maxTokens={} mockEnabled={} fallbackToMockOnError={}",
                props.getBaseUrl(), props.getModel(), maskKey(props.getApiKey()),
                props.getMaxTokens(), props.getMockEnabled(), props.getFallbackToMockOnError());
        if (!llmEnabled()) {
            log.warn("当前不会调用真实大模型（mockEnabled=true 或 api-key 为空），将返回内置示例行程");
        }
    }

    /**
     * 原始响应里可能带整段 reasoning_content（实测单次可达 2 万字符），
     * 直接拼进异常信息会撑爆日志与数据库字段，这里统一截断。
     */
    private String trimForLog(String resp) {
        if (resp == null) {
            return "";
        }
        return resp.length() <= 500 ? resp : resp.substring(0, 500) + "…（已截断，原始 " + resp.length() + " 字符）";
    }

    /** Key 脱敏：只保留后 4 位，用于日志中核对是否换了 Key */
    private String maskKey(String key) {
        if (blank(key)) {
            return "(未配置)";
        }
        String k = key.trim();
        return k.length() <= 4 ? "****" : "****" + k.substring(k.length() - 4);
    }

    /** 当前是否走真实大模型（false 表示使用内置示例数据） */
    public boolean llmEnabled() {
        return !(Boolean.TRUE.equals(props.getMockEnabled())
                || props.getApiKey() == null || props.getApiKey().isBlank());
    }

    /**
     * 生成行程
     */
    public Itinerary generateItinerary(PlanGenerateRequest req) {
        return generateItinerary(req, null);
    }

    /**
     * 生成行程（可携带质量标准的纠正指令，用于未达标后重写一次）
     *
     * @param correction 追加到用户 Prompt 末尾的纠正说明，可为 null
     */
    public Itinerary generateItinerary(PlanGenerateRequest req, String correction) {
        if (!llmEnabled()) {
            log.warn("DeepSeek API Key 未配置或已开启 mock，返回内置示例行程");
            return mockItinerary(req);
        }

        String userPrompt = buildPrompt(req) + (correction == null ? "" : correction);
        log.debug("调用大模型 Prompt：\n{}", userPrompt);
        String content = chat(userPrompt);
        Itinerary itinerary = parseItinerary(content);
        normalize(itinerary, req);
        return itinerary;
    }

    /** 模型不可用时的降级出口：返回内置示例行程（同样需通过质量标准校验） */
    public Itinerary fallbackMock(PlanGenerateRequest req) {
        return mockItinerary(req);
    }

    /* ---------------------------------- Prompt ---------------------------------- */

    public String buildPrompt(PlanGenerateRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下出行条件，为用户生成一份详细、可执行的旅行行程方案。\n\n");
        sb.append("【出行条件】\n");
        sb.append("- 出发地：").append(req.getOrigin()).append('\n');
        sb.append("- 目的地：").append(req.getDestination()).append('\n');
        sb.append("- 出行天数：").append(req.getDays()).append(" 天\n");
        sb.append("- 总预算：").append(req.getBudget() == null ? "不限" : req.getBudget() + " 元（人民币）").append('\n');
        sb.append("- 兴趣爱好：").append(blank(req.getHobbies()) ? "无特殊要求" : req.getHobbies()).append('\n');
        sb.append("- 其他偏好：").append(blank(req.getPreferences()) ? "无" : req.getPreferences()).append('\n');
        if (!blank(req.getStartDate())) {
            sb.append("- 计划出发日期：").append(req.getStartDate()).append('\n');
        }
        if (!blank(req.getCompanions())) {
            sb.append("- 同行人：").append(req.getCompanions()).append('\n');
        }
        sb.append("\n【地点要求】\n");
        sb.append("1. 每个事项必须写出真实、具体的地点全称（如“橘子洲头景区”“岳麓书院”“太平老街”“湖南省博物馆”），"
                + "禁止使用“核心景区”“本地特色餐厅”“文化街区”这类泛称。\n");
        sb.append("2. 同一天内的地点按地理位置就近串联，给出明确的游览顺序，避免来回折返。\n");
        sb.append("3. 餐厅要写出具体店名或商圈，如\"坡子街火宫殿总店\"。\n");

        sb.append("\n【交通要求】\n");
        sb.append("1. itinerary.transport 中必须给出：\n");
        sb.append("   - outbound：从出发地到目的地的去程交通（具体班次类型、站点、耗时、参考票价）；\n");
        sb.append("   - inbound：返程交通（同上）；\n");
        sb.append("   - local：市内交通总体建议（当地地铁/公交体系、是否推荐地铁+打车组合、乘车码/一卡通建议）；\n");
        sb.append("   - options：3-5 条可选交通方式及其优缺点与票价区间。\n");
        sb.append("2. 每一天的 day.transport 要写明当天在景点之间移动所采用的具体交通方式（如\"地铁 2 号线橘子洲站→溁湾镇站，约 10 分钟\"）。\n");
        sb.append("3. 每个事项的 item.transport 要写明\"从上一地点到该地点\"的交通方式与大致耗时；当天第一个事项写从酒店出发的方式。\n");

        sb.append("\n【住宿要求】\n");
        sb.append("hotels 至少给出 3 家，第 1 家为推荐酒店（category 填\"推荐\"），其余为备选（category 填\"备选\"）。"
                + "每家都要写清 name（酒店名称）、area（所在区域/商圈）、price（大致价位）、"
                + "reason（推荐理由：交通/性价比/环境/亲子等）、advantage（位置优势：离哪些景点或地铁站多近）、"
                + "level（经济型/舒适型/高档型）。\n");
        sb.append("price 必须写成「数字区间 + 元/晚」的格式，例如\"420-560 元/晚\"或\"约 400 元/晚\"，"
                + "不要写\"视房型而定\"这类无法核算的表述——系统会按该价格实时计算住宿总花费。\n");
        sb.append("【关键】酒店必须与用户预算匹配，且价位要拉开梯度，覆盖不同预算区间："
                + "按「总预算 ÷ 天数 × 40%~50%」估算每晚可承受的房价，"
                + "至少 1 家酒店落在该区间内，再各给 1 家明显更低与明显更高的选项，"
                + "保证用户无论把预算调高还是调低，都有一家刚好合适的酒店可选。\n");
        sb.append("这三家的每晚价格要至少跨越三档（例如 200-300 / 400-600 / 800 元以上），"
                + "不要三家挤在同一个价位区间。\n");
        sb.append("行程动线请以「住在地铁沿线或核心景区附近」为前提设计，说明每天从酒店出发、返回酒店的方式。\n");

        sb.append("\n【购物推荐要求】\n");
        sb.append("shopping 给出 2-5 个值得去的购物地点或区域（不是具体某家店也行，写清区域/商圈即可），"
                + "覆盖不同类型：地方特产伴手礼、文创礼品、综合商圈、农贸批发市场等。\n");
        sb.append("每条要写清 name（地点或区域名称，必须具体，如\"坡子街\"\"黄兴路步行街 & IFS 国金中心\"）、"
                + "area（所在区域/商圈）、type（类型）、buyWhat（适合买什么：具体品类或商品）、"
                + "suitableFor（适合人群/场景）、tips（实用提示：营业时间、砍价、能否快递等）。\n");
        sb.append("【重要】只推荐去处，不给任何购物额度、预算或金额建议——是否购物、花多少钱完全由用户自己决定，"
                + "禁止出现\"购物预算\"\"人均消费上限\"\"建议预留 X 元购物\"这类表述。\n");

        sb.append("\n【输出要求】\n");
        sb.append("1. 只输出 JSON，不要输出任何多余文字，不要使用 Markdown 代码块。\n");
        sb.append("2. JSON 结构严格如下：\n");
        sb.append("{\n");
        sb.append("  \"title\": \"行程标题（20 字以内）\",\n");
        sb.append("  \"summary\": \"行程总览，150 字左右\",\n");
        sb.append("  \"transport\": {\n");
        sb.append("    \"outbound\": \"去程交通：具体方式+站点+耗时+票价\",\n");
        sb.append("    \"inbound\": \"返程交通：具体方式+站点+耗时+票价\",\n");
        sb.append("    \"local\": \"市内交通总体建议\",\n");
        sb.append("    \"options\": [\"可选方式 1（耗时/票价/优缺点）\", \"可选方式 2\"]\n");
        sb.append("  },\n");
        sb.append("  \"hotels\": [\n");
        sb.append("    {\"name\": \"酒店名称\", \"area\": \"所在区域\", \"price\": \"350-500 元/晚\", "
                + "\"reason\": \"推荐理由\", \"advantage\": \"位置优势\", \"level\": \"舒适型\", \"category\": \"推荐\"},\n");
        sb.append("    {\"name\": \"备选酒店名称\", \"area\": \"\", \"price\": \"\", \"reason\": \"\", "
                + "\"advantage\": \"\", \"level\": \"经济型\", \"category\": \"备选\"}\n");
        sb.append("  ],\n");
        sb.append("  \"shopping\": [\n");
        sb.append("    {\"name\": \"购物地点或区域名称\", \"area\": \"所在区域/商圈\", \"type\": \"特产伴手礼\", "
                + "\"buyWhat\": \"适合买什么（具体品类）\", \"suitableFor\": \"适合人群/场景\", \"tips\": \"实用提示\"}\n");
        sb.append("  ],\n");
        sb.append("  \"days\": [\n");
        sb.append("    {\n");
        sb.append("      \"day\": 1,\n");
        sb.append("      \"theme\": \"当日主题\",\n");
        sb.append("      \"date\": \"第 1 天（或具体日期）\",\n");
        sb.append("      \"transport\": \"当天景点之间的交通小结\",\n");
        sb.append("      \"items\": [\n");
        sb.append("        {\"time\": \"09:00-11:00\", \"place\": \"具体地点名称\", \"activity\": \"具体安排描述\", "
                + "\"transport\": \"从上一地点到此处的交通方式与耗时\", \"cost\": \"约 80 元/人\", \"tips\": \"实用小贴士\"}\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"budget\": {\"transport\": \"\", \"accommodation\": \"\", \"food\": \"\", "
                + "\"tickets\": \"\", \"other\": \"\", \"total\": \"\"},\n");
        sb.append("  \"tips\": [\"出行提示 1\", \"出行提示 2\"]\n");
        sb.append("}\n");
        sb.append("3. days 数组长度必须等于出行天数 ").append(req.getDays()).append(" 天，每天安排 3-5 个事项。\n");
        sb.append("4. 时间顺序合理、不冲突，预留交通与用餐时间。\n");
        sb.append("5. 各项费用累计应控制在总预算以内，并在 budget 中给出各项金额（字符串，带单位\"元\"）。"
                + "其中 accommodation 按「推荐酒店单价 × 入住晚数」估算。"
                + "注意 budget 中只有交通/住宿/餐饮/门票/其他五项加合计，不设购物项——购物完全由用户自行决定。\n");
        sb.append("6. 内容必须贴合用户的兴趣爱好，语言为简体中文。\n");
        return sb.toString();
    }

    /* --------------------------------- HTTP 调用 --------------------------------- */

    private String chat(String userPrompt) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("stream", false);
        body.put("temperature", 0.7);
        body.put("response_format", Map.of("type", "json_object"));
        if (props.getMaxTokens() != null && props.getMaxTokens() > 0) {
            body.put("max_tokens", props.getMaxTokens());
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);

        String resp;
        try {
            resp = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (HttpStatusCodeException e) {
            // 把 HTTP 状态码翻译成可操作的提示，避免把整段异常堆栈抛给用户
            String detail = extractErrorMessage(e.getResponseBodyAsString());
            log.error("调用大模型失败 HTTP {}：{}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(explainStatus(e.getStatusCode().value(), detail));
        } catch (Exception e) {
            log.error("调用大模型失败", e);
            throw new BusinessException("调用大模型失败：" + e.getMessage());
        }
        if (resp == null || resp.isBlank()) {
            throw new BusinessException("大模型返回内容为空");
        }
        try {
            JsonNode root = objectMapper.readTree(resp);
            if (root.hasNonNull("error")) {
                throw new BusinessException("大模型返回错误：" + root.path("error").path("message").asText());
            }
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            JsonNode content = message.path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                String finish = choice.path("finish_reason").asText("");
                boolean reasoningOnly = message.path("reasoning_content").asText("").length() > 0;
                if (reasoningOnly) {
                    // 推理模型（deepseek-flash / deepseek-v4-pro）会把输出额度先花在「思考」上，
                    // 生成长行程 JSON 时极易只产出思考、没有正文。deepseek-chat 无此问题。
                    throw new BusinessException("该模型是推理模型，思考过程占满了输出额度，正文未产出"
                            + ("length".equals(finish) ? "（finish_reason=length）" : "")
                            + "：请把 deepseek.model 改为 deepseek-chat，或调大 deepseek.max-tokens 后重试");
                }
                if ("length".equals(finish)) {
                    throw new BusinessException("大模型输出被 max_tokens 截断（finish_reason=length），"
                            + "请调大 deepseek.max-tokens 后重试");
                }
                throw new BusinessException("大模型返回内容为空，原始响应：" + trimForLog(resp));
            }
            return content.asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("解析大模型响应失败：" + e.getMessage());
        }
    }

    /** 从 DeepSeek 的错误响应体里取出 error.message */
    private String extractErrorMessage(String body) {
        if (blank(body)) {
            return null;
        }
        try {
            JsonNode err = objectMapper.readTree(body).path("error").path("message");
            return err.isMissingNode() || err.asText().isBlank() ? null : err.asText();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 把 HTTP 状态码翻译成用户能照做的提示 */
    private String explainStatus(int status, String detail) {
        String prefix = switch (status) {
            case 400 -> "请求被 DeepSeek 拒绝";
            case 401 -> "DeepSeek API Key 无效或未授权，请检查 deepseek.api-key 配置";
            case 402 -> "DeepSeek 账户余额不足，请先充值后再试";
            case 403 -> "DeepSeek 拒绝访问该模型，请确认账号是否开通对应模型";
            case 429 -> "DeepSeek 请求过于频繁，请稍后重试";
            default -> status >= 500
                    ? "DeepSeek 服务端异常（HTTP " + status + "），请稍后重试"
                    : "调用 DeepSeek 失败（HTTP " + status + "）";
        };
        return blank(detail) ? prefix : prefix + "：" + detail;
    }

    /* ---------------------------------- 解析 ---------------------------------- */

    public Itinerary parseItinerary(String content) {
        String json = content.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(json, Itinerary.class);
        } catch (Exception e) {
            log.error("行程 JSON 解析失败，原始内容：{}", content, e);
            throw new BusinessException("大模型返回格式异常，无法解析为行程：" + e.getMessage());
        }
    }

    private void normalize(Itinerary itinerary, PlanGenerateRequest req) {
        if (itinerary == null) {
            throw new BusinessException("大模型未返回有效行程");
        }
        if (blank(itinerary.getTitle())) {
            itinerary.setTitle(req.getDestination() + req.getDays() + "日行程");
        }
        if (itinerary.getDays() == null || itinerary.getDays().isEmpty()) {
            itinerary.setDays(mockItinerary(req).getDays());
        }
        if (itinerary.getDays().size() > req.getDays()) {
            itinerary.setDays(new ArrayList<>(itinerary.getDays().subList(0, req.getDays())));
        }
        for (int i = 0; i < itinerary.getDays().size(); i++) {
            Itinerary.DayPlan day = itinerary.getDays().get(i);
            if (day.getDay() == null) {
                day.setDay(i + 1);
            }
            if (day.getItems() == null) {
                day.setItems(new ArrayList<>());
            }
        }
    }

    /* ---------------------------------- Mock ---------------------------------- */

    private Itinerary mockItinerary(PlanGenerateRequest req) {
        String dest = blank(req.getDestination()) ? "" : req.getDestination().trim();
        int days = req.getDays() == null || req.getDays() < 1 ? 1 : req.getDays();
        if (dest.contains("长沙")) {
            Itinerary it = loadChangshaMock();
            if (it != null && it.getDays() != null && !it.getDays().isEmpty()) {
                return fitDays(it, req, dest, days);
            }
        }
        return genericMock(req, days);
    }

    /** 长沙示例方案放在 resources/mock/changsha.json，内容与代码解耦，便于单独维护 */
    private Itinerary loadChangshaMock() {
        try (InputStream in = new ClassPathResource("mock/changsha.json").getInputStream()) {
            return objectMapper.readValue(in, Itinerary.class);
        } catch (Exception e) {
            log.warn("读取长沙示例方案失败：{}", e.getMessage());
            return null;
        }
    }

    /** 把示例方案的天数对齐到用户请求的天数：多了裁剪，少了补机动日 */
    private Itinerary fitDays(Itinerary it, PlanGenerateRequest req, String dest, int days) {
        it.setTitle(dest + " " + days + " 日行程（示例数据）");
        List<Itinerary.DayPlan> list = new ArrayList<>(it.getDays());
        while (list.size() > days) {
            list.remove(list.size() - 1);
        }
        while (list.size() < days) {
            int next = list.size() + 1;
            Itinerary.DayPlan extra = new Itinerary.DayPlan();
            extra.setTheme("机动 / 周边自由行");
            extra.setDate("第 " + next + " 天");
            extra.setTransport("机动日：可乘高铁或大巴前往岳阳楼、韶山、靖港古镇等周边目的地，单程 1-1.5 小时。");
            List<Itinerary.Item> items = new ArrayList<>();
            items.add(item("09:00-12:00", "周边一日游（岳阳楼 / 韶山 / 靖港古镇）",
                    "按兴趣任选其一：岳阳楼看洞庭湖，韶山参观毛泽东故居，靖港古镇逛水乡老街。",
                    "长沙南站乘高铁至岳阳东站约 40 分钟；韶山方向高铁约 40 分钟；靖港古镇建议自驾或包车约 1 小时",
                    "门票 0-80 元/人", "周边线路请提前查班次，返程末班车多在 20:00 前"));
            items.add(item("15:00-18:00", "返回长沙市区",
                    "回到市区后可再逛一次喜欢的商圈补货，或回酒店休整。",
                    "高铁返长沙南站后换地铁 2 号线回五一广场，约 30 分钟",
                    "约 60 元/人", "机动日不要排太满，留足返程时间"));
            extra.setItems(items);
            list.add(extra);
        }
        for (int i = 0; i < list.size(); i++) {
            Itinerary.DayPlan d = list.get(i);
            d.setDay(i + 1);
            if (blank(d.getDate())) {
                d.setDate("第 " + (i + 1) + " 天");
            }
        }
        it.setDays(list);
        return it;
    }

    /** 其他目的地的通用示例方案：结构完整，同样含点对点交通、往返交通与住宿推荐 */
    private Itinerary genericMock(PlanGenerateRequest req, int days) {
        String origin = blank(req.getOrigin()) ? "出发地" : req.getOrigin();
        String dest = blank(req.getDestination()) ? "目的地" : req.getDestination();
        Itinerary it = new Itinerary();
        it.setTitle(dest + " " + days + " 日行程（示例数据）");
        it.setSummary("本方案为系统内置示例数据。配置有效的 DeepSeek API Key 后，将由大模型按你的条件实时生成："
                + "从" + origin + "出发前往" + dest + "，共 " + days + " 天，"
                + "预算 " + (req.getBudget() == null ? "不限" : req.getBudget() + " 元") + "，"
                + "重点覆盖" + (blank(req.getHobbies()) ? "经典景点与本地美食" : req.getHobbies())
                + "。示例行程已包含每日地点顺序、点对点交通、往返与市内交通建议、住宿推荐与备选酒店。");

        Itinerary.Transport t = new Itinerary.Transport();
        t.setOutbound("去程：" + origin + " → " + dest + "。中短途优先高铁/动车，准点率高且车站多接驳地铁；"
                + "距离较远可选航班，需提前 2 小时到机场。到站后优先乘地铁或机场大巴进城。");
        t.setInbound("返程：" + dest + " → " + origin + "。建议提前 1-2 天购票，"
                + "并确认末班车次与机场大巴时刻，返程当天预留 40 分钟以上的市内到站时间。");
        t.setLocal("市内交通建议「地铁 + 网约车」组合：核心景区间用地铁避免堵车，夜间与远距离景点用网约车；"
                + "可开通当地乘车码，单程票价一般 2-8 元。");
        t.setOptions(List.of(
                "高铁 / 动车：中短途首选，到站即换地铁，二等座参考价 100-500 元",
                "飞机：适合长距离，含税约 500-1200 元，需提前 2 小时安检",
                "地铁 / 公交：市区主力，单程 2-8 元，扫码即可乘坐",
                "网约车 / 出租车：适合夜间与跨区出行，起步价约 8-13 元",
                "共享单车 / 步行：老城与滨水区域短途接驳，约 1-2 元/30 分钟"));
        it.setTransport(t);

        it.setHotels(List.of(
                hotel("市区核心商圈·舒适型酒店", dest + "市中心商圈", "350-550 元/晚",
                        "位于商圈与地铁换乘点附近，白天出行、晚上逛吃都方便，适合首次到访、重视便利的游客。",
                        "步行可达主要商业街与地铁站，到核心景区地铁 2-4 站。", "舒适型", "推荐"),
                hotel("地铁沿线·经济型连锁酒店", dest + "地铁 1/2 号线沿线", "180-280 元/晚",
                        "预算友好、连锁标准化、干净安全，适合学生党与短途出行。",
                        "紧邻地铁站出入口，通勤式出行效率高。", "经济型", "备选"),
                hotel("城市地标·高档型酒店", dest + "城市地标 / 核心商圈", "800-1500 元/晚",
                        "视野与硬件出色，适合想住得舒服、重视景观与服务体验的游客。",
                        "位于城市地标内，楼下即商圈，夜景视野好。", "高档型", "备选")));

        List<Itinerary.DayPlan> list = new ArrayList<>();
        for (int i = 1; i <= days; i++) {
            Itinerary.DayPlan day = new Itinerary.DayPlan();
            day.setDay(i);
            day.setDate("第 " + i + " 天");
            day.setTheme(i == 1 ? "抵达与城市初体验" : (i == days ? "收尾采购与返程" : "深度游玩"));
            day.setTransport("当天以「地铁 + 步行」串联，景点之间单程约 15-30 分钟；夜间长距离移动建议打车，预留 20 分钟弹性。");
            List<Itinerary.Item> items = new ArrayList<>();
            items.add(item("09:00-11:30", dest + "代表性地标景区",
                    "参观核心景点，提前在线预约门票，避开团队高峰。",
                    "从酒店乘地铁直达最近站点，约 20 分钟",
                    "约 80 元/人", "建议错峰出行，早上人少"));
            items.add(item("11:30-13:30", "本地特色餐饮街",
                    "品尝当地招牌菜与小吃，选一家排队适中的老店。",
                    "从景区步行或打车约 10 分钟",
                    "约 100 元/人", "可在点评类 App 提前取号"));
            items.add(item("14:00-17:00", "文化街区 / 博物馆",
                    "了解城市历史人文，博物馆通常免费但需预约。",
                    "地铁 1-2 站或打车约 15 分钟",
                    "约 50 元/人", "周一闭馆较多，注意开放时间"));
            items.add(item("18:30-21:00", "夜景打卡地 / 滨水步道",
                    "散步夜市、拍摄夜景，顺路解决晚餐。",
                    "打车约 15 分钟，注意地铁末班车时间",
                    "约 60 元/人", "夜间返程预留打车预算"));
            day.setItems(items);
            list.add(day);
        }
        it.setDays(list);

        // 购物推荐：只推荐去处，不含任何额度
        Itinerary.ShoppingSpot s1 = shoppingSpot("市中心商圈 & 步行街", dest + "市中心商圈",
                "综合商圈", "服饰、美妆、数码与本地品牌门店", "想逛街购物、买当季新品的游客",
                "营业时间一般 10:00-22:00，商场可开发票，大件可让商家安排快递");
        Itinerary.ShoppingSpot s2 = shoppingSpot("本地特产 / 老字号聚集街区", dest + "老城区步行街",
                "特产伴手礼", "本地糕点、茶叶、腊味、辣椒酱等可带走的特产", "需要给亲友带伴手礼的游客",
                "注意保质期与携带限制（液体、肉制品上飞机需托运）");
        Itinerary.ShoppingSpot s3 = shoppingSpot("城市文创商店 / 博物馆文创", dest + "博物馆或文化街区",
                "文创礼品", "博物馆周边、地方题材文创、冰箱贴与明信片", "喜欢文化类小礼品、预算有限的游客",
                "博物馆文创多在闭馆前一小时停止售卖，周一闭馆需避开");
        it.setShopping(List.of(s1, s2, s3));

        Itinerary.BudgetBreakdown budget = new Itinerary.BudgetBreakdown();
        budget.setTransport("约 700 元");
        budget.setAccommodation("约 1200 元（舒适型酒店约 400 元/晚 × 3 晚）");
        budget.setFood("约 600 元");
        budget.setTickets("约 400 元");
        budget.setOther("约 200 元");
        budget.setTotal(req.getBudget() == null ? "约 3100 元" : req.getBudget() + " 元");
        it.setBudget(budget);

        it.setTips(List.of(
                "示例数据：配置有效的 DeepSeek API Key 后将由大模型实时生成",
                "出行前请确认景区开放时间与预约政策",
                "住宿建议选在地铁换乘站附近，可显著压缩通勤时间",
                "建议预留 15% 的机动预算应对临时消费"));
        return it;
    }

    private Itinerary.Hotel hotel(String name, String area, String price, String reason,
                                  String advantage, String level, String category) {
        Itinerary.Hotel h = new Itinerary.Hotel();
        h.setName(name);
        h.setArea(area);
        h.setPrice(price);
        h.setReason(reason);
        h.setAdvantage(advantage);
        h.setLevel(level);
        h.setCategory(category);
        return h;
    }

    private Itinerary.Item item(String time, String place, String activity, String transport,
                                String cost, String tips) {
        Itinerary.Item item = new Itinerary.Item();
        item.setTime(time);
        item.setPlace(place);
        item.setActivity(activity);
        item.setTransport(transport);
        item.setCost(cost);
        item.setTips(tips);
        return item;
    }

    private Itinerary.ShoppingSpot shoppingSpot(String name, String area, String type,
                                               String buyWhat, String suitableFor, String tips) {
        Itinerary.ShoppingSpot s = new Itinerary.ShoppingSpot();
        s.setName(name);
        s.setArea(area);
        s.setType(type);
        s.setBuyWhat(buyWhat);
        s.setSuitableFor(suitableFor);
        s.setTips(tips);
        return s;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
