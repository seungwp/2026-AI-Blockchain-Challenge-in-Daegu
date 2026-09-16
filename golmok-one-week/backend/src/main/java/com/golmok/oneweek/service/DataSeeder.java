package com.golmok.oneweek.service;

import com.golmok.oneweek.entity.*;
import com.golmok.oneweek.entity.Enums.*;
import com.golmok.oneweek.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 기동 시 H2 에 기준 데이터를 적재한다. 원본은 모두 resources/data/*.csv 이며
 * 가게·축제·출처·규칙을 코드에 하드코딩하지 않는다.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    @Bean
    public ApplicationRunner seedData(SourceRepository sources, StoreRepository stores,
                                      FestivalEventRepository festivals, MenuRuleRepository rules) {
        return args -> {
            if (sources.count() > 0) return;
            seedSources(sources);
            seedStores(stores);
            seedFestivals(festivals);
            seedRules(rules);
            log.info("seed 완료: 출처 {}건, 가게 {}건, 행사 {}건, 규칙 {}건",
                    sources.count(), stores.count(), festivals.count(), rules.count());
        };
    }

    /**
     * 근거 출처. 원본은 resources/data/sources.csv.
     * 컬럼: id,sourceType,title,organization,authors,publicationYear,url,description,reliabilityNote
     * id 는 docs/coefficients.md 의 출처 번호와 같은 고정값이라 자동 생성하지 않는다.
     */
    private void seedSources(SourceRepository repo) {
        List<Source> out = new ArrayList<>();
        for (String[] c : readCsv("/data/sources.csv", 9)) {
            out.add(Source.builder()
                    .id(Long.parseLong(c[0]))
                    .sourceType(SourceType.valueOf(c[1]))
                    .title(c[2])
                    .organization(blankToNull(c[3]))
                    .authors(blankToNull(c[4]))
                    .publicationYear(c[5].isBlank() ? null : Integer.parseInt(c[5]))
                    .url(blankToNull(c[6]))
                    .description(blankToNull(c[7]))
                    .reliabilityNote(blankToNull(c[8]))
                    .build());
        }
        repo.saveAll(out);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    /** 따옴표로 감싼 필드를 고려한 최소 CSV 분리. */
    /**
     * 대구 음식점 실데이터. 원본은 식품의약품안전처 일반음식점 인허가 자료를
     * pipeline/export_golmok_stores.py 로 가공한 resources/data/daegu_stores.csv (영업 중, 좌표 WGS84).
     * 컬럼: name,category,address,roadAddress,district,latitude,longitude
     */
    private void seedStores(StoreRepository repo) {
        List<Store> out = new ArrayList<>();
        for (String[] c : readCsv("/data/daegu_stores.csv", 7)) {
            out.add(Store.builder()
                    .name(c[0]).category(c[1]).address(c[2]).roadAddress(c[3])
                    .city("대구광역시").district(c[4])
                    .latitude(Double.parseDouble(c[5])).longitude(Double.parseDouble(c[6]))
                    .demoData(false).build());
        }
        repo.saveAll(out);
        log.info("음식점 {}곳 seed 완료", out.size());
    }

    /**
     * 대구 축제 실데이터. 원본은 한국관광공사 TourAPI 를 pipeline/fetch_festival.py 로 수집한
     * resources/data/daegu_festivals.csv.
     * 컬럼: contentid,title,start,end,addr,lon,lat,tel,category,adm_nm
     */
    private void seedFestivals(FestivalEventRepository repo) {
        List<FestivalEvent> out = new ArrayList<>();
        for (String[] c : readCsv("/data/daegu_festivals.csv", 10)) {
            out.add(FestivalEvent.builder()
                    .name(c[1])
                    .startDate(LocalDate.parse(c[2]))
                    .endDate(LocalDate.parse(c[3]))
                    .address(c[4])
                    .longitude(Double.parseDouble(c[5]))
                    .latitude(Double.parseDouble(c[6]))
                    .locationName(c[9])
                    .description("한국관광공사 TourAPI 축제 정보")
                    .impactNote("행사 기간 인근 방문객·교통 변화 가능성을 함께 고려")
                    .sourceId(SourceCatalog.FESTIVAL_TOURAPI_ID)
                    .demoData(false).build());
        }
        repo.saveAll(out);
    }

    /**
     * 운영 가이드 규칙. 원본은 resources/data/menu_rules.csv.
     * 컬럼: menuCategory,conditionType,conditionValue,recommendationType,recommendationText,sourceId,confidence
     * sourceId 는 SourceCatalog 의 고정 ID 와 같다. 문구만 고칠 때는 CSV 만 수정하면 된다.
     */
    private void seedRules(MenuRuleRepository repo) {
        List<MenuRule> out = new ArrayList<>();
        for (String[] c : readCsv("/data/menu_rules.csv", 7)) {
            out.add(MenuRule.builder()
                    .menuCategory(MenuCategory.valueOf(c[0]))
                    .conditionType(ConditionType.valueOf(c[1]))
                    .conditionValue(c[2])
                    .recommendationType(RecommendationType.valueOf(c[3]))
                    .recommendationText(c[4])
                    .sourceId(Long.parseLong(c[5]))
                    .confidence(Confidence.valueOf(c[6]))
                    .build());
        }
        repo.saveAll(out);
    }

    /**
     * 클래스패스 CSV 를 읽어 헤더를 뺀 행들을 돌려준다.
     * 컬럼 수가 minColumns 에 못 미치거나 첫 칸이 빈 행은 건너뛴다.
     */
    private List<String[]> readCsv(String resourcePath, int minColumns) {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.warn("시드 CSV 를 찾지 못했습니다: {}", resourcePath);
                return rows;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            r.readLine(); // 헤더
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (line.isBlank()) continue;
                String[] c = splitCsv(line);
                if (c.length < minColumns || c[0].isBlank()) continue;
                rows.add(c);
            }
        } catch (Exception e) {
            log.warn("시드 CSV 로드 실패 {}: {}", resourcePath, e.toString());
        }
        return rows;
    }

    /** 따옴표로 감싼 필드와 그 안의 쉼표를 고려한 최소 CSV 분리. "" 는 따옴표 한 개로 읽는다. */
    private static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }

}
