package com.golmok.oneweek.service;

import com.golmok.oneweek.entity.Enums.Confidence;
import com.golmok.oneweek.entity.Enums.MenuCategory;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 키워드가 먼저, LLM은 키워드가 없을 때만. LLM 응답은 카테고리 코드와 정확히 같을 때만 채택한다. */
class MenuClassificationServiceTest {

    @Test
    void 키워드에_걸리면_LLM을_호출하지_않는다() throws Exception {
        var client = mock(GroqChatClient.class);
        when(client.hasKey()).thenReturn(true);

        var res = new MenuClassificationService(client).classify("양념치킨", "");

        assertEquals(MenuCategory.CHICKEN, res.menuCategory());
        verify(client, never()).complete(anyList(), anyDouble(), anyInt(), any(Duration.class));
    }

    @Test
    void 키워드가_없으면_LLM이_고른_카테고리를_쓴다() throws Exception {
        var client = mock(GroqChatClient.class);
        when(client.hasKey()).thenReturn(true);
        when(client.complete(anyList(), anyDouble(), anyInt(), any(Duration.class))).thenReturn(" CHINESE\n");

        var res = new MenuClassificationService(client).classify("깐풍기", "");

        assertEquals(MenuCategory.CHINESE, res.menuCategory());
        assertEquals(Confidence.MEDIUM, res.confidence());
    }

    @Test
    void LLM_응답이_코드가_아니면_기타로_둔다() throws Exception {
        var client = mock(GroqChatClient.class);
        when(client.hasKey()).thenReturn(true);
        when(client.complete(anyList(), anyDouble(), anyInt(), any(Duration.class))).thenReturn("중식입니다");

        var res = new MenuClassificationService(client).classify("깐풍기", "");

        assertEquals(MenuCategory.ETC, res.menuCategory());
        assertEquals(Confidence.LOW, res.confidence());
    }
}
