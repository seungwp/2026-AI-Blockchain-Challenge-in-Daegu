package com.golmok.oneweek.provider;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** 격자 변환과 발표시각 선택. 둘 중 하나만 틀려도 엉뚱한 지역·시점의 예보를 받는다. */
class KmaGridTest {

    @Test
    void 대구시청_좌표가_격자_89_91_로_변환된다() {
        KmaGrid.Point p = KmaGrid.of(35.8714, 128.6014);   // 대구시청
        assertEquals(89, p.nx());
        assertEquals(91, p.ny());
    }

    @Test
    void 서울_좌표는_격자_60_127_이다() {
        KmaGrid.Point p = KmaGrid.of(37.5665, 126.9780);   // 서울시청
        assertEquals(60, p.nx());
        assertEquals(127, p.ny());
    }

    @Test
    void 대구_안에서도_동네마다_격자가_갈린다() {
        KmaGrid.Point duryu = KmaGrid.of(35.8521, 128.55481);   // 달서구 두류동
        KmaGrid.Point gyo = KmaGrid.of(35.873247, 128.597138);  // 중구 교동

        // 5km 격자라 같은 대구여도 동네가 다르면 격자가 달라진다 (구·군별 예보의 근거)
        assertNotEquals(duryu, gyo);
        assertEquals(new KmaGrid.Point(88, 90), duryu);
        assertEquals(new KmaGrid.Point(89, 91), gyo);

        // 그래도 같은 도시라 멀지는 않다
        assertTrue(Math.abs(duryu.nx() - gyo.nx()) <= 2 && Math.abs(duryu.ny() - gyo.ny()) <= 2);
    }

    @Test
    void 발표시각은_현재보다_이전의_가장_최근_시각이다() {
        assertEquals(14, KmaWeatherProvider.latestBase(LocalDateTime.of(2026, 9, 16, 16, 30)).getHour());
        assertEquals(17, KmaWeatherProvider.latestBase(LocalDateTime.of(2026, 9, 16, 17, 30)).getHour());
        assertEquals(14, KmaWeatherProvider.latestBase(LocalDateTime.of(2026, 9, 16, 17, 5)).getHour());

        LocalDateTime early = KmaWeatherProvider.latestBase(LocalDateTime.of(2026, 9, 16, 1, 0));
        assertEquals(23, early.getHour());
        assertEquals(15, early.getDayOfMonth());
    }
}
