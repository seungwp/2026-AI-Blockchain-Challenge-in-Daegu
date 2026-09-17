package com.golmok.oneweek.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.golmok.oneweek.dto.ReportDtos.WeatherDay;
import com.golmok.oneweek.provider.Providers.WeatherProvider;
import com.golmok.oneweek.entity.SourceCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 기상청 예보 연동. 7일치를 두 API 로 나눠 채운다.
 *
 * <ul>
 *   <li>단기예보(getVilageFcst): 오늘~+4일. 5km 격자라 가게 좌표로 구·군이 반영된다.</li>
 *   <li>중기예보(getMidLandFcst, getMidTa): +5일 이후. <b>대구 광역 단위</b>라 동네 구분이 없다.</li>
 * </ul>
 *
 * 어느 단계든 실패하면 남은 날짜를 {@link MockWeatherProvider} 예시 값으로 채우고 로그만 남긴다.
 * 심사 시연 중 외부 API 가 죽어도 화면이 비지 않도록 하기 위한 것이다.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "golmok.providers.weather", havingValue = "kma")
public class KmaWeatherProvider implements WeatherProvider {

    private static final String VILAGE =
            "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND =
            "https://apis.data.go.kr/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA =
            "https://apis.data.go.kr/1360000/MidFcstInfoService/getMidTa";

    /** 중기육상예보 구역: 대구·경북. */
    private static final String MID_LAND_REG = "11H10000";
    /** 중기기온 지점: 대구. */
    private static final String MID_TA_REG = "11H10701";

    /** 단기예보 발표 시각(시). 이 중 현재 시각 이전의 가장 최근 것을 쓴다. */
    private static final int[] BASE_HOURS = {23, 20, 17, 14, 11, 8, 5, 2};

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final MockWeatherProvider fallback = new MockWeatherProvider();

    @Value("${golmok.keys.data-go-kr:}")
    private String serviceKey;

    @Override
    public List<WeatherDay> weekly(double latitude, double longitude, LocalDate start, int days) {
        List<WeatherDay> filler = fallback.weekly(latitude, longitude, start, days);
        if (serviceKey == null || serviceKey.isBlank()) {
            log.warn("DATA_GO_KR_KEY 가 없어 예시 날씨를 사용합니다.");
            return filler;
        }

        Map<LocalDate, DayBuilder> byDate = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) byDate.put(start.plusDays(i), new DayBuilder());

        try {
            readShortTerm(latitude, longitude, byDate);
        } catch (Exception e) {
            log.warn("단기예보 조회 실패, 해당 구간은 예시 값 사용: {}", e.toString());
        }
        try {
            readMidTerm(byDate);
        } catch (Exception e) {
            log.warn("중기예보 조회 실패, 해당 구간은 예시 값 사용: {}", e.toString());
        }

        List<WeatherDay> out = new ArrayList<>();
        int i = 0;
        for (Map.Entry<LocalDate, DayBuilder> e : byDate.entrySet()) {
            WeatherDay demo = filler.get(i++);
            out.add(e.getValue().toWeatherDay(e.getKey(), demo));
        }
        return out;
    }

    // ── 단기예보 (오늘 ~ +4일) ──────────────────────────────────────────────

    private void readShortTerm(double lat, double lon, Map<LocalDate, DayBuilder> byDate) throws Exception {
        KmaGrid.Point g = KmaGrid.of(lat, lon);
        LocalDateTime base = latestBase(LocalDateTime.now());

        JsonNode items = get(VILAGE, Map.of(
                "base_date", base.format(YMD),
                "base_time", String.format("%02d00", base.getHour()),
                "nx", String.valueOf(g.nx()),
                "ny", String.valueOf(g.ny()),
                "numOfRows", "1000",
                "pageNo", "1"));

        for (JsonNode it : items) {
            LocalDate d = LocalDate.parse(it.path("fcstDate").asText(), YMD);
            DayBuilder b = byDate.get(d);
            if (b == null) continue;
            String v = it.path("fcstValue").asText();
            switch (it.path("category").asText()) {
                case "TMX" -> b.tMax = parseDouble(v);
                case "TMN" -> b.tMin = parseDouble(v);
                case "TMP" -> b.temps.add(parseDouble(v));
                case "POP" -> b.pop = Math.max(b.pop == null ? 0 : b.pop, (int) parseDouble(v));
                case "PCP" -> b.mm = Math.max(b.mm == null ? 0.0 : b.mm, parsePcp(v));
                case "REH" -> b.humidity.add(parseDouble(v));
                case "SKY" -> b.skyCounts.merge(sky(v), 1, Integer::sum);
                case "PTY" -> { if (!"0".equals(v)) b.pty = pty(v); }
                default -> { }
            }
        }
    }

    /** 발표 시각 중 현재보다 이전인 가장 최근 것. 02시 이전이면 전날 23시. */
    static LocalDateTime latestBase(LocalDateTime now) {
        for (int h : BASE_HOURS) {
            // 발표 후 자료 생성에 10분쯤 걸리므로 여유를 둔다
            if (now.getHour() > h || (now.getHour() == h && now.getMinute() >= 10)) {
                return now.withHour(h).withMinute(0);
            }
        }
        return now.minusDays(1).withHour(23).withMinute(0);
    }

    // ── 중기예보 (+5일 이후) ────────────────────────────────────────────────

    private void readMidTerm(Map<LocalDate, DayBuilder> byDate) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String tmFc = now.getHour() >= 18
                ? now.format(YMD) + "1800"
                : now.getHour() >= 6 ? now.format(YMD) + "0600" : now.minusDays(1).format(YMD) + "1800";
        LocalDate announced = LocalDate.parse(tmFc.substring(0, 8), YMD);

        JsonNode land = first(get(MID_LAND, Map.of("regId", MID_LAND_REG, "tmFc", tmFc,
                "numOfRows", "10", "pageNo", "1")));
        JsonNode ta = first(get(MID_TA, Map.of("regId", MID_TA_REG, "tmFc", tmFc,
                "numOfRows", "10", "pageNo", "1")));

        for (int n = 3; n <= 10; n++) {
            LocalDate d = announced.plusDays(n);
            DayBuilder b = byDate.get(d);
            if (b == null || b.hasShortTerm()) continue;   // 단기예보가 이미 채웠으면 건드리지 않음

            // 8일차부터는 오전/오후 구분 없이 하루 단위로 발표된다
            JsonNode am = land.get("wf" + n + "Am");
            JsonNode pm = land.get("wf" + n + "Pm");
            JsonNode whole = land.get("wf" + n);
            String condition = text(pm, text(am, text(whole, null)));
            if (condition != null) b.condition = condition;

            Integer popAm = intOrNull(land.get("rnSt" + n + "Am"));
            Integer popPm = intOrNull(land.get("rnSt" + n + "Pm"));
            Integer popWhole = intOrNull(land.get("rnSt" + n));
            Integer pop = max(popAm, popPm, popWhole);
            if (pop != null) b.pop = pop;

            Double min = doubleOrNull(ta.get("taMin" + n));
            Double max = doubleOrNull(ta.get("taMax" + n));
            if (min != null) b.tMin = min;
            if (max != null) b.tMax = max;
            b.wide = true;
        }
    }

    // ── HTTP ───────────────────────────────────────────────────────────────

    private JsonNode get(String url, Map<String, String> params) throws Exception {
        StringBuilder q = new StringBuilder(url)
                .append("?serviceKey=").append(URLEncoder.encode(serviceKey, StandardCharsets.UTF_8))
                .append("&dataType=JSON");
        params.forEach((k, v) -> q.append('&').append(k).append('=')
                .append(URLEncoder.encode(v, StandardCharsets.UTF_8)));

        HttpRequest req = HttpRequest.newBuilder(URI.create(q.toString()))
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());

        JsonNode root = mapper.readTree(res.body());
        String code = root.path("response").path("header").path("resultCode").asText();
        if (!"00".equals(code)) {
            throw new IllegalStateException("기상청 응답 코드 " + code + " "
                    + root.path("response").path("header").path("resultMsg").asText());
        }
        return root.path("response").path("body").path("items").path("item");
    }

    private static JsonNode first(JsonNode items) {
        return items.isArray() && !items.isEmpty() ? items.get(0) : items;
    }

    // ── 값 변환 ─────────────────────────────────────────────────────────────

    /** 하루치 조각을 모아 WeatherDay 로 만든다. 비어 있는 값은 예시 값으로 메운다. */
    private static final class DayBuilder {
        Double tMax, tMin, mm;
        Integer pop;
        String condition, pty;
        boolean wide;                 // 중기예보(광역)로 채워졌는지
        final List<Double> temps = new ArrayList<>();
        final List<Double> humidity = new ArrayList<>();
        final Map<String, Integer> skyCounts = new LinkedHashMap<>();

        boolean hasShortTerm() {
            return !temps.isEmpty() || tMax != null || tMin != null;
        }

        WeatherDay toWeatherDay(LocalDate date, WeatherDay demo) {
            boolean real = hasShortTerm() || wide;
            Double max = tMax != null ? tMax : orNull(temps.stream().mapToDouble(Double::doubleValue).max());
            Double min = tMin != null ? tMin : orNull(temps.stream().mapToDouble(Double::doubleValue).min());
            String cond = pty != null ? pty
                    : condition != null ? condition
                    : skyCounts.entrySet().stream().max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey).orElse(null);
            Integer reh = humidity.isEmpty() ? null
                    : (int) Math.round(humidity.stream().mapToDouble(Double::doubleValue).average().orElse(0));

            return new WeatherDay(date,
                    MockWeatherProvider.DOW[date.getDayOfWeek().getValue() - 1],
                    cond != null ? cond : demo.condition(),
                    max != null ? max : demo.tempMax(),
                    min != null ? min : demo.tempMin(),
                    pop != null ? pop : demo.precipitationProbability(),
                    mm != null ? mm : (real ? 0.0 : demo.precipitationMm()),
                    reh != null ? reh : (real ? null : demo.humidity()),   // 중기예보에는 습도가 없다
                    !real,                // 실제 예보로 채워졌으면 데모 아님
                    SourceCatalog.WEATHER_API_ID);
        }
    }

    private static Double orNull(OptionalDouble v) {
        return v.isPresent() ? v.getAsDouble() : null;
    }

    private static String sky(String code) {
        return switch (code) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> "맑음";
        };
    }

    private static String pty(String code) {
        return switch (code) {
            case "1", "5" -> "비";
            case "2", "6" -> "비/눈";
            case "3", "7" -> "눈";
            case "4" -> "소나기";
            default -> null;
        };
    }

    /** 강수량은 "1.0mm", "강수없음", "30.0~50.0mm" 같은 문자열로 온다. */
    private static double parsePcp(String v) {
        if (v == null || v.isBlank() || v.contains("없음")) return 0.0;
        String n = v.replaceAll("[^0-9.~]", "");
        if (n.contains("~")) n = n.substring(n.lastIndexOf('~') + 1);
        try {
            return n.isBlank() ? 0.0 : Double.parseDouble(n);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static double parseDouble(String v) {
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String text(JsonNode n, String fallback) {
        return n == null || n.isNull() || n.asText().isBlank() ? fallback : n.asText();
    }

    private static Integer intOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.asInt();
    }

    private static Double doubleOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.asDouble();
    }

    private static Integer max(Integer... values) {
        Integer best = null;
        for (Integer v : values) if (v != null && (best == null || v > best)) best = v;
        return best;
    }
}
