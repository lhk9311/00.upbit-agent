package com.lhk.upbitagent.market;

import com.lhk.upbitagent.domain.Candle;
import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.upbit.UpbitClient;
import com.lhk.upbitagent.upbit.UpbitDtos.UpbitMarket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/**
 * ===================================================================
 *  업비트 시세 수집기 (MarketCollector)
 * ===================================================================
 *
 * [이 클래스가 하는 일]
 *  서버가 켜져 있는 동안 "몇 초마다" 업비트에서 시세를 받아와서
 *  MarketDataStore(메모리 저장소)에 넣어두는 일만 한다.
 *  → 검색 화면·에이전트·백테스트는 업비트를 직접 부르지 않고, 여기서 모아둔 값을 꺼내 쓴다.
 *
 * [왜 미리 모아두나?]
 *  업비트는 "1초에 요청 몇 번까지" 제한이 있다.
 *  검색 버튼을 누를 때마다 301개 종목 캔들을 새로 받으면 제한에 바로 걸리고 느리다.
 *  그래서 수집은 이 클래스가 뒤에서 천천히 계속 하고, 나머지는 메모리 값만 읽는다.
 *  (식당으로 치면: 주문 들어올 때마다 장 보러 가는 게 아니라, 미리 장 봐서 냉장고에 넣어두는 것)
 *
 * [메서드 2개가 각각 "타이머"로 돈다]
 *  1) refreshTickers()  : 10초마다 → 301개 종목 "현재가"를 한 번의 요청으로 갱신
 *  2) refreshNextUnit() : 끝나면 5초 쉬고 다시 → 종목별 "캔들"을 하나씩 받아옴
 *                         (기준봉을 돌아가며: 1시간봉 → 15분봉 → 4시간봉 → 일봉 → 다시 1시간봉 ...)
 *
 *  배치 과제 때 쓴 @Scheduled(cron = "0 0 15 * * *") 와 같은 기능이다.
 *  차이는 "매일 15시에 한 번"이 아니라 "N초마다 계속 반복"이라는 것뿐.
 */
@Component
// ↑ @Component : "이 클래스는 스프링이 직접 객체로 만들어서 관리해줘(= Bean 등록)" 라는 표시.
//   서버가 시작될 때 스프링이 new MarketCollector(...) 를 알아서 한 번 해준다.
//   @Service, @Repository, @Controller 도 사실 다 @Component 의 한 종류(역할 이름만 다름).
//   @Scheduled 가 동작하려면 이 클래스가 반드시 Bean 이어야 한다.
public class MarketCollector {

    // ---------------------------------------------------------------
    // 로그
    // ---------------------------------------------------------------
    // System.out.println 대신 쓰는 로그 도구.
    // log.info("... {}개", 301) 처럼 쓰면 {} 자리에 값이 들어가고, 시간·스레드 이름까지 같이 찍힌다.
    // (IntelliJ 콘솔에 보이는 "INFO ... MarketCollector : 마켓 목록 갱신: 301개" 가 이것)
    // static final : 클래스당 하나만 있으면 되고(static), 바뀔 일이 없다(final).
    private static final Logger log = LoggerFactory.getLogger(MarketCollector.class);

    // ---------------------------------------------------------------
    // 상수
    // ---------------------------------------------------------------
    /**
     * 캔들 수집 순서. 화면 기본값인 1시간봉을 가장 먼저 모은다.
     * 서버를 켜자마자 1시간봉 검색이 가능하도록 하기 위함.
     */
    private static final CandleUnit[] ORDER = {
            CandleUnit.MIN60, CandleUnit.MIN15, CandleUnit.MIN240, CandleUnit.DAY
    };

    /**
     * 마켓 목록(어떤 코인이 상장돼 있는지)은 자주 안 바뀌니까 1시간에 한 번만 새로 받는다.
     * 60 * 60 * 1000L = 3,600,000ms = 1시간.  끝의 L 은 long 타입이라는 뜻(int 범위 초과 방지).
     */
    private static final long MARKET_REFRESH_MS = 60 * 60 * 1000L;

    // ---------------------------------------------------------------
    // 의존성 (이 클래스가 일을 하려면 필요한 다른 객체들)
    // ---------------------------------------------------------------
    /** 업비트 API 를 실제로 호출하는 객체 (HTTP 요청 담당) */
    private final UpbitClient upbit;

    /** 받아온 시세를 넣어두는 메모리 저장소 (냉장고 역할) */
    private final MarketDataStore store;

    // ---------------------------------------------------------------
    // 설정값 (application.yml 에서 읽어옴)
    // ---------------------------------------------------------------
    // @Value("${키:기본값}") : application.yml 의 collector.request-gap-ms 값을 이 변수에 넣어줘.
    //                          yml 에 없으면 콜론(:) 뒤의 130 을 쓴다.
    // → 코드를 안 고치고 yml 만 바꿔서 속도를 조절할 수 있다.

    /** 캔들 요청 하나 보내고 다음 요청까지 쉬는 시간(ms). 업비트 요청 제한에 안 걸리려고. */
    @Value("${collector.request-gap-ms:130}")
    private long requestGapMs;

    /** 종목 하나당 받아올 캔들 개수 (업비트는 한 번에 최대 200개까지 줌) */
    @Value("${collector.candle-count:200}")
    private int candleCount;

    // ---------------------------------------------------------------
    // 상태값 (실행되면서 바뀌는 값)
    // ---------------------------------------------------------------
    /** 이번에 모을 기준봉이 ORDER 배열의 몇 번째인지 (0 → 1 → 2 → 3 → 0 → ...) */
    private int unitIndex = 0;

    /** 마켓 목록을 마지막으로 받은 시각(ms). 1시간 지났는지 판단용 */
    private long marketsLoadedAt = 0;

    // ---------------------------------------------------------------
    // 생성자 = 의존성 주입(DI)
    // ---------------------------------------------------------------
    /**
     * 스프링이 이 클래스를 만들 때, 파라미터에 필요한 객체(UpbitClient, MarketDataStore)를
     * 자기가 관리하는 Bean 중에서 찾아서 자동으로 넣어준다. 이게 "생성자 주입(DI)" 이다.
     *
     * 내가 직접 new MarketDataStore() 를 하지 않는 이유:
     *  - 모든 클래스가 "같은" MarketDataStore 하나를 같이 써야 한다 (냉장고가 여러 개면 안 됨).
     *  - 스프링이 하나만 만들어서(싱글톤) 필요한 곳마다 똑같은 걸 넣어준다.
     *    → 수집기가 넣은 값을 검색 컨트롤러·에이전트가 그대로 꺼내 볼 수 있는 이유.
     *
     * 생성자가 하나뿐이면 @Autowired 를 안 붙여도 스프링이 알아서 이 생성자를 쓴다.
     * 필드를 final 로 해두면 한 번 들어간 뒤 바뀌지 않아서 안전하다.
     */
    public MarketCollector(UpbitClient upbit, MarketDataStore store) {
        this.upbit = upbit;
        this.store = store;
    }

    // ===================================================================
    // 타이머 1 : 현재가 갱신 (10초마다)
    // ===================================================================
    /**
     * @Scheduled(fixedDelay = 10_000, initialDelay = 0)
     *  - fixedDelay   = 10_000 : 이 메서드가 "끝난 뒤" 10초(10,000ms) 쉬고 다시 실행.
     *                            (10_000 의 _ 는 그냥 읽기 쉽게 끊은 것. 10000 과 같다)
     *  - initialDelay = 0      : 서버 켜지자마자 바로 첫 실행.
     *
     * 참고로 비슷한 옵션 3가지:
     *  - fixedDelay : 끝나고 나서 N초 뒤 다시   ← 여기서 쓰는 것 (이전 실행과 절대 안 겹침)
     *  - fixedRate  : 시작 시각 기준 N초마다   (실행이 오래 걸리면 밀려서 바로 또 실행될 수 있음)
     *  - cron       : 달력 기준 "매일 15시" 같은 것 (배치 과제에서 쓴 방식)
     *
     * 이 메서드를 직접 호출하는 코드는 프로젝트 어디에도 없다.
     * 스프링의 스케줄러가 시간 맞춰 알아서 불러준다.
     * (UpbitAgentApplication 에 붙은 @EnableScheduling 이 이 기능을 켜는 스위치)
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 0)
    public void refreshTickers() {
        // try-catch 로 감싼 이유:
        // 업비트가 잠깐 응답을 안 하거나 인터넷이 끊겨서 예외가 나도,
        // 로그만 남기고 넘어가서 10초 뒤에 다시 시도하게 하려고.
        try {
            // ① 마켓 목록이 비어 있거나(서버 처음 켰을 때) 1시간이 지났으면 새로 받기
            //    System.currentTimeMillis() = 지금 시각(1970년부터 지난 ms)
            if (store.markets().isEmpty() || System.currentTimeMillis() - marketsLoadedAt > MARKET_REFRESH_MS) {
                store.updateMarkets(upbit.krwMarkets());      // KRW-BTC, KRW-ETH ... 301개
                marketsLoadedAt = System.currentTimeMillis(); // 받은 시각 기록
                log.info("마켓 목록 갱신: {}개", store.markets().size());
            }

            // ② 종목 코드만 뽑아서 리스트로 만들기  ["KRW-BTC", "KRW-ETH", ...]
            //    stream() : 리스트를 하나씩 흘려보내면서 가공
            //    map(UpbitMarket::market) : 각 UpbitMarket 객체에서 market(코드)만 꺼냄
            //                               = map(m -> m.market()) 과 같은 뜻
            //    toList() : 다시 리스트로 모음
            //    for 문으로 쓰면 이렇게 된다:
            //      List<String> codes = new ArrayList<>();
            //      for (UpbitMarket m : store.markets()) { codes.add(m.market()); }
            List<String> codes = store.markets().stream().map(UpbitMarket::market).toList();

            // ③ 301개 현재가를 "한 번의 요청"으로 받아서 저장
            //    (업비트 ticker API 는 종목을 쉼표로 여러 개 넘길 수 있다)
            store.updateTickers(upbit.tickers(codes));

        } catch (Exception e) {
            // 실패해도 서버가 죽지 않는다. warn 레벨로 기록만 하고 다음 10초를 기다린다.
            log.warn("현재가 갱신 실패: {}", e.getMessage());
        }
    }

    // ===================================================================
    // 타이머 2 : 캔들 수집 (한 바퀴 끝나면 5초 쉬고 다음 기준봉)
    // ===================================================================
    /**
     * @Scheduled(fixedDelayString = "${collector.candle-delay-ms:5000}", initialDelay = 3_000)
     *  - fixedDelayString : fixedDelay 와 같은데, 숫자 대신 yml 설정값(문자열)을 쓰고 싶을 때.
     *                       yml 의 collector.candle-delay-ms(5000) → 끝나고 5초 쉬고 다시 실행.
     *  - initialDelay = 3_000 : 서버 켜고 3초 뒤 첫 실행.
     *                           (타이머 1이 먼저 마켓 목록을 받아올 시간을 주려고)
     *
     * 한 번 실행될 때 하는 일:
     *  "이번 기준봉(예: 1시간봉)" 하나를 정해서, 301개 종목 캔들을 하나씩 전부 받아온다.
     *  301개 × (요청 시간 + 130ms 쉬기) ≈ 1분 정도 걸린다.
     *  다 끝나면 5초 쉬고 → 다음 기준봉(15분봉)으로 다시 실행.
     *  → 4개 기준봉을 한 바퀴 다 도는 데 약 4~5분. 그래서 캔들은 몇 분에 한 번씩 새로워진다.
     *
     * [중요] 타이머 1과 타이머 2는 "동시에" 돈다.
     *  스프링 스케줄러는 기본적으로 일꾼(스레드)이 1명이라, 하나가 1분 동안 일하면 다른 게 기다려야 한다.
     *  그래서 application.yml 에 spring.task.scheduling.pool.size: 3 으로 일꾼을 3명 둬서
     *  현재가 갱신 / 캔들 수집 / 에이전트가 서로 안 막히게 했다.
     *  (콘솔 로그의 [scheduling-1], [scheduling-2] 가 일꾼 이름)
     */
    @Scheduled(fixedDelayString = "${collector.candle-delay-ms:5000}", initialDelay = 3_000)
    public void refreshNextUnit() {
        // 마켓 목록을 아직 못 받았으면 받을 종목이 없으니 이번엔 그냥 끝낸다.
        // (5초 뒤 다시 실행됨)
        if (store.markets().isEmpty()) {
            return;
        }

        // ① 이번에 모을 기준봉 고르기
        CandleUnit unit = ORDER[unitIndex];
        // 다음 실행을 위해 인덱스를 하나 넘긴다.
        // % (나머지 연산) : 0→1→2→3 다음에 4 % 4 = 0 이 되어서 다시 처음으로 돌아간다.
        // 이렇게 돌아가며 하나씩 처리하는 걸 "라운드 로빈" 이라고 한다.
        unitIndex = (unitIndex + 1) % ORDER.length;

        // ② 진행 상황을 세기 위한 변수들
        long started = System.currentTimeMillis(); // 시작 시각 (걸린 시간 계산용)
        int ok = 0;    // 성공한 종목 수
        int done = 0;  // 처리한(시도한) 종목 수
        List<UpbitMarket> markets = store.markets();
        int total = markets.size();

        // 예상 시간 = 종목 수 × (쉬는 시간 130ms + 요청 시간 대략 50ms) / 1000 → 초 단위
        log.info("{} 캔들 수집 시작 ({}개 종목, 약 {}초 소요)", unit.label(), total, total * (requestGapMs + 50) / 1000);

        // ③ 종목을 하나씩 돌면서 캔들 받아오기
        for (UpbitMarket m : markets) {
            // 상태 API(/api/collector/status)에서 "지금 몇 개째인지" 볼 수 있게 진행률 저장
            store.updateProgress(unit, done, total);

            // 100개마다 한 번씩 로그 (안 찍으면 1분 동안 조용해서 멈춘 줄 알게 됨)
            if (done > 0 && done % 100 == 0) {
                log.info("{} 캔들 수집 중 {}/{}", unit.label(), done, total);
            }
            done++;

            try {
                // 업비트에 "KRW-BTC 1시간봉 200개 줘" 요청
                List<Candle> candles = upbit.candles(unit, m.market(), candleCount);
                // 메모리 저장소에 덮어쓰기 (같은 종목·기준봉의 이전 값은 교체됨)
                store.updateCandles(unit, m.market(), candles);
                ok++;

            } catch (HttpClientErrorException.TooManyRequests e) {
                // HTTP 429 = "요청이 너무 많아요" (업비트 요청 제한에 걸림)
                // 1초 쉬어서 업비트를 진정시키고 다음 종목으로 넘어간다.
                // 이 종목은 이번 바퀴에서 빠지지만, 다음 바퀴에 다시 받으니 괜찮다.
                log.debug("요청 제한(429), 잠시 대기: {}", m.market());
                sleep(1_000);

            } catch (Exception e) {
                // 그 밖의 에러(네트워크 등)도 이 종목만 건너뛰고 계속 진행.
                // 하나 실패했다고 301개 전체 수집이 멈추면 안 되니까.
                log.warn("캔들 수집 실패 {} {}: {}", unit, m.market(), e.getMessage());
            }

            // 다음 요청 전에 130ms 쉬기 (요청 제한 예방)
            sleep(requestGapMs);
        }

        // ④ 한 바퀴 끝
        store.updateProgress(null, 0, 0);   // "지금 수집 중 아님" 으로 표시
        store.markCandlesUpdated(unit);     // "이 기준봉은 준비 완료" 표시
                                            // → 이게 찍혀야 검색/에이전트/백테스트가 이 기준봉을 쓴다
                                            //   (서버 켜고 1분 동안 "수집 중" 안내가 뜨는 이유)
        log.info("{} 캔들 수집 완료: {}/{}개, {}초",
                unit.label(), ok, total, (System.currentTimeMillis() - started) / 1000);

        // 이 메서드가 끝나면 fixedDelay(5초) 뒤에 스프링이 다시 불러준다 → 다음 기준봉 수집
    }

    // ===================================================================
    // 도우미
    // ===================================================================
    /**
     * 지정한 ms 만큼 현재 스레드(일꾼)를 멈춘다.
     * Thread.sleep 은 InterruptedException 을 던질 수 있어서 매번 try-catch 를 써야 하는데,
     * 그게 번거로워서 한 곳에 모아둔 것.
     *
     * InterruptedException : 누가 "그만 자고 일어나(서버 종료 등)" 라고 깨웠을 때 나는 예외.
     * 그 신호를 무시하지 않도록 interrupt() 로 다시 표시해둔다 (자바의 관례).
     */
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
