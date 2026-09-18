# Docker 배포 준비

이 구성은 Next.js와 Spring Boot를 각각 컨테이너로 실행하고, Caddy가 외부 요청을 받는다.
외부에는 80번 포트만 열며 `/api`, `/swagger-ui`, `/v3/api-docs` 요청만 백엔드로 전달한다.

## 서버 준비

1. NCP 서버 보안 그룹에서 TCP 80을 연다.
2. 저장소를 서버에 내려받고, 저장소 루트에 `.env`를 만든다. 이 파일은 커밋하지 않는다.
3. `.env`에는 API 키를 넣는다. 좌표 API 키를 발급받았다면 `JUSO_COORD_API_KEY`도 추가한다.

```env
DATA_GO_KR_KEY=
KAMIS_CERT_KEY=
KAMIS_CERT_ID=
GROQ_API_KEY=
JUSO_SEARCH_API_KEY=
JUSO_COORD_API_KEY=
```

`NEXT_PUBLIC_API_BASE_URL`은 비워 둔다. 그러면 프론트는 현재 접속한 주소의 `/api`를 호출하며, API 키가 브라우저로 노출되지 않는다.

## 실행

저장소 루트에서 실행한다.

```bash
docker compose -f compose.golmok.yaml up -d --build
docker compose -f compose.golmok.yaml ps
docker compose -f compose.golmok.yaml logs -f backend
```

`http://서버-공인-IP/`로 화면을 확인하고, `http://서버-공인-IP/swagger-ui.html`에서 Swagger를 확인한다.

중지는 다음 명령을 사용한다.

```bash
docker compose -f compose.golmok.yaml down
```

## 도메인과 HTTPS

도메인의 A 레코드를 서버 공인 IP로 연결한 뒤 `golmok-one-week/Caddyfile` 첫 줄의 `:80`을 실제 도메인으로 바꾼다. 이후 다시 `up -d --build`를 실행하면 Caddy가 HTTPS 인증서를 관리한다. HTTPS 운영에서는 80과 443 포트를 모두 연다.

## 주의

- `.env`는 이미지에 복사하지 않고 Compose가 백엔드 환경변수로만 전달한다.
- 기본 프로필은 H2 메모리 DB라 컨테이너 재시작 시 리포트 데이터가 초기화된다. MySQL 운영 전환은 별도 작업이다.
- 로컬에서는 Docker 대신 기존 `backend` 및 `frontend` 실행 방식을 계속 쓸 수 있다.
