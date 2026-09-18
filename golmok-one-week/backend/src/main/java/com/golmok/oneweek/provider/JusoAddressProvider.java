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

/** 주소 검색만 연동한다. 좌표제공 API는 승인 전이므로 호출하거나 좌표를 추정하지 않는다. */
@Component
public class JusoAddressProvider {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    @Value("${golmok.keys.juso-search-api-key:}") private String apiKey;

    public record Address(String roadAddress, String address, String district) {}

    public List<Address> search(String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.length() > 200)
            throw new IllegalArgumentException("주소는 1~200자로 입력해주세요.");
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("주소 검색을 사용할 수 없습니다. 등록된 가게 검색을 이용해주세요.");
        try {
            String url = "https://business.juso.go.kr/addrlink/addrLinkApi.do?resultType=json&currentPage=1&countPerPage=20"
                    + "&confmKey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                    + "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            var req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw new IllegalStateException();
            return parse(mapper.readTree(res.body()));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            // 인증키가 담긴 요청 URL/외부 응답/예외 메시지는 전달하지 않는다.
            throw new IllegalStateException("주소 검색에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    static List<Address> parse(JsonNode root) {
        var results = root.path("results");
        if (!"0".equals(results.path("common").path("errorCode").asText()))
            throw new IllegalArgumentException("주소 검색에 실패했습니다. 도로명과 건물번호를 확인해주세요.");
        if (results.path("common").path("totalCount").asInt() > 20)
            throw new IllegalArgumentException("주소가 많이 검색되었습니다. 대구 지역명·도로명·건물번호를 입력해주세요.");
        List<Address> out = new ArrayList<>();
        for (var row : results.path("juso")) {
            if (!"대구광역시".equals(row.path("siNm").asText())) continue;
            String road = row.path("roadAddrPart1").asText();
            if (!road.isBlank()) out.add(new Address(road, row.path("jibunAddr").asText(), row.path("sggNm").asText()));
        }
        return List.copyOf(out);
    }
}
