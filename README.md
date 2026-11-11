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
| 3 | 모의매매 에이전트, 거래 테이블(DB), 투자 히스토리 탭 | ⏳ |
| 4 | 에이전트 현황 탭 (실시간 평가손익) | ⏳ |
| 5 | 백테스트 | ⏳ |
| 6 | 실주문 연결 (업비트 키, IP 허용, 한도) | ⏳ |
| 7 | 로그인, 배포 | ⏳ |

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
- 수집 현황: http://localhost:8080/api/collector/status

### 2) 프론트엔드

Node 20.19 이상 또는 22.12 이상이 필요하다.

```bash
cd frontend
npm install
npm run dev           # → http://localhost:5173
```

개발 중 `/api` 요청은 Vite가 8080으로 넘겨준다 (`vite.config.js`의 proxy).

## 화면 (상단 탭)

- **종목 검색**: 맨 위 조건 패널 → 아래에 조건 통과 종목 최대 10개 + 선택 종목 차트
- **실시간 시세**: 기존 `00.upbit-chart` 화면 (코인 목록 + 차트)
- **에이전트 현황 / 투자 히스토리 / 설정**: 3단계 이후 구현 예정

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
POST /api/screener          조건 검색 (요청 예시는 아래)
GET  /api/collector/status  수집 현황
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
