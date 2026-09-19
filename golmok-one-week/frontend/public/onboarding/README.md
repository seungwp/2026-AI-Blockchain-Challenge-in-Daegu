# 온보딩 원본 에셋

- 디자인: https://www.figma.com/design/Jzee3REkg8VRGkfEwfTYm6/ui
- owner.png: Onboarding1 (30:408) 내 사장님 일러스트 원본.
- back.svg, search.svg, location.svg: Onboarding2 (30:528) 원본 아이콘.
- enter.svg, chip.svg: Onboarding3 (30:448) 원본 아이콘.
- NotoSansKR.woff: https://github.com/google/fonts/tree/main/ofl/notosanskr
  의 NotoSansKR[wght].ttf에서 아래 범위를 유지한 가변 웹폰트.
- 글꼴 라이선스: OFL.txt.

글꼴 변환 명령(fontTools):

```
py -m fontTools.subset NotoSansKR.ttf --output-file=NotoSansKR.woff --flavor=woff --unicodes=U+0000-00FF,U+1100-11FF,U+2000-206F,U+3000-303F,U+3130-318F,U+AC00-D7A3,U+FF00-FFEF --layout-features=*
```
