package com.golmok.oneweek.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.golmok.oneweek.dto.ReportDtos.PricePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KAMIS 소매가격을 요청 시점에 실시간으로 조회한다(pipeline/fetch_kamis.py 와 같은 API·코드 사용).
 * 향후 7일 급등확률({@link com.golmok.oneweek.entity.IngredientPrice#getProbSpike()})은 학습된 모델의
 * 결과라 여기서 다시 계산하지 않고 매일 갱신되는 시드 스냅샷 값을 그대로 쓴다.
 * 최근 며칠을 함께 조회해 가장 최신 날짜의 "평균"(현재가)·"평년" 행을 쓴다. 축산물은 하루 정도
 * 늦게 올라오는 경우가 있어 당일 값이 없을 수 있기 때문이다.
 */
@Component
@Slf4j
public class KamisPriceProvider {

    private static final String URL = "https://www.kamis.or.kr/service/price/xml.do";
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int LOOKBACK_DAYS = 30;

    /** item -> (부류코드, 품목코드, 품종코드, 등급코드). pipeline/fetch_kamis.py 의 ITEMS 와 동일. */
    private static final Map<String, String[]> CODES = new LinkedHashMap<>();
    static {
        CODES.put("배추", new String[]{"200", "211", "", "04"});
        CODES.put("무", new String[]{"200", "231", "", "04"});
        CODES.put("양파", new String[]{"200", "245", "00", "04"});
        CODES.put("대파", new String[]{"200", "246", "00", "04"});
        CODES.put("깐마늘", new String[]{"200", "258", "01", "04"});
        CODES.put("삼겹살", new String[]{"500", "4304", "27", "00"});
        CODES.put("닭", new String[]{"500", "9901", "99", "00"});
        CODES.put("계란", new String[]{"500", "9903", "23", "71"});
    }

    public record LivePrice(double price, Double normalPrice, LocalDate date, List<PricePoint> history) {}
    private record Cached(LivePrice price, Instant until) {}
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${golmok.keys.kamis-cert-key:}")
    private String certKey;
    @Value("${golmok.keys.kamis-cert-id:}")
    private String certId;

    /** 조회 가능한 품목만 담아 돌려준다. 실패한 품목은 그냥 빠지고(호출부가 시드 스냅샷으로 대체), 로그만 남긴다. */
    public Map<String, LivePrice> fetchLatest(Iterable<String> items) {
        Map<String, LivePrice> out = new LinkedHashMap<>();
        if (certKey == null || certKey.isBlank() || certId == null || certId.isBlank()) {
            log.warn("KAMIS_CERT_KEY/ID 가 없어 실시간 식자재 가격을 건너뜁니다.");
            return out;
        }
        for (String item : items) {
            String[] codes = CODES.get(item);
            if (codes == null) continue;
            Cached cached = cache.get(item);
            if (cached != null && cached.until().isAfter(Instant.now())) {
                if (cached.price() != null) out.put(item, cached.price());
                continue;
            }
            try {
                LivePrice p = fetchOne(codes);
                cache.put(item, new Cached(p, Instant.now().plusSeconds(p == null ? 60 : 21600)));
                if (p != null) out.put(item, p);
            } catch (Exception e) {
                cache.put(item, new Cached(null, Instant.now().plusSeconds(60)));
                log.warn("KAMIS 실시간 조회 실패 ({}): {}", item, e.getClass().getSimpleName());
            }
        }
        return out;
    }

    private LivePrice fetchOne(String[] codes) throws Exception {
        LocalDate end = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate start = end.minusDays(LOOKBACK_DAYS);

        StringBuilder q = new StringBuilder(URL)
                .append("?action=periodRetailProductList")
                .append("&p_cert_key=").append(URLEncoder.encode(certKey, StandardCharsets.UTF_8))
                .append("&p_cert_id=").append(URLEncoder.encode(certId, StandardCharsets.UTF_8))
                .append("&p_returntype=json")
                .append("&p_startday=").append(start.format(YMD))
                .append("&p_endday=").append(end.format(YMD))
                .append("&p_itemcategorycode=").append(codes[0])
                .append("&p_itemcode=").append(codes[1])
                .append("&p_kindcode=").append(codes[2])
                .append("&p_productrankcode=").append(codes[3])
                .append("&p_countrycode=2200")
                .append("&p_convert_kg_yn=N");

        HttpRequest req = HttpRequest.newBuilder(URI.create(q.toString()))
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());

        return parse(mapper.readTree(res.body()));
    }

    static LivePrice parse(JsonNode response) {
        JsonNode data = response.path("data");
        JsonNode items = data.path("item");
        if (!items.isArray()) return null;

        Map<LocalDate, Double> averages = new TreeMap<>();
        Map<LocalDate, Double> normals = new TreeMap<>();
        for (JsonNode it : items) {
            String county = it.path("countyname").asText();
            if (!"평균".equals(county) && !"평년".equals(county)) continue;
            try {
                LocalDate d = LocalDate.parse(it.path("yyyy").asText() + "-" + it.path("regday").asText().replace("/", "-"));
                double price = Double.parseDouble(it.path("price").asText().replace(",", ""));
                if (!Double.isFinite(price) || price <= 0) continue;
                ("평균".equals(county) ? averages : normals).put(d, price);
            } catch (RuntimeException ignored) {
                // 휴장일 '-' / 결측 행은 관측값이 아니다.
            }
        }
        if (averages.isEmpty()) return null;
        LocalDate latest = averages.keySet().stream().max(LocalDate::compareTo).orElseThrow();
        return new LivePrice(averages.get(latest), normals.get(latest), latest,
                averages.entrySet().stream().map(e -> new PricePoint(e.getKey(), e.getValue())).toList());
    }
}
