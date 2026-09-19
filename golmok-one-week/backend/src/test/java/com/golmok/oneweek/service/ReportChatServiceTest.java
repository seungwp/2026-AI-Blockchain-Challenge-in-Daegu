package com.golmok.oneweek.service;

import com.golmok.oneweek.dto.ReportDtos.ReportResponse;
import com.golmok.oneweek.dto.ReportDtos.WeatherDay;
import com.golmok.oneweek.dto.StoreResponse;
import com.golmok.oneweek.entity.Enums.MenuCategory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ReportChatServiceTest {

    @Test
    void apiKey가_없으면_외부_호출_없이_안내한다() throws Exception {
        var client = mock(GroqChatClient.class);
        when(client.hasKey()).thenReturn(false);

        String answer = new ReportChatService(client).ask(report(), "비 오는 날은 언제예요?", List.of());

        assertEquals("죄송합니다. 지금은 챗봇을 사용할 수 없습니다.", answer);
        verify(client, never()).complete(anyList(), anyDouble(), anyInt(), any(Duration.class));
    }

    @Test
    void 리포트_사실만_사용하는_응답을_반환하고_성과수치_거절정책을_전달한다() throws Exception {
        var client = mock(GroqChatClient.class);
        when(client.hasKey()).thenReturn(true);
        when(client.complete(anyList(), anyDouble(), anyInt(), any(Duration.class)))
                .thenReturn("9월 20일에는 비 예보가 있습니다. 포장 준비를 점검해보세요.");

        String answer = new ReportChatService(client).ask(report(), "매출이 20% 오를까요?", List.of());

        assertEquals("9월 20일에는 비 예보가 있습니다. 포장 준비를 점검해보세요.", answer);
        @SuppressWarnings("unchecked")
        var messages = (List<Map<String, String>>) mockingDetails(client).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("complete"))
                .findFirst().orElseThrow().getArgument(0);
        assertTrue(messages.getFirst().get("content").contains("성과의 구체적인 수치"));
    }

    private ReportResponse report() {
        LocalDate day = LocalDate.of(2026, 9, 20);
        return new ReportResponse(1L,
                new StoreResponse(1L, "테스트 치킨", "치킨", "대구광역시 중구", null,
                        35.87, 128.60, "대구광역시", "중구", false),
                "후라이드치킨", MenuCategory.CHICKEN, day, day.plusDays(6), "비 예보가 있습니다.", null,
                List.of(), List.of(new WeatherDay(day, "일", "비", 25.0, 18.0, 80,
                        2.0, 70, false, 6L)), null, List.of(), List.of(), List.of(), List.of(),
                false, null, "운영 참고용");
    }
}
