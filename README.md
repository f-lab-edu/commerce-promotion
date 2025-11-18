# 🤍 이벤트 커머스 시스템 

대규모 트래픽 환경에서 ** 선착순 쿠폰 발급 → 이벤트 시작 → 재고 한정 특가 구매 →  결제 → 굿즈 제공**까지 이어지는 이벤트형 커머스를 설계·구현한 프로젝트입니다.


## 프로젝트 목표
- 순간 트래픽을 안정적으로 처리할 수 있는 커머스 시스템 구축
- 선착순/재고 경쟁 상황의 대규모 동시성 이슈 해결

## 주요 구현 및 해결 과제
- Redis Lua Script 기반 재고관리로 Race Condition 방지 및 원자적 처리 보장
- Kafka 메시지 큐 도입을 통한 대규모 주문/결제 요청의 비동기 처리 아키텍처 설계
- Docker 기반 컨테이너화로 개발 및 배포 환경 일관성 확보
- Redis Key Expiration Listener로 이벤트 자동 오픈 처리 및 이벤트 복구 : 리스너 미수신, 재시작 대비 스케줄러로 스캔 만료 보정

  
## 사용 기술
- **Language**: Java 17
- **Framework**: Spring Boot 3.5.3
- **Database**: MySQL 8.0
- **Cache**: Redis
- **Messaging & Async Processing** Queue : Kafka + Outbox Pattern
- **Infra**: Docker / Docker Compose, AWS EC2
- **Load Test**: JMeter
