-- H2(MySQL 모드)와 MariaDB 에서 모두 동작하도록 작성
-- 시각은 epoch 밀리초(BIGINT), 퍼센트는 % 단위

CREATE TABLE IF NOT EXISTS strategy (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(100)  NOT NULL,
    condition_json       VARCHAR(4000) NOT NULL,   -- 조건 검색 조건 (ScreenerCondition JSON)
    natural_text         VARCHAR(500),             -- 자연어로 입력한 원문
    order_krw            DOUBLE        NOT NULL,   -- 1회 매수 금액
    max_positions        INT           NOT NULL,
    take_profit_pct      DOUBLE,
    stop_loss_pct        DOUBLE,
    trailing_stop_pct    DOUBLE,
    max_hold_hours       INT,
    daily_loss_limit_krw DOUBLE,
    mode                 VARCHAR(10)   NOT NULL,   -- PAPER / LIVE
    enabled              BOOLEAN       NOT NULL,
    deleted              BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at           BIGINT        NOT NULL,
    buy_from             VARCHAR(5),               -- 매수 시간대 시작 HH:mm (한국 시간, NULL = 제한 없음)
    buy_to               VARCHAR(5)                -- 매수 시간대 끝 HH:mm (포함하지 않음)
);

-- 이미 만들어진 DB 에 나중에 추가한 칸 (CREATE TABLE IF NOT EXISTS 는 기존 테이블을 바꾸지 않으므로)
ALTER TABLE strategy ADD COLUMN IF NOT EXISTS buy_from VARCHAR(5);
ALTER TABLE strategy ADD COLUMN IF NOT EXISTS buy_to VARCHAR(5);

CREATE TABLE IF NOT EXISTS trade (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    strategy_id   BIGINT       NOT NULL,
    market        VARCHAR(20)  NOT NULL,
    korean_name   VARCHAR(100),
    status        VARCHAR(10)  NOT NULL,   -- OPEN / CLOSED
    mode          VARCHAR(10)  NOT NULL,
    buy_price     DOUBLE       NOT NULL,
    quantity      DOUBLE       NOT NULL,
    buy_amount    DOUBLE       NOT NULL,
    buy_fee       DOUBLE       NOT NULL,
    buy_at        BIGINT       NOT NULL,
    highest_price DOUBLE       NOT NULL,
    sell_price    DOUBLE,
    sell_amount   DOUBLE,
    sell_fee      DOUBLE,
    sell_at       BIGINT,
    sell_reason   VARCHAR(100),
    pnl           DOUBLE,
    pnl_rate      DOUBLE
);

CREATE INDEX IF NOT EXISTS idx_trade_status ON trade (status);
CREATE INDEX IF NOT EXISTS idx_trade_strategy ON trade (strategy_id);

CREATE TABLE IF NOT EXISTS agent_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at  BIGINT        NOT NULL,
    strategy_id BIGINT,
    level       VARCHAR(10)   NOT NULL,   -- INFO / BUY / SELL / WARN
    message     VARCHAR(500)  NOT NULL
);

-- 로그인 계정. 비밀번호는 BCrypt 해시로만 저장 (원래 비밀번호는 어디에도 남지 않음)
CREATE TABLE IF NOT EXISTS app_user (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    username            VARCHAR(50)  NOT NULL UNIQUE,
    password_hash       VARCHAR(100) NOT NULL,
    created_at          BIGINT       NOT NULL,
    password_changed_at BIGINT                    -- 이 시각 이전에 발급된 토큰은 무효
);

-- 설정 탭 값 (키 하나에 JSON 한 덩어리)
CREATE TABLE IF NOT EXISTS app_setting (
    setting_key   VARCHAR(50)   PRIMARY KEY,
    setting_value VARCHAR(4000) NOT NULL
);
