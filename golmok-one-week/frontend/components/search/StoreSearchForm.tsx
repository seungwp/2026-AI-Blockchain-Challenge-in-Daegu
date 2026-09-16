"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";

const schema = z.object({
  keyword: z.string().trim().min(1, "상호명 또는 주소를 입력해주세요."),
});

export type StoreSearchValues = z.infer<typeof schema>;

export default function StoreSearchForm({
  onSearch,
  defaultKeyword = "",
}: {
  onSearch: (keyword: string) => void;
  defaultKeyword?: string;
}) {
  const { register, handleSubmit, formState } = useForm<StoreSearchValues>({
    resolver: zodResolver(schema),
    defaultValues: { keyword: defaultKeyword },
  });

  return (
    <form onSubmit={handleSubmit((v) => onSearch(v.keyword.trim()))}>
      <label htmlFor="keyword">상호명 또는 주소</label>
      <div className="row">
        <input id="keyword" placeholder="예: 교촌치킨 두류점 / 대구광역시 중구 교동" {...register("keyword")} />
        <button type="submit">검색</button>
      </div>
      {formState.errors.keyword && <p role="alert" className="error">{formState.errors.keyword.message}</p>}
    </form>
  );
}
