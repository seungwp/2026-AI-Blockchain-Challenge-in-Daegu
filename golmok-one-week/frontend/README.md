# 골목 한 주 — 프론트엔드

Next.js(App Router) · TypeScript · Axios · React Hook Form + Zod
스타일은 `app/globals.css` 최소 CSS만 사용합니다. **디자인 시스템을 만들지 않습니다.**

## 실행

```bash
cp .env.local.example .env.local
npm install
npm run dev      # http://localhost:3000
npm run build && npm run start
```

`NEXT_PUBLIC_API_BASE_URL` 기본값은 `http://localhost:8080` 입니다.

## 구조

```
app/
├── page.tsx                          시작 페이지
├── search/page.tsx                   가게 검색 + 주소 직접 입력
├── store/[storeId]/confirm/page.tsx  가게 확인·대표 메뉴·자동 분류
└── report/[reportId]/page.tsx        이번 주 운영 가이드
components/
├── layout/   Header, Footer
├── search/   StoreSearchForm, StoreResultList
├── store/    MenuCategorySelector
├── report/   ReportSummary, RecommendationList, WeatherList, CommercialAreaSummary,
│             FestivalList, DailyGuideList, SourceList
└── common/   StateMessage, DemoBadge, Disclaimer
lib/
├── api.ts    백엔드 호출 (실패 시 mock 폴백)
└── mock.ts   백엔드 없이 동작시키는 데모 데이터
types/index.ts  API 타입 (이름·필드 변경 금지)
```

## 규칙

- 비즈니스 판단(권고 생성)은 백엔드가 합니다. 프론트는 입력·요청·표시만 합니다.
- 백엔드 호출이 실패하면 `lib/mock.ts` 데이터로 폴백하고, 리포트 상단에 안내 문구를 노출합니다.
- `isDemoData` 가 true면 "데모 데이터" 배지를 유지합니다.
- 면책 문구와 `sourceIds` 표시는 제거하지 않습니다.
- 모바일 터치 타겟 44px 이상을 유지합니다.

디자인 교체 시 주의사항은 [`../docs/handoff-to-design.md`](../docs/handoff-to-design.md) 를 참고하세요.
