package com.golmok.oneweek.rule;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 행사 거리 구간 파싱 검증.
 * "1000"(1km 이내)과 "1000-3000"(1km 초과 3km 이내)이 같은 행사에 중복으로 걸리면 안 된다.
 */
class FestivalDistanceBandTest {

    private int[] band(String value) throws Exception {
        Method m = WeeklyGuideRuleEngine.class.getDeclaredMethod("distanceBand", String.class);
        m.setAccessible(true);
        return (int[]) m.invoke(null, value);
    }

    private boolean matches(int[] b, int meters) {
        return meters > b[0] && meters <= b[1];
    }

    @Test
    void 두_구간은_겹치지_않는다() throws Exception {
        int[] near = band("1000");
        int[] far = band("1000-3000");
        for (int d : new int[]{0, 1, 300, 999, 1000, 1001, 1224, 2249, 3000, 3001, 12089}) {
            assertFalse(matches(near, d) && matches(far, d), d + "m 가 두 구간에 동시에 걸림");
        }
    }

    @Test
    void 경계값이_맞다() throws Exception {
        int[] near = band("1000");
        int[] far = band("1000-3000");

        assertTrue(matches(near, 0));
        assertTrue(matches(near, 1000));
        assertFalse(matches(near, 1001));

        assertFalse(matches(far, 1000));
        assertTrue(matches(far, 1001));
        assertTrue(matches(far, 3000));
        assertFalse(matches(far, 3001));

        // 실데이터 회귀: 대구메이커페스타는 교동 1224m, 평화시장 2249m → 간접 구간에만 걸린다
        assertTrue(matches(far, 1224) && matches(far, 2249));
        // 달성 현대미술제 12km 는 어느 구간에도 안 걸린다
        assertFalse(matches(near, 12089) || matches(far, 12089));
    }

    @Test
    void 잘못된_값은_기본값으로_떨어진다() throws Exception {
        assertArrayEquals(new int[]{-1, 1000}, band(null));
        assertArrayEquals(new int[]{-1, 1000}, band("abc"));
        assertArrayEquals(new int[]{0, 3000}, band("x-y"));
    }
}
