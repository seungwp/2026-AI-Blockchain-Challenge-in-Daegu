"use client";

import { MENU_CATEGORIES, type MenuCategory } from "@/types";

/** 자동 분류 결과를 사용자가 수정할 수 있게 하는 select box. */
export default function MenuCategorySelector({
  value,
  onChange,
  id = "menuCategory",
}: {
  value: MenuCategory;
  onChange: (value: MenuCategory) => void;
  id?: string;
}) {
  return (
    <>
      <label htmlFor={id}>메뉴 카테고리</label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value as MenuCategory)}>
        {MENU_CATEGORIES.map((c) => (
          <option key={c} value={c}>{c}</option>
        ))}
      </select>
    </>
  );
}
