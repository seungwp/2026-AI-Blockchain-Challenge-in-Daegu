package com.golmok.oneweek.controller;

import com.golmok.oneweek.dto.ChatDtos.ChatRequest;
import com.golmok.oneweek.dto.ChatDtos.ChatResponse;
import com.golmok.oneweek.dto.MenuDtos.ClassifyRequest;
import com.golmok.oneweek.dto.MenuDtos.ClassifyResponse;
import com.golmok.oneweek.dto.ReportDtos.CreateRequest;
import com.golmok.oneweek.dto.ReportDtos.ReportResponse;
import com.golmok.oneweek.dto.SourceResponse;
import com.golmok.oneweek.dto.StoreResponse;
import com.golmok.oneweek.exception.NotFoundException;
import com.golmok.oneweek.repository.SourceRepository;
import com.golmok.oneweek.service.MenuClassificationService;
import com.golmok.oneweek.service.ReportService;
import com.golmok.oneweek.service.StoreService;
import com.golmok.oneweek.provider.JusoAddressProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** REST 컨트롤러 모음. */
public final class ApiControllers {
    @RestController
    @RequestMapping("/api/addresses")
    @RequiredArgsConstructor
    @Tag(name = "Address")
    public static class AddressController {
        private final JusoAddressProvider provider;

        @GetMapping("/search")
        @Operation(summary = "대구 도로명주소 검색 (좌표제공 API 승인 전에는 좌표 미제공)")
        public List<JusoAddressProvider.Address> search(@RequestParam String keyword) {
            return provider.search(keyword);
        }
    }
    private ApiControllers() {}

    @RestController
    @RequestMapping("/api/health")
    @Tag(name = "Health")
    public static class HealthController {
        @GetMapping
        @Operation(summary = "헬스 체크")
        public Map<String, String> health() {
            return Map.of("status", "ok");
        }
    }

    @RestController
    @RequestMapping("/api/stores")
    @RequiredArgsConstructor
    @Tag(name = "Store")
    public static class StoreController {
        private final StoreService storeService;

        @GetMapping("/search")
        @Operation(summary = "상호명 또는 주소로 가게 검색")
        public List<StoreResponse> search(@RequestParam String keyword,
                                          @RequestParam(defaultValue = "대구광역시") String city) {
            if (keyword.isBlank()) throw new IllegalArgumentException("상호명 또는 주소를 입력해주세요.");
            return storeService.search(keyword, city);
        }

        @GetMapping("/{storeId}")
        @Operation(summary = "가게 단건 조회")
        public StoreResponse get(@PathVariable Long storeId) {
            return storeService.get(storeId);
        }

        @PostMapping("/manual")
        @Operation(summary = "검색 결과가 없을 때 주소 직접 입력으로 가게 생성")
        @ResponseStatus(HttpStatus.CREATED)
        public StoreResponse manual(@RequestBody Map<String, String> body) {
            String address = body.get("address");
            if (address == null || address.isBlank()) throw new IllegalArgumentException("주소를 입력해주세요.");
            return storeService.createFromAddress(address.trim(), body.getOrDefault("city", "대구광역시"));
        }
    }

    @RestController
    @RequestMapping("/api/menu")
    @RequiredArgsConstructor
    @Tag(name = "Menu")
    public static class MenuController {
        private final MenuClassificationService menuClassificationService;

        @PostMapping("/classify")
        @Operation(summary = "대표 메뉴명으로 메뉴 카테고리 자동 분류")
        public ClassifyResponse classify(@Valid @RequestBody ClassifyRequest request) {
            return menuClassificationService.classify(request.menuName(), request.storeCategory());
        }
    }

    @RestController
    @RequestMapping("/api/reports")
    @RequiredArgsConstructor
    @Tag(name = "Report")
    public static class ReportController {
        private final ReportService reportService;

        @PostMapping
        @Operation(summary = "이번 주 운영 가이드 리포트 생성")
        public ResponseEntity<ReportResponse> create(@Valid @RequestBody CreateRequest request) {
            ReportResponse response = reportService.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @GetMapping("/{reportId}")
        @Operation(summary = "리포트 조회 (새로고침 시 동일 결과)")
        public ReportResponse get(@PathVariable Long reportId) {
            return reportService.get(reportId);
        }

        @PostMapping("/{reportId}/chat")
        @Operation(summary = "리포트 데이터를 근거로 사장님 질문에 답하는 챗봇")
        public ChatResponse chat(@PathVariable Long reportId, @Valid @RequestBody ChatRequest request) {
            return new ChatResponse(reportService.chat(reportId, request));
        }
    }

    @RestController
    @RequestMapping("/api/sources")
    @RequiredArgsConstructor
    @Tag(name = "Source")
    public static class SourceController {
        private final SourceRepository sourceRepository;

        @GetMapping
        @Operation(summary = "전체 출처 목록")
        public List<SourceResponse> all() {
            return sourceRepository.findAll().stream().map(SourceResponse::from).toList();
        }

        @GetMapping("/{sourceId}")
        @Operation(summary = "출처 단건 조회")
        public SourceResponse get(@PathVariable Long sourceId) {
            return sourceRepository.findById(sourceId).map(SourceResponse::from)
                    .orElseThrow(() -> new NotFoundException("해당 출처를 찾을 수 없습니다. id=" + sourceId));
        }
    }
}
