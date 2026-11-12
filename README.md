# upbit-agent

업비트 KRW 마켓 **조건 검색 → 자동 매매 에이전트** 프로젝트.
`00.upbit-chart`(Vue 차트)를 골조로 가져와 백엔드를 붙였다.

```
upbit-agent/
├ frontend/   Vue 3 + Vite + ApexCharts + Bootstrap
└ backend/    Spring Boot 3.5 + Java 17
```

## 진행 상황

| 단계 | 내용 | 상태 |
|---|---|---|
| 1 | 골조 이식, 차트 버그 수정, 상단 탭 | ✅ |
| 2 | 시세 수집기 + 조건 검색 API + 검색 화면 | ✅ |
| 3 | 모의매매 에이전트, 거래 테이블(DB), 투자 히스토리 탭 | ✅ |
| 4 | 에이전트 현황 탭 (실시간 평가손익) | ✅ |
| 5 | 백테스트 | ✅ |
| 6 | 실주문 연결 (업비트 키, IP 허용, 한도) | ⏳ |
| 7 | 로그인(Spring Security + JWT), 설정 탭 | ✅ (배포는 하지 않음) |

## 실행

### 1) 백엔드 (먼저)

IntelliJ에서 `backend/` 폴더를 Gradle 프로젝트로 열고 `UpbitAgentApplication`을 실행한다.

터미널을 쓰는 경우 (Gradle 설치 필요, 최초 1회 wrapper 생성):
```bash
cd backend
gradle wrapper        # 최초 1회 → gradlew 생성
./gradlew bootRun     # Windows: gradlew.bat bootRun
```

- 주소: http://localhost:8080
- 서버를 켜면 업비트 시세를 모으기 시작한다. **1시간봉은 약 1분 뒤**부터 검색 가능하고, 15분·4시간·일봉은 차례로 준비된다.
- 수집 현황: http://localhost:8080/api/collector/status (수집 중이면 `collecting`에 진행률)
- 콘솔에 `1시간봉 캔들 수집 시작 → 수집 중 100/301 → 수집 완료` 로그가 차례로 찍힌다

#### 자연어 검색용 Claude API 키 (선택)

키가 없어도 서버는 정상으로 뜨고, 조건 패널로 직접 검색할 수 있다. 문장으로 검색하려면:

1. https://console.anthropic.com 에서 API 키 발급 (결제 수단 등록 필요)
2. 환경변수 `ANTHROPIC_API_KEY` 설정
   - IntelliJ: Run → Edit Configurations → `UpbitAgentApplication` → Environment variables 에 `ANTHROPIC_API_KEY=sk-ant-...`
   - Windows 터미널: `setx ANTHROPIC_API_KEY "sk-ant-..."` 후 터미널·IntelliJ 재시작
3. 서버 재시작

키는 `application.yml`이나 코드에 직접 쓰지 않는다 (깃허브에 올라가면 바로 도용된다).
모델은 가장 저렴한 `claude-haiku-5-5`를 쓴다 (`application.yml`의 `anthropic.model`).

### 2) 프론트엔드

Node 20.19 이상 또는 22.12 이상이 필요하다.

```bash
cd frontend
npm install
npm run dev           # → http://localhost:5173
```

개발 중 `/api` 요청은 Vite가 8080으로 넘겨준다 (`vite.config.js`의 proxy).

## 화면 (상단 탭)

- **종목 검색**: 맨 위 자연어 입력 → 조건 패널(해석 결과, 직접 수정 가능) → 아래에 조건 통과 종목 최대 10개 + 선택 종목 차트
- **실시간 시세**: 기존 `00.upbit-chart` 화면 (코인 목록 + 차트)
- **에이전트 현황**: 보유 평가손익·오늘/누적 실현손익·승률, 보유 종목(평가수익률, 수동 매도), 활동 기록, 전략 시작·정지·삭제 (5초마다 갱신)
- **투자 히스토리**: 전체 거래. 완료 거래는 매수·매도 가격 모두, 진행 중 거래는 매수 가격만. 종목명·매수일시·매수가·매도일시·매도가·수익률·손익 정렬, 상태·전략·기간 필터
- **설정**: 모든 전략 신규 매수 중단 스위치, 수수료·모의 체결가 불리·재진입 대기, 새 전략 기본값, 비밀번호 변경, 시스템 상태(수집 진행률 등)

수익률·손익은 한국 관례대로 **플러스 빨강, 마이너스 파랑**.

## 모의매매 에이전트

검색 결과에서 **이 조건으로 에이전트 시작**을 누르면 전략(매수 조건 + 매도 규칙 + 리스크 한도)이 저장되고 바로 실행된다.

1분마다 (`agent.cycle-ms`) 전략별로:
1. 보유 종목 매도 판단: **손절 → 익절 → 트레일링(수익 구간에서 최고가 대비 하락) → 최대 보유 시간** 순
2. 오늘 실현손실이 일일 손실 한도를 넘으면 신규 매수 중단
3. 빈 자리(최대 보유 수 − 보유 수)만큼 조건 검색 상위 종목 매수. 이미 보유 중이거나 최근 60분 안에 판 종목은 제외

- 지금은 **모의매매만** 지원한다. 실제 주문은 나가지 않고 현재가로 체결됐다고 가정해 기록한다.
- 결과가 실제보다 좋게 나오지 않도록 **수수료 0.05%**와 **체결가 0.1% 불리**를 매수·매도 양쪽에 반영한다 (`application.yml`의 `agent.*`).
- 전략을 **정지**하면 신규 매수만 멈추고, 보유 종목은 매도 규칙대로 계속 관리한다.
- 현재가 수신이 1분 넘게 끊기면 매매 판단을 쉬고 활동 기록에 남긴다.
- 판단 로직(`AgentEngine`)은 순수 Java라 단위 테스트로 검증한다.

## 백테스트

전략을 서버가 이미 모아둔 과거 캔들에 그대로 적용해본다. 업비트를 추가로 호출하지 않아 1초 안에 끝난다.

- 전략 설정 창의 **백테스트** 버튼(저장 전), 에이전트 현황의 전략 카드 **백테스트** 버튼(저장 후)
- 결과: 총손익·수익률(투입 한도 = 1회 금액 × 최대 보유 수 대비), 거래 수·승률, 최대 낙폭, 평균 보유 시간, 같은 기간 **비트코인 그냥 보유** 수익률, 수익곡선, 거래 목록

계산 규칙 (`BacktestEngine`, 애매하면 불리하게)
- 봉이 끝난 시점에 조건 검색 → 종가로 매수 (수수료·슬리피지 반영)
- 다음 봉부터 봉 안의 고가·저가로 매도 판단. 한 봉에서 손절가·익절가 모두 닿으면 **손절**, 갭 하락이면 시가에 손절
- 일일 손실 한도, 재진입 대기, 최대 보유 수 모두 실제 에이전트와 같게 적용
- 기간 끝에 남은 포지션은 마지막 종가로 평가

한계
- 종목당 최대 200봉이라 1시간봉은 약 5일, 일봉은 약 4개월 구간이다 (지표 계산용 앞 69봉 제외)
- 지금 상장된 종목만 대상 (상장폐지 종목 빠짐 → 실제보다 좋게 나올 수 있음)
- 24시간 거래대금·전일 대비 등락률은 캔들로 근사, 유의 종목은 현재 기준

### DB

개발용 **H2 파일 DB**를 쓴다. 설치가 필요 없고, `backend/data/`에 저장돼 서버를 꺼도 기록이 남는다 (깃에는 올라가지 않음).

- DB 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/upbit-agent;MODE=MySQL;AUTO_SERVER=TRUE`, 사용자 `sa`, 비밀번호 없음)
- 테이블: `strategy`(전략), `trade`(거래), `agent_log`(활동 기록), `app_user`(로그인 계정), `app_setting`(설정) — `schema.sql`
- 배포 때 MariaDB로 바꾸려면 `build.gradle`의 H2 를 MariaDB 드라이버로, `application.yml`의 datasource 를 MariaDB 주소로 바꾸면 된다 (SQL 은 둘 다에서 동작하도록 작성).

## 로그인 (Spring Security + JWT)

처음 서버를 켜면 관리자 계정(`admin`)이 자동으로 만들어지고, **비밀번호가 콘솔 로그에 한 번 찍힌다.**
```
==================================================
  관리자 계정을 만들었어요
  아이디   : admin
  비밀번호 : xxxxxxxxxxxx
==================================================
```
로그인 후 설정 탭에서 비밀번호를 바꾸면 된다.

| 환경변수 | 설명 |
|---|---|
| `ADMIN_PASSWORD` | 처음 만들 관리자 비밀번호 (없으면 무작위) |
| `ADMIN_RESET=true` | 비밀번호를 잊었을 때. 켜고 재시작하면 관리자 비밀번호를 다시 만든다 → 끝나면 꼭 끄기 |
| `JWT_SECRET` | 토큰 서명 키(32자 이상). 없으면 재시작할 때마다 새로 만들어져 다시 로그인해야 함 |

흐름
```
로그인: POST /api/auth/login → AuthenticationManager(UserDetailsService + BCrypt) → JWT 발급
요청  : Authorization: Bearer <토큰> → JwtAuthFilter 가 서명·만료 확인 → 통과하면 로그인 사용자로 표시
       → SecurityConfig 규칙(/api/auth/login 외에는 전부 로그인 필요) → 컨트롤러
```
- 세션을 만들지 않는다(STATELESS). 서버는 로그인 상태를 기억하지 않고, 매 요청 토큰 서명만 확인한다.
- 비밀번호는 BCrypt 해시로만 저장한다 (`app_user` 테이블).
- 같은 아이디로 10분 안에 5번 틀리면 10분 잠금 (`LoginAttemptLimiter`).
- 비밀번호를 바꾸면 그 전에 발급된 토큰은 모두 무효가 된다 (`password_changed_at`).
- 화면은 토큰을 localStorage 에 저장하고, axios 인터셉터가 모든 요청에 붙인다. 401 이면 로그인 화면으로.

## 자연어 검색

```
"1시간봉에서 거래대금 100억 이상, 과매도였다가 볼린저 하단 찍고 반등하는 코인"
   ↓ POST /api/screener/parse  (Claude API, 구조화된 출력으로 JSON 스키마 보장)
{ unit: MIN60, minTradeAmount: 100억, rsiMax: 30, bbLowerTouchWithin: 3 } + 해석 설명
   ↓ 화면이 조건 패널에 채워서 보여줌 (사람이 확인·수정)
   ↓ POST /api/screener
결과 최대 10개
```

- LLM 은 **문장을 조건으로 바꾸는 일만** 한다. 실제 종목 선별은 서버의 규칙 기반 엔진이 하므로 결과가 재현 가능하다.
- 애매한 표현("과매도", "거래량 터진", "눌림목")을 어떤 숫자로 정했는지 설명과 함께 돌려준다.
- 지원하지 않는 요청(뉴스, 재무, 특정 코인 지정 등)은 무시하고 "반영하지 못한 요청"으로 알려준다.
- LLM 이 범위를 벗어난 값(RSI 150 등)을 주면 `NlConditionMapper`가 바로잡고 화면에 알려준다.
- 프롬프트와 스키마는 `backend/src/main/resources/nl/` 에 있어 코드 수정 없이 다듬을 수 있다.

## 조건 검색

켜진 조건은 모두 **AND**로 적용되므로 조건을 세밀하게 줄수록 결과가 줄어든다.
통과한 종목을 정렬 기준으로 줄 세워 **최대 10개**를 반환한다.

| 그룹 | 조건 |
|---|---|
| 기본 | 기준봉(15분/1시간/4시간/일), 가격 범위, 24h 거래대금, 전일대비 범위, 유의 종목 제외 |
| 추세 | 이평선 정배열(5>20>60), 현재가 > MA20, N봉 이내 골든크로스 |
| 모멘텀 | RSI(14) 범위, N봉 이내 MACD 시그널 상향돌파, N봉 이내 볼린저 하단 터치 후 반등 |
| 거래량·변동성 | 거래량 급증 배수(직전 완성봉 / 20봉 평균), ATR(14)% 범위, 20봉 고점 대비 하락률 |
| 정렬 | 거래량 급증 배수, 거래대금, 등락률, RSI 낮은 순 |

### API

```
POST /api/screener              조건 검색 (요청 예시는 아래)
POST /api/screener/parse        자연어 → 조건 변환 { "text": "..." }
GET  /api/screener/parse/status 자연어 검색 사용 가능 여부 (API 키 설정 여부)
GET  /api/collector/status      수집 현황

POST /api/auth/login             로그인 → { token, username, expiresAt } (로그인 없이 호출 가능)
GET  /api/auth/me                지금 로그인한 사용자
POST /api/auth/password          비밀번호 변경 → 새 토큰
GET  /api/settings               설정 조회
PUT  /api/settings               설정 저장
GET  /api/system/status          시스템 상태
(위 로그인 API 를 뺀 모든 API 는 Authorization: Bearer <토큰> 필요)

GET  /api/strategies             전략 목록
POST /api/strategies             전략 저장 (+ 바로 실행)
POST /api/strategies/{id}/start  시작
POST /api/strategies/{id}/stop   정지 (신규 매수만 중단)
DELETE /api/strategies/{id}      삭제 (보유 종목이 없을 때만)
GET  /api/positions              보유 종목 + 평가손익
POST /api/trades/{id}/close      수동 매도
GET  /api/trades                 투자 히스토리 (status, strategyId, from, to, sort, dir)
GET  /api/agent/summary          요약 (실현손익, 승률, 평가손익)
GET  /api/agent/logs             활동 기록
POST /api/agent/run              한 주기 즉시 실행
POST /api/backtest               저장 전 전략 백테스트 (전략 요청 본문)
POST /api/strategies/{id}/backtest 저장된 전략 백테스트
```

```json
{
  "unit": "MIN60",
  "minTradeAmount": 5000000000,
  "excludeWarning": true,
  "maAligned": true,
  "volumeSurgeRatio": 3,
  "rsiMin": 40,
  "sortBy": "VOLUME_SURGE",
  "limit": 10
}
```
값을 생략하거나 `null`로 보내면 그 조건은 사용하지 않는다.

## 구조 메모

- 업비트 시세 API는 요청 수 제한이 있어서, 검색할 때마다 200여 개 종목을 호출하지 않는다.
  `MarketCollector`가 주기적으로 캔들을 모아 `MarketDataStore`(메모리)에 두고, 검색은 그 캐시만 읽는다.
- 지표 계산(`Indicators`)과 검색 엔진(`ScreenerEngine`)은 Spring 에 의존하지 않는 순수 Java라 단위 테스트로 검증한다.
  `./gradlew test`

## 차트 버그 수정 내역 (00.upbit-chart 대비)

- axios 의존성 누락 → 추가
- 시간축이 UTC로 표시되던 문제 → 한국 시간 표시
- ApexCharts가 첫 렌더링 때의 x·y축 눈금을 계속 유지하던 문제 → 데이터가 바뀔 때마다 축 범위 직접 갱신
- 실시간 모드에서 다른 코인 선택 시 이전 코인 가격이 섞이던 문제
- 실시간 캔들 시각을 분 단위로 맞추지 않아 1분봉과 겹치던 문제
- 코인 목록 현재가가 갱신되지 않던 문제 → 3초마다 갱신 (정렬 유지)
- 캔들 색: 상승 빨강, 하락 파랑
