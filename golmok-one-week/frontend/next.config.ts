import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Docker 런타임에는 필요한 서버 파일만 복사한다.
  output: "standalone",
};

export default nextConfig;
