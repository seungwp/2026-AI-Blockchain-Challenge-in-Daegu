package com.golmok.oneweek.service;

import com.golmok.oneweek.entity.Store;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/** 실주소는 번지·건물명으로 끝나므로 동 이름을 마지막 토큰으로 뽑으면 안 된다. */
class DongOfTest {

    private String dong(String address, String district) throws Exception {
        Method m = CommercialAreaService.class.getDeclaredMethod("dongOf", Store.class);
        m.setAccessible(true);
        Store s = Store.builder().address(address).district(district).build();
        return (String) m.invoke(new CommercialAreaService(null), s);
    }

    @Test
    void 번지로_끝나는_주소에서_동을_뽑는다() throws Exception {
        assertEquals("두류동", dong("대구광역시 달서구 두류동 620-12", "달서구"));
        assertEquals("대명동", dong("대구광역시 남구 대명동 817-1", "남구"));
        assertEquals("교동", dong("대구광역시 중구 교동 0068-0002 지상1층", "중구"));
    }

    @Test
    void 읍면_가_리도_인식한다() throws Exception {
        assertEquals("다사읍", dong("대구광역시 달성군 다사읍 세천리 1", "달성군"));
        assertEquals("동성로2가", dong("대구광역시 중구 동성로2가 165-1", "중구"));
    }

    @Test
    void 구_이름은_동으로_보지_않는다() throws Exception {
        assertNotEquals("달서구", dong("대구광역시 달서구 두류동 620-12", "달서구"));
        assertNull(dong(null, "중구"));
    }
}
