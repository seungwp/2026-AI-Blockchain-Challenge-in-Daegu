package com.golmok.oneweek.provider;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 특일 정보 API가 돌려준 "추석" 날짜 목록을 노진원 외(2019) 계수 항목으로 분류한다.
 * 공식 추석 연휴는 보통 3일(전날·당일·마지막날)이라 정렬한 목록의 처음/끝을 그대로 쓰고,
 * 대체공휴일 등으로 늘어난 중간 날짜는 정확한 음력 당일을 계산하지 않고 모두 "연휴 기간"으로 둔다.
 */
final class ChuseokClassifier {

    static final String EVE = "CHUSEOK_EVE";
    static final String DAY = "CHUSEOK_DAY";
    static final String PERIOD = "CHUSEOK_PERIOD";
    static final String LAST = "CHUSEOK_LAST";

    private ChuseokClassifier() {}

    static Map<LocalDate, String> classify(List<LocalDate> chuseokDates) {
        List<LocalDate> sorted = chuseokDates.stream().sorted().distinct().toList();
        Map<LocalDate, String> out = new LinkedHashMap<>();
        if (sorted.isEmpty()) return out;
        if (sorted.size() == 1) {
            out.put(sorted.get(0), DAY);
            return out;
        }
        for (int i = 0; i < sorted.size(); i++) {
            String code = i == 0 ? EVE
                    : i == sorted.size() - 1 ? LAST
                    : sorted.size() == 3 ? DAY
                    : PERIOD;
            out.put(sorted.get(i), code);
        }
        return out;
    }
}
