package com.golmok.oneweek.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.golmok.oneweek.provider.Providers.HolidayProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 한국천문연구원 특일 정보(공휴일) 연동. "추석"으로 표시된 날짜만 뽑아
 * {@link ChuseokClassifier} 로 전날/당일/연휴기간/마지막날을 분류한다.
 * 설날 등 다른 명절은 우리 계수(노진원 외 2019, 추석 한정)가 없어 분류하지 않는다.
 * 조회 실패 시 빈 지도를 돌려주고 로그만 남긴다 — HOLIDAY 규칙이 조용히 안 켜질 뿐 화면은 그대로 뜬다.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "golmok.providers.holiday", havingValue = "kasi")
public class KasiHolidayProvider implements HolidayProvider {

    private static final String URL =
            "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${golmok.keys.data-go-kr:}")
    private String serviceKey;

    @Override
    public Map<LocalDate, String> classify(LocalDate start, LocalDate end) {
        if (serviceKey == null || serviceKey.isBlank()) {
            log.warn("DATA_GO_KR_KEY 가 없어 명절 정보를 건너뜁니다.");
            return Map.of();
        }
        try {
            List<LocalDate> chuseok = new ArrayList<>();
            for (int year = start.getYear(); year <= end.getYear(); year++) {
                chuseok.addAll(fetchChuseokDates(year));
            }
            Map<LocalDate, String> classified = ChuseokClassifier.classify(chuseok);
            classified.keySet().removeIf(d -> d.isBefore(start) || d.isAfter(end));
            return classified;
        } catch (Exception e) {
            log.warn("명절 정보 조회 실패: {}", e.toString());
            return Map.of();
        }
    }

    private List<LocalDate> fetchChuseokDates(int year) throws Exception {
        StringBuilder q = new StringBuilder(URL)
                .append("?serviceKey=").append(URLEncoder.encode(serviceKey, StandardCharsets.UTF_8))
                .append("&solYear=").append(year)
                .append("&numOfRows=100&_type=json");

        HttpRequest req = HttpRequest.newBuilder(URI.create(q.toString()))
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());

        JsonNode root = mapper.readTree(res.body());
        String code = root.path("response").path("header").path("resultCode").asText();
        if (!"00".equals(code)) {
            throw new IllegalStateException("특일정보 응답 코드 " + code);
        }

        List<LocalDate> out = new ArrayList<>();
        JsonNode items = root.path("response").path("body").path("items").path("item");
        for (JsonNode it : items) {
            if ("추석".equals(it.path("dateName").asText())) {
                out.add(LocalDate.parse(it.path("locdate").asText(), YMD));
            }
        }
        return out;
    }
}
