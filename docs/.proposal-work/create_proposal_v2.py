# 붙임4 제안요약서 v2 — 평가항목(실용성·기술완성도·사업화·충실도) 반영 + 실제 화면 캡처 포함
# 실행: py -X utf8 docs/.proposal-work/create_proposal_v2.py   (저장소 루트에서)
import os

from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

HERE = os.path.dirname(os.path.abspath(__file__))
# 서식·표·글꼴 헬퍼는 v1 스크립트를 그대로 재사용한다(문서 본문 생성 직전까지).
_src = open(os.path.join(HERE, "create_proposal_docx.py"), encoding="utf-8").read()
_helpers = _src.split("doc = Document()")[0]
_helpers = _helpers.replace("set_font(r, 8.8)", "set_font(r, 8.4)")  # 표 본문
_helpers = _helpers.replace("set_font(r, 9.2, True, \"FFFFFF\")", "set_font(r, 8.8, True, \"FFFFFF\")")  # 표 머리
_helpers = _helpers.replace("line=1.25)", "line=1.18)")
exec(_helpers)  # noqa: S102

# Word로 파일을 열어 둔 상태에서도 작업할 수 있게 출력 경로를 환경변수로 바꿀 수 있다.
OUT = os.environ.get("PROPOSAL_OUT") or os.path.join(HERE, "..", "붙임4_제안요약서_최종.docx")
FIG = os.path.join(HERE, "..", "figures")
FLOW = os.path.join(HERE, "..", "..", "golmok-one-week", "docs", "figures", "service-flow.png")
URL = "www.blackapple.store"


# 5장 내외로 맞추기 위해 v1보다 촘촘한 간격을 쓴다.
def add_text(doc, text, bold=False, size=9.7, color=None, before=0, after=3, line=1.28):
    p = doc.add_paragraph()
    format_paragraph(p, before, after, line)
    set_font(p.add_run(text), size, bold, color)
    return p


def add_heading(doc, text, level=1):
    p = doc.add_paragraph()
    format_paragraph(p, before=7 if level == 1 else 5, after=3, line=1.05)
    set_font(p.add_run(text), 12.5 if level == 1 else 10.5, True, NAVY)
    p.paragraph_format.keep_with_next = True
    return p


def add_bullet(doc, text):
    p = doc.add_paragraph(style="List Bullet")
    format_paragraph(p, after=2, line=1.25)
    set_font(p.add_run(text), 9.4)
    return p


def add_picture(doc, path, width_cm, caption=None):
    p = doc.add_paragraph()
    format_paragraph(p, before=4, after=2, align=WD_ALIGN_PARAGRAPH.CENTER)
    p.add_run().add_picture(path, width=Cm(width_cm))
    if caption:
        c = doc.add_paragraph()
        format_paragraph(c, after=8, align=WD_ALIGN_PARAGRAPH.CENTER)
        set_font(c.add_run(caption), 8.6, False, GRAY)


def add_callout(doc, title, body):
    """강조 박스(1×1 표)."""
    t = doc.add_table(rows=1, cols=1)
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = t.rows[0].cells[0]
    set_cell_shading(cell, BLUE)
    set_cell_margin(cell, 120, 160, 120, 160)
    cell.width = Cm(16.0)
    p = cell.paragraphs[0]
    format_paragraph(p, after=0, line=1.3)
    set_font(p.add_run(title + "  "), 10.2, True, NAVY)
    set_font(p.add_run(body), 9.4)
    doc.add_paragraph().paragraph_format.space_after = Pt(1)


def shot_grid(doc, items, per_row=3, width_cm=3.3):
    """화면 캡처 격자 (이미지 + 설명)."""
    rows = [items[i:i + per_row] for i in range(0, len(items), per_row)]
    for row in rows:
        t = doc.add_table(rows=1, cols=per_row)
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        t.autofit = False
        for i in range(per_row):
            cell = t.rows[0].cells[i]
            cell.width = Cm(width_cm + 0.6)
            set_cell_margin(cell, 40, 40, 40, 40)
            if i >= len(row):
                continue
            name, caption = row[i]
            # 이미지와 설명을 한 칸에 넣어 쪽 넘김에서 갈라지지 않게 한다.
            p = cell.paragraphs[0]
            format_paragraph(p, after=2, align=WD_ALIGN_PARAGRAPH.CENTER)
            p.add_run().add_picture(os.path.join(FIG, name), width=Cm(width_cm))
            p2 = cell.add_paragraph()
            format_paragraph(p2, after=0, line=1.2, align=WD_ALIGN_PARAGRAPH.CENTER)
            set_font(p2.add_run(caption), 8.0, False, GRAY)
        t.rows[0]._tr.get_or_add_trPr().append(OxmlElement("w:cantSplit"))
        doc.add_paragraph().paragraph_format.space_after = Pt(2)


doc = Document()
section = doc.sections[0]
section.top_margin = Cm(1.3)
section.bottom_margin = Cm(1.1)
section.left_margin = Cm(1.6)
section.right_margin = Cm(1.6)

styles = doc.styles
styles["Normal"].font.name = "Malgun Gothic"
styles["Normal"]._element.rPr.rFonts.set(qn("w:eastAsia"), "Malgun Gothic")
styles["Normal"].font.size = Pt(10.2)
styles["Title"].font.name = "Malgun Gothic"
styles["Title"]._element.rPr.rFonts.set(qn("w:eastAsia"), "Malgun Gothic")
add_page_number(section.footer)

# ── 1장: 표지 + 과제요약 + 목적·필요성 ─────────────────────────────
p = doc.add_paragraph()
format_paragraph(p, after=6, align=WD_ALIGN_PARAGRAPH.CENTER)
set_font(p.add_run("붙임 4  제안 요약서"), 11, True, GRAY)

p = doc.add_paragraph(style="Title")
format_paragraph(p, before=10, after=6, line=1.2, align=WD_ALIGN_PARAGRAPH.CENTER)
set_font(p.add_run("장사메이트\n대구 음식점 소상공인을 위한 공공데이터 기반 AI 주간 운영 가이드"), 16, True, NAVY)

info = doc.add_table(rows=2, cols=4)
info.style = "Table Grid"
info.alignment = WD_TABLE_ALIGNMENT.CENTER
for row in info.rows:
    for c in row.cells:
        set_cell_margin(c)
for i, (label, width) in enumerate([("접수번호", 2.6), ("", 4.4), ("팀명", 2.2), ("노드", 6.8)]):
    cell = info.rows[0].cells[i]
    cell.width = Cm(width)
    p = cell.paragraphs[0]
    format_paragraph(p, after=0, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_font(p.add_run(label), 9.5, i in (0, 2), "FFFFFF" if i in (0, 2) else None)
    if i in (0, 2):
        set_cell_shading(cell, NAVY)
for i, (label, width) in enumerate([("제안 분야", 2.6), ("소상공인·골목상권 디지털 금융", 4.4),
                                    ("세부 주제", 2.2), ("골목상권 데이터 기반 AI 컨설팅", 6.8)]):
    cell = info.rows[1].cells[i]
    cell.width = Cm(width)
    p = cell.paragraphs[0]
    format_paragraph(p, after=0, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_font(p.add_run(label), 9.2, i in (0, 2), "FFFFFF" if i in (0, 2) else None)
    if i in (0, 2):
        set_cell_shading(cell, NAVY)

add_callout(doc, "지금 바로 체험", f"{URL}  ·  실제 배포된 서비스(HTTPS)로 대구 음식점 24,079곳 중 내 가게를 검색해 이번 주 리포트를 바로 확인할 수 있습니다.")

add_heading(doc, "과제 요약", 1)
add_text(doc, "대구 음식점 사장님은 매주 달라지는 날씨·공휴일·지역 행사·식자재 가격을 기관별 사이트에서 각각 확인한 뒤 발주와 인력, 배달 준비를 결정한다. 장사메이트는 상호명과 대표 메뉴만 입력하면 가게 위치와 메뉴 기준으로 이번 주에 확인할 운영 항목을 정리해 주는 웹서비스이며, 대구 음식점 24,079곳 데이터로 실제 배포되어 동작한다. 식품 인허가·기상청 예보·KAMIS 가격·TourAPI 행사·천문연구원 공휴일을 결합해 최대 3건의 운영 참고사항과 근거를 제시한다. 매출·이익·현금흐름은 예측하지 않으며, 리포트에 없는 숫자는 AI가 만들어내지 못하도록 차단한다. 매주 쌓이는 운영 기록은 동의를 전제로 소상공인 자금 상담의 보조 근거로 확장할 수 있다.")

add_heading(doc, "1  제안내용의 목적 및 필요성", 1)
add_heading(doc, "문제 정의 — 사장님의 월요일 아침", 2)
add_text(doc, "치킨집 사장님은 이번 주 발주량을 정하려고 기상청에서 비 예보를, KAMIS에서 닭고기 시세를, 지자체 홈페이지에서 축제 일정을, 달력에서 연휴를 따로 확인해야 한다. 네 곳을 모두 확인해도 “그래서 우리 가게는 이번 주에 무엇을 준비해야 하는가”는 스스로 해석해야 한다. 결국 대부분은 확인을 건너뛰고 지난주와 같게 발주한다.", after=4)
add_text(doc, "통계청 2022년 소상공인실태조사에서 숙박·음식점업의 경영 애로사항은 원재료비 60.2%, 상권 쇠퇴 39.3%, 경쟁 심화 32.7% 순으로 나타났다. 원재료비와 수요 변동은 사장님이 매주 직접 판단해야 하는 영역이지만, 판단에 필요한 공공데이터는 기관별로 흩어져 있다.", size=9.7)

add_heading(doc, "핵심 솔루션", 2)
add_text(doc, "장사메이트는 흩어진 공공데이터를 ‘내 가게 기준’으로 묶어 이번 주에 확인할 운영 항목으로 바꿔 준다. 사장님은 4개 사이트를 오가는 대신 1개 화면에서 준비 항목과 그 근거를 확인한다. 조언은 짧게, 근거는 ‘근거보기’에서 출처·기준일과 함께 제시해 판단은 사장님이 하도록 설계했다.")

add_heading(doc, "주요 기능 한눈에 보기", 2)
add_table(doc, ["기능", "내용", "구현 상태"], [
    ["① 내 가게 등록", "대구 음식점 인허가 24,079곳에서 상호명 검색, 주소 직접 입력, 대표 메뉴 10종 자동 분류", "구현 완료"],
    ["② 이번 주 처방", "날씨·명절·행사·식자재·주변 현황을 결합해 확인할 항목 최대 3건과 실행 문장 제시", "구현 완료"],
    ["③ 근거보기", "권고마다 판단 근거와 논문·공공데이터 출처, 기준일, 원문 링크 제공", "구현 완료"],
    ["④ 식자재 급등 예측", "KAMIS 가격·대구 날씨로 학습한 모델이 다음 7일 급등 확률을 계산해 카드에 표시", "구현 완료"],
    ["⑤ 리포트 챗봇·재분석", "리포트에 있는 사실만 설명하는 챗봇, 오늘 기준 재분석", "구현 완료"],
], [3.2, 9.6, 3.2])
add_text(doc, "출처: 통계청 「2022년 소상공인실태조사」", size=8.5, color=GRAY, after=0)

# ── 2장: 구조 및 기술 ─────────────────────────────────────────────
add_heading(doc, "2  서비스 구조 및 기술", 1)
add_picture(doc, FLOW, 13.4, "그림 1. 서비스 흐름 — 사장님 입력 → 공공데이터 수집 → 규칙·AI 분석 → 근거 있는 주간 가이드")

add_heading(doc, "공공데이터와 서비스 적용", 2)
add_table(doc, ["데이터", "서비스에서의 활용", "표시 기준"], [
    ["대구 식품 인허가", "가게 검색, 업태·주소·좌표 확인, 반경 500m 음식점 집계", "24,079곳 수집(2026-09 기준)"],
    ["기상청 단기·중기예보", "7일 기온·강수 변화와 발주·배달 준비 항목 연결", "예보 발표 시점 표시"],
    ["KAMIS 대구 소매가격", "대표 메뉴 품목의 가격·최근 7일 추이와 다음 7일 급등 확률 예측", "품목·조사일·예측 기준일 표시"],
    ["한국관광공사 TourAPI", "가게 반경 3km 내 예정 행사와 거리 확인", "행사명·일정·거리 표시"],
    ["한국천문연구원 특일", "추석 등 연휴 전날·당일 구분해 휴무·발주 점검 안내", "공휴일 기준일 표시"],
], [3.4, 8.4, 4.2])

add_text(doc, "주요 데이터 출처 — 행정안전부·식품의약품안전처 식품위생업소 인허가, 기상청 단기·중기예보, 한국농수산식품유통공사 KAMIS 대구 소매가격, 한국관광공사 TourAPI, 한국천문연구원 특일 정보, 통계청 「2022년 소상공인실태조사」, 한국자료분석학회(2019) 음식점 카드매출과 날씨·요일·공휴일 분석 연구 외 논문·통계 14건", size=9.2, after=0)

add_heading(doc, "기술 스택", 2)
add_table(doc, ["구분", "적용 기술", "역할"], [
    ["Frontend", "Next.js 16, TypeScript, Axios, React Hook Form, Zod", "가게 등록, 리포트, 근거보기, 챗봇 화면"],
    ["Backend", "Java 21, Spring Boot 3.5, Spring Data JPA, Validation, Swagger", "공공데이터 Provider, 규칙 엔진, 리포트 API"],
    ["데이터", "H2(개발)·MySQL 프로필, 공공데이터 수집 스크립트(Python)", "24,079곳 상가·행사·가격 데이터 적재"],
    ["AI·모델", "LightGBM 급등 예측, 규칙 엔진 37종, Groq gpt-oss-120b", "식자재 급등 확률 산출, 권고 생성, 리포트 범위 챗봇"],
    ["배포", "Docker Compose, Caddy(HTTPS 자동 발급)", f"{URL} 상시 운영"],
], [2.2, 7.6, 6.2])

add_heading(doc, "AI가 숫자를 지어내지 않게 하는 장치", 2)
add_bullet(doc, "숫자 차단(LlmNumberGuard): AI 응답에 입력 데이터에 없는 숫자·날짜가 있으면 그 응답을 폐기하고 규칙 기반 문장으로 대체한다.")
add_bullet(doc, "근거 연결: 모든 권고에 출처 식별자를 붙이고, 검증에 실패한 조언은 화면에 표시하지 않는다.")
add_bullet(doc, "매출 질문 거절: 챗봇은 서비스가 산출하지 않는 매출·이익·발주량 수치 질문에 답하지 않고 확인 가능한 항목으로 안내한다.")
add_bullet(doc, "데이터 상태 표시: 외부 API 미연결·수집 실패 시 근거 없는 권고를 만들지 않고 해당 상태를 화면에 명시한다.")

add_heading(doc, "식자재 급등 예측 모델", 2)
add_text(doc, "KAMIS 대구 소매가(2023~2026)와 대구 기상 관측값으로 학습한 LightGBM 모델이 품목별로 ‘다음 7일 평균가가 지난 7일보다 10% 이상 오를 확률’을 계산한다. 학습에 쓰지 않은 2026년 실제 가격으로 검증했다.", after=4)
add_bullet(doc, "2026년 실제 급등 33건 중 27건을 사전에 경고(같은 기간 단순 모멘텀 규칙은 11건).")
add_bullet(doc, "F1 0.32 · PR-AUC 0.43 (무작위 경고 수준 0.08)로 모두 기준선을 웃돌며, 기준 미달 시 파이프라인이 중단되도록 검증 코드에 명시했다.")
add_bullet(doc, "예측 기준일로부터 7일이 지나면 화면에서 확률을 감추고, 확률만으로 매출·발주량을 단정하지 않는다.")

add_heading(doc, "동작 검증", 2)
add_bullet(doc, f"실서비스 배포: {URL}에서 상시 접속 가능하며 실데이터로 가게 검색·리포트 생성·챗봇 응답을 확인했다.")
add_bullet(doc, "자동 테스트 44개 통과(규칙 엔진, AI 숫자 차단, 외부 API 연동 포함).")
add_bullet(doc, "장애 대비: 백엔드 중단 시에도 전체 흐름이 예시 데이터로 동작하며 ‘예시’임을 화면에 표시한다.")
add_bullet(doc, "모바일 대응: 360~390px 폭에서 가로 스크롤 없이 전체 기능 동작을 확인했다.")

# ── 3장: 차별성 ──────────────────────────────────────────────────
add_heading(doc, "3  제안내용의 차별성", 1)
add_heading(doc, "기존 서비스와의 비교", 2)
add_table(doc, ["구분", "소상공인 상권정보시스템", "POS·매출관리 앱", "장사메이트"], [
    ["기준 단위", "행정구역·업종 통계", "내 가게 매출 기록", "내 가게 위치 + 대표 메뉴"],
    ["시점", "지난 분기·연도", "지난 매출", "다음 7일"],
    ["결과물", "지표·보고서 열람", "매출 정산·세무 자료", "이번 주 준비 항목 3건 + 근거"],
    ["근거 제시", "통계 출처", "자사 데이터", "권고별 논문·공공데이터 출처와 기준일"],
    ["필요 준비", "직접 해석 필요", "POS 설치·가맹 필요", "상호명·메뉴 입력만, 사업자번호 불필요"],
], [2.6, 4.3, 3.7, 5.4])

add_heading(doc, "예측이 아닌 ‘확인 가능한 준비’", 2)
add_text(doc, "서비스는 매출액, 매출 증감률, 이익, 현금흐름, 필요 발주량을 예측하지 않는다. 공공데이터에서 확인된 사실을 근거로 ‘점검 권장’, ‘준비 권장’ 수준의 운영 참고사항만 제공한다. 근거 없는 성과 예측을 배제해, 사장님이 서비스 결과를 어디까지 믿어도 되는지 스스로 판단할 수 있게 했다.")

add_heading(doc, "금융 서비스와의 연결 지점", 2)
add_text(doc, "장사메이트는 금융상품을 추천하거나 대출 가능성을 판정하지 않는다. 대신 금융 판단 이전 단계의 ‘운영 기록’을 만든다. 어떤 주에 어떤 외부 변화(연휴·비 예보·식자재 급등)가 있었고 사장님이 무엇을 준비했는지가 주 단위로 남으면, 매출 숫자만으로는 보이지 않는 가게의 운영 맥락이 쌓인다.", after=4)
add_table(doc, ["단계", "활용 방향", "전제 조건"], [
    ["현재", "공공데이터 기반 주간 운영 가이드와 근거 제공", "추가 동의 없이 공개 데이터만 사용"],
    ["다음", "주간 운영 기록의 누적, 지역·업종별 외부 변화 이력 축적", "사장님 동의, 개인정보 보호 체계"],
    ["확장", "iM뱅크·지역 지원기관의 소상공인 상담·정책자금 안내 시 보조 근거로 제공", "금융기관 협의, 별도 검증과 책임 기준"],
], [2.2, 9.6, 4.2])
add_text(doc, "금융거래 기반 현금흐름 예측이나 자금 부족 조기경보는 현재 서비스 기능이 아니다. 데이터 동의와 검증 체계가 갖춰진 뒤 별도 과제로 검토한다.", size=9.2, color=GRAY, after=0)

# ── 4장: 기대효과·사업화 ─────────────────────────────────────────
add_heading(doc, "4  기대효과 및 사업화 계획", 1)
add_heading(doc, "기대효과", 2)
add_bullet(doc, "음식점 사장님: 기관별 4개 사이트를 오가던 확인 과정을 1개 화면으로 줄이고, 연휴·비 예보·식자재 급등처럼 놓치기 쉬운 변화를 미리 점검한다.")
add_bullet(doc, "지역사회: 대구 행사·상권·가격 공공데이터를 가게 단위 활용으로 연결해 공공데이터가 생계 현장에서 쓰이게 한다.")
add_bullet(doc, "금융·지원기관: 운영 기록과 외부 변화 이력이 쌓이면 지역·업종별 지원정책 설계와 소상공인 상담의 보조 지표로 활용할 여지가 생긴다.")

add_heading(doc, "사업화와 단계별 고도화 계획", 2)
add_table(doc, ["단계", "내용", "검증 기준"], [
    ["1단계 MVP(현재)", "대구 음식점 대상 가게 등록, 공공데이터 결합, 주간 운영 가이드 제공, 실서비스 배포", "전체 사용자 흐름 동작, 테스트 44개, 데이터 상태 표시"],
    ["2단계 사용자 확보", "대구 소상공인 지원기관·상권 활성화 사업과 연계한 시범 도입, 업종별 가이드 고도화", "주간 재방문율, 재분석 이용률, 데이터 누락률"],
    ["3단계 연계 확장", "동의 기반 POS·주문 데이터 연계, 금융기관 상담 보조 근거 제공 검토, 지역·업종 확대", "개인정보 보호 체계, 금융기관 협의, 별도 성능 검증"],
], [3.0, 7.4, 5.6])

add_heading(doc, "평가항목별 제안의 대응", 2)
add_table(doc, ["평가 항목", "제안의 대응"], [
    ["실용성 및 문제해결력", "사업자번호·POS 없이 상호명과 대표 메뉴만 입력하면 이번 주 준비 항목이 나온다. 기관별로 흩어진 확인 과정을 한 화면으로 줄였고, 실제 배포된 주소에서 누구나 바로 체험할 수 있다."],
    ["기술 완성도", "Next.js·Spring Boot 분리 구조, Provider 기반 공공데이터 연동, 규칙 엔진 37종, AI 숫자 차단 장치, 테스트 44개, HTTPS 상시 배포까지 구현을 마쳤다."],
    ["사업화 확장 가능성", "공공데이터 기반 무료 가이드로 사용자를 모으고, 지역 지원기관 연계와 동의 기반 데이터 확장을 거쳐 금융 상담 보조 근거 제공까지 단계적으로 확장한다."],
    ["제안 충실도", "문제 정의부터 데이터 흐름, 검증 결과, 화면 캡처, 확장 단계까지 확인 가능한 근거와 함께 제시하고, 현재 구현과 향후 계획을 명확히 구분했다."],
], [3.4, 12.6])

add_heading(doc, "팀 구성", 2)
add_text(doc, "팀 노드(3인) — ① 서비스 기획·공공데이터 수집/모델링·백엔드 개발 ② 프론트엔드 구현·디자인 적용 ③ 제안서 작성·데이터 검증", after=0)

# ── 5장: 자유 기재(화면) ─────────────────────────────────────────
add_heading(doc, "5  구현 화면", 1)
add_text(doc, f"아래는 실제 배포된 서비스({URL})에서 대구 남구의 음식점을 검색해 생성한 2026년 9월 넷째 주 리포트 화면이다. 모든 수치는 공공데이터 실측값이며 임의로 만든 예시가 아니다.", size=9.6, after=6)
add_callout(doc, "심사 확인 포인트", f"{URL} 접속 → 상호명 검색(예: 교촌치킨) → 대표 메뉴 입력 → 이번 주 처방과 근거보기까지 약 1분이면 확인할 수 있습니다.")

shot_grid(doc, [
    ("app-02-search.png", "① 인허가 데이터에서 내 가게 검색 (사업자번호 불필요)"),
    ("app-06-home.png", "② 이번 주 처방 — 추석 연휴·날씨·식자재를 한 화면에"),
    ("app-07-evidence.png", "③ 근거보기 — 판단 근거와 논문 출처, 원문 링크"),
    ("app-08-chat.png", "④ 챗봇 — 매출 예측 질문은 거절하고 확인 항목으로 안내"),
], per_row=4, width_cm=3.6)



doc.core_properties.title = "장사메이트 — 대구 음식점 소상공인을 위한 공공데이터 기반 AI 주간 운영 가이드"
doc.core_properties.subject = "2026 AI Blockchain Challenge in Daegu 제안 요약서"
doc.core_properties.author = "노드"
doc.save(OUT)
print(os.path.abspath(OUT))
