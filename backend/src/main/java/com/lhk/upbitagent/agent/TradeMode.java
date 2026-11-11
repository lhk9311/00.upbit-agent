package com.lhk.upbitagent.agent;

public enum TradeMode {
    /** 모의매매: 실제 주문 없이 현재가로 체결됐다고 가정하고 기록만 한다 */
    PAPER,
    /** 실매매: 업비트 주문 API 사용 (6단계에서 구현 예정) */
    LIVE
}
