package com.golmok.oneweek.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.golmok.oneweek.entity.Store;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** 수집 스크립트가 검증 후 저장한 API 스냅샷. 원본 키/관리번호는 포함하지 않는다. */
@Component
public class PublicDataSnapshots {
    private final JsonNode shops;
    private final JsonNode festivals;
    private final JsonNode prices;
    private final Map<String, JsonNode> storeIndex = new HashMap<>();

    public PublicDataSnapshots(ObjectMapper mapper) {
        shops = read(mapper, "store_enrichment.json");
        festivals = read(mapper, "festival_details.json");
        prices = read(mapper, "ingredient_history.json");
        shops.path("items").forEach(row -> storeIndex.put(key(row.path("name").asText(),
                row.path("roadAddress").asText(), row.path("address").asText()), row));
    }

    private JsonNode read(ObjectMapper mapper, String file) {
        try (InputStream in = getClass().getResourceAsStream("/data/" + file)) {
            return in == null ? mapper.createObjectNode() : mapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("공공데이터 스냅샷 파싱 실패: " + file, e);
        }
    }

    private static String key(String name, String road, String address) {
        return name + "\n" + road + "\n" + address;
    }

    public void enrich(Store store) {
        JsonNode row = storeIndex.get(key(store.getName(), store.getRoadAddress(), store.getAddress()));
        if (row == null) return;
        store.setDetailCategoryCode(row.path("detailCode").asText());
        store.setDetailCategoryName(row.path("detailName").asText());
        store.setCategoryAsOf(shops.path("asOf").asText());
    }

    public JsonNode festivals() { return festivals; }
    public JsonNode prices() { return prices; }
    public LocalDate priceFetchedAt() {
        return prices.hasNonNull("fetchedAt") ? LocalDate.parse(prices.get("fetchedAt").asText()) : LocalDate.MIN;
    }
}
