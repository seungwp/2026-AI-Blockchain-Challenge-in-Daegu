package com.golmok.oneweek.dto;

import com.golmok.oneweek.entity.Store;

public record StoreResponse(Long id, String name, String category, String address, String roadAddress,
                            Double latitude, Double longitude, String city, String district, boolean isDemoData) {
    public static StoreResponse from(Store s) {
        return new StoreResponse(s.getId(), s.getName(), s.getCategory(), s.getAddress(), s.getRoadAddress(),
                s.getLatitude(), s.getLongitude(), s.getCity(), s.getDistrict(), s.isDemoData());
    }
}
