# 골목 한 주 — 프론트엔드

Next.js(App Router) · TypeScript · Axios · React Hook Form + Zod
스타일은 `app/globals.css` 최소 CSS만 사용합니다. **디자인은 하지 않았습니다.**

## 실행

```bash
cp .env.local.example .env.local
npm install
npm run dev      # http://localhost:3000
npm run build && npm run start
```

`NEXT_PUBLIC_API_BASE_URL` 기본값은 `http://localhost:8080` 입니다. **백엔드를 안 띄워도** `lib/mock.ts` 데모 데이터로 전체 화면이 동작합니다.

## 노트북 켤 때마다 (작업 순서)

**최초 1회만:**
```bash
git clone https://github.com/seungwp/2026-AI-Blockchain-Challenge-in-Daegu.git
cd 2026-AI-Blockchain-Challenge-in-Daegu/golmok-one-week/frontend
cp .env.local.example .env.local
npm install
git checkout -b frontend/design origin/main   # 본인 작업 브랜치 생성
```

**이후 작업할 때마다:**
1. `git checkout frontend/design` (없으면 위 최초 1회 과정으로 생성)
2. `git pull origin main` → 백엔드 쪽 변경사항(API 필드 추가 등)을 받아서 브랜치에 합침 (충돌 나면 해결 후 커밋)
3. `npm install` — `package.json`이 바뀐 적 있을 때만 (평소엔 생략 가능)
4. `npm run dev` 로 `http://localhost:3000` 접속해서 작업 (백엔드는 안 띄워도 됨)
5. 작업한 만큼 커밋: `git add <파일>` → `git commit -m "설명"`
6. `git push origin frontend/design` (최초 push는 `-u` 옵션 추가: `git push -u origin frontend/design`)
7. **다 됐다고 main에 반영하고 싶으면**: GitHub에서 이 브랜치로 "Compare & pull request" 클릭 → base가 `main`인지 확인 → PR 생성 → 리뷰·merge는 백엔드 담당이 처리

**주의:** `main`에는 절대 직접 push하지 않습니다(PR로만 반영). 다른 사람 브랜치도 강제로 push(force-push)하지 않습니다.

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
