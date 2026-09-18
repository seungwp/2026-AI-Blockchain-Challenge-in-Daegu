package com.golmok.oneweek.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** 네이버 Maps Geocoding으로 대구 주소와 WGS84 좌표를 함께 확인한다. */
@Component
public class NaverGeocodingProvider {
    private static final String GEOCODING_URL = "https://maps.apigw.ntruss.com/map-geocode/v2/geocode";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    @Value("${golmok.keys.naver-map-client-id:}") private String clientId;
    @Value("${golmok.keys.naver-map-client-secret:}") private String clientSecret;

    public record Address(String roadAddress, String address, String district, double latitude, double longitude) {}

    public List<Address> search(String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.length() > 200)
            throw new IllegalArgumentException("주소는 1~200자로 입력해주세요.");
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank())
            throw new IllegalStateException("주소 검색을 사용할 수 없습니다. 등록된 가게 검색을 이용해주세요.");
        try {
            String url = GEOCODING_URL + "?count=20&language=kor&query="
                    + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            var req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .header("x-ncp-apigw-api-key-id", clientId)
                    .header("x-ncp-apigw-api-key", clientSecret)
                    .GET().build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw new IllegalStateException();
            return parse(mapper.readTree(res.body()));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            // 인증 헤더·외부 응답·예외 메시지는 전달하지 않는다.
            throw new IllegalStateException("주소 검색에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    static List<Address> parse(JsonNode root) {
        if (!"OK".equals(root.path("status").asText()))
            throw new IllegalArgumentException("주소 검색에 실패했습니다. 도로명과 건물번호를 확인해주세요.");
        List<Address> out = new ArrayList<>();
        for (var row : root.path("addresses")) {
            String road = row.path("roadAddress").asText();
            String jibun = row.path("jibunAddress").asText();
            String fullAddress = !road.isBlank() ? road : jibun;
            if (!fullAddress.startsWith("대구광역시")) continue;
            double latitude = row.path("y").asDouble(Double.NaN);
            double longitude = row.path("x").asDouble(Double.NaN);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) continue;
            out.add(new Address(road, jibun, district(row), latitude, longitude));
        }
        return List.copyOf(out);
    }

    private static String district(JsonNode row) {
        for (var element : row.path("addressElements")) {
            for (var type : element.path("types")) {
                if ("SIGUGUN".equals(type.asText())) return element.path("longName").asText();
            }
        }
        return "";
    }
}
