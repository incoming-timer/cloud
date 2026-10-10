# 원격 타이머 서버 로드맵

## 문서 목적

이 문서는 Timer Overlay의 원격 타이머 서버와 데스크톱 클라이언트 동기화
계층을 구현하기 위한 단계별 계획을 정의한다. 원격 저장소는 로컬 기능을
대체하지 않는다. 클라이언트는 네트워크 상태와 관계없이 로컬 저장소를 먼저
갱신하고, 서버는 인증된 백업, 여러 기기 사이의 변경 직렬화, 충돌 감지와
최소 지연 전파를 담당한다.

서버의 핵심 기능은 타이머 관리다. 공유 프리셋, 통계, 커뮤니티 기능은 안정된
동기화 기반을 확보한 뒤 별도 단계에서 다룬다.

## 제품 요구사항

### 필수 동작

- 로그인하지 않거나 오프라인이어도 타이머 생성과 실행 제어가 동작한다.
- 로컬 변경은 서버 응답을 기다리지 않고 화면과 로컬 저장소에 즉시 반영한다.
- 온라인인 다른 기기에는 확정된 변경이 최소 지연으로 전파된다.
- 연결이 끊겼다가 복구되면 누락된 변경을 순서대로 가져온다.
- 중복 요청, 순서가 뒤바뀐 요청, 동시 변경이 데이터를 손상시키지 않는다.
- 서버 장애나 인증 만료가 로컬 타이머 실행을 중단시키지 않는다.
- 사용자는 동기화 중, 오프라인, 실패, 충돌 상태를 구분할 수 있다.

### 초기 서비스 수준 목표

운영 데이터가 쌓이기 전까지 다음 값을 초기 목표로 사용하고 측정 결과에 따라
조정한다.

| 지표                                 | 목표                            |
| ------------------------------------ | ------------------------------- |
| 로컬 UI 반영 시간                    | 네트워크와 무관하게 100ms 이내  |
| 같은 리전의 온라인 기기 간 전파 지연 | p50 250ms 이하, p95 1초 이하    |
| WebSocket 연결 복구                  | 네트워크 복구 후 p95 5초 이하   |
| 확정된 변경 내구성                   | 서버 성공 응답 후 변경 유실 0건 |
| 중복 적용                            | 같은 `operationId`에 대해 0건   |
| 동기화 API 가용성                    | 월 99.9%를 초기 목표로 측정     |

기기 간 지연시간은 클라이언트 변경 발생 시점부터 다른 온라인 클라이언트가
변경을 로컬 repository에 적용한 시점까지 측정한다. 클라이언트 시계 차이를
피하기 위해 서버 수신, 커밋, 발행 시각도 함께 기록한다.

## 범위와 비범위

### 첫 번째 서버 릴리스 범위

- OIDC 기반 사용자 인증과 사용자별 접근 제어
- 타이머 생성, 조회, 편집, 삭제
- 시작, 일시정지, 재개, 초기화, 즉시 완료 명령
- 낙관적 동시성 제어와 멱등 요청 처리
- WebSocket 기반 실시간 변경 알림
- cursor 기반 증분 동기화와 재연결 복구
- 로컬 outbox를 이용한 오프라인 변경 업로드
- 동기화 상태, 로그, 지표와 운영 경보

### 첫 번째 서버 릴리스 비범위

- 서버가 매초 타이머 값을 계산해 전송하는 기능
- 오버레이 위치, 크기, 모니터 정보 등 장치 종속 설정 동기화
- 타이머 공동 편집 화면이나 사용자 간 공유
- 세션 통계, 검색, 공개 프리셋과 소셜 기능
- 여러 리전에 걸친 active-active 쓰기

서버는 타이머의 기준 상태와 전이 시각만 저장한다. 남은 시간과 완료 여부는
클라이언트가 현재 시각으로 계산하며, 실행 중인 타이머를 위해 초 단위 tick을
서버에서 방송하지 않는다.

## 목표 아키텍처

```text
Renderer
   │ typed IPC
Electron main
   │
Local Timer Service
   ├─ Local Repository        모든 읽기와 쓰기의 우선 경로
   ├─ Durable Outbox          미전송 명령과 재시도 상태
   ├─ Sync Cursor             마지막으로 적용한 서버 변경 순서
   └─ Sync Engine
        ├─ HTTPS push/pull    명령 제출과 누락 변경 복구
        └─ WebSocket          커밋된 변경의 최소 지연 알림
                 │
            API Service
             ├─ AuthZ
             ├─ Timer Command Handler
             ├─ Sync API
             └─ Realtime Gateway
                 │
              PostgreSQL
             ├─ timers
             ├─ timer_operations
             ├─ change_log
             └─ outbox_events
```

WebSocket은 빠른 알림 경로이며 유일한 신뢰 경로가 아니다. 연결 누락, 프로세스
재시작, 메시지 유실은 항상 영속적인 `change_log`와 sync cursor를 이용한 pull로
복구한다. 클라이언트는 WebSocket 메시지에 포함된 변경을 적용하거나, cursor가
건너뛴 경우 즉시 증분 pull을 수행한다.

## 데이터 모델

### 타이머

서버 타이머 레코드는 최소한 다음 정보를 가진다.

```text
id                 UUID, 클라이언트에서 생성
user_id            소유 사용자
title              타이머 제목
duration_ms        기본 지속 시간
repeat             반복 여부
state_type         paused | running
remaining_ms       paused일 때의 남은 시간
started_at         running 기준 시각
expires_at         running 만료 시각
version            타이머별 단조 증가 버전
created_at         서버 생성 시각
updated_at         서버 확정 시각
deleted_at         nullable tombstone
```

`expired`는 저장하지 않고 `expires_at <= now`로 계산한다. 이는
[ADR 0001](decisions/0001-derived-timer-completion.md)의 결정을 유지한다.

### 처리된 명령

`timer_operations`는 `(user_id, operation_id)`에 unique 제약을 두고 요청 결과를
보관한다. 네트워크 응답이 유실되어 클라이언트가 같은 명령을 다시 보내도 기존
결과를 반환한다.

각 명령은 다음 필드를 포함한다.

- `operationId`: 클라이언트가 생성한 전역 고유 ID
- `timerId`: 대상 타이머 ID
- `deviceId`: 진단과 충돌 설명에 사용하는 설치 ID
- `type`: create, edit, start, pause, resume, reset, complete, delete
- `expectedVersion`: 클라이언트가 명령을 만들 때 본 버전
- `occurredAt`: 클라이언트에서 사용자가 조작한 시각
- `payload`: 명령별 검증된 입력

### 변경 로그와 서버 outbox

모든 성공한 타이머 변경에는 사용자별로 정렬 가능한 `changeSeq`를 발급한다.
타이머 갱신, 명령 처리 기록, `change_log`, 실시간 발행용 `outbox_events`를 한 DB
트랜잭션으로 커밋한다. 별도 publisher가 outbox event를 WebSocket 계층에
전달하고 성공 후 처리 완료로 표시한다.

이 transactional outbox 구조는 DB에는 반영됐지만 WebSocket 알림은 사라지는
dual-write 문제를 막는다. 발행이 중복될 수 있으므로 클라이언트는 `changeSeq`와
`operationId`로 중복을 제거한다.

### 삭제

삭제는 tombstone으로 기록하고 다른 기기가 삭제를 확인할 수 있을 만큼 보존한다.
초기 정책은 30일이며, 모든 활성 기기의 cursor 진행 상황을 알 수 있게 된 뒤
조정한다. 오래된 클라이언트가 삭제 전 레코드를 다시 올리더라도 자동으로
복원하지 않는다.

## API와 실시간 프로토콜

### HTTPS API

```text
POST /v1/sync/push
GET  /v1/sync/pull?after=<changeSeq>&limit=<n>
GET  /v1/timers/:timerId
GET  /v1/sync/bootstrap
```

`push`는 명령 batch를 받지만 각 타이머의 명령 순서를 유지한다. 일부 명령만
실패할 수 있으므로 명령별 결과를 반환한다. `bootstrap`은 새 기기나 cursor가
보존 기간보다 오래된 경우 압축된 현재 상태와 기준 cursor를 반환한다.

주요 오류 코드는 다음과 같이 고정한다.

- `401 token_expired`: 토큰 갱신 후 동일 operation을 재시도할 수 있음
- `403 forbidden`: 소유권이 없으며 재시도하지 않음
- `409 version_conflict`: 서버 최신 레코드와 버전을 함께 반환
- `410 cursor_expired`: bootstrap이 필요함
- `422 invalid_transition`: 현재 상태에서 허용되지 않는 명령
- `429 rate_limited`: `Retry-After`를 따라 재시도

API 계약은 OpenAPI와 공유 schema로 버전 관리한다. renderer가 직접 호출하지
않고 Electron main의 sync engine만 네트워크와 토큰에 접근한다.

### WebSocket 프로토콜

WebSocket 연결은 인증 후 사용자 전용 변경 스트림을 구독한다.

```text
client -> hello { deviceId, lastAppliedSeq }
server -> ready { serverTime, latestSeq }
server -> change { changeSeq, operationId, timerId, record }
client -> ack { changeSeq }
server -> resync_required { fromSeq }
```

- 서버는 DB 커밋이 끝난 변경만 발행한다.
- 한 연결 안에서는 `changeSeq` 순서로 전달한다.
- 클라이언트가 이미 적용한 sequence는 무시한다.
- sequence에 공백이 있으면 해당 변경을 적용하기 전에 pull로 복구한다.
- ping/pong과 idle timeout으로 끊어진 연결을 빠르게 발견한다.
- 재연결은 즉시 한 번 시도한 뒤 exponential backoff와 jitter를 사용한다.
- WebSocket 장애가 지속되면 짧은 주기의 pull fallback을 사용한다.

첫 배포는 단일 리전 API와 realtime gateway로 시작한다. 인스턴스가 여러 개면
PostgreSQL outbox publisher와 관리형 pub/sub을 이용해 연결이 있는 모든
gateway에 변경을 fan-out한다. pub/sub 자체는 내구성 원장이 아니며 DB 변경
로그가 최종 복구 수단이다.

## 동시성 및 충돌 정책

### 기본 규칙

서버는 타이머 행을 잠그고 `expectedVersion`을 비교한 뒤 상태 전이와 버전 증가를
하나의 트랜잭션으로 수행한다. 버전이 다르면 마지막 쓰기 승리로 덮어쓰지 않고
`409 version_conflict`를 반환한다.

| 변경                      | 정책                                            |
| ------------------------- | ----------------------------------------------- |
| 생성                      | 클라이언트 UUID와 `operationId`로 멱등 처리     |
| 제목·지속 시간·반복 편집  | 버전 비교, 충돌 시 최신 상태를 사용자에게 제시  |
| 시작·일시정지·재개·초기화 | 상태 전이와 버전을 원자적으로 검증              |
| 즉시 완료                 | 타이머 버전 또는 실행 cycle ID로 중복 완료 방지 |
| 삭제와 편집 경합          | tombstone 우선, 명시적인 복원 명령만 허용       |
| 서로 다른 타이머 변경     | 독립적으로 처리하고 서로 차단하지 않음          |

클라이언트는 로컬 optimistic update를 유지한다. 서버가 충돌을 반환하면 서버
상태를 적용하고 미반영 로컬 명령을 `conflict`로 보관한다. 타이머 실행 명령은
의미가 달라질 수 있으므로 최신 상태 위에 자동 재실행하지 않는다. 단순 메타데이터
편집만 안전한 경우에 한해 field-level merge를 이후 단계에서 도입한다.

### 시간 처리

- 서버와 클라이언트는 UTC epoch millisecond를 교환한다.
- 연결과 API 응답에 `serverTime`을 포함해 클라이언트가 clock offset을 추정한다.
- 서버는 허용 범위를 벗어난 미래·과거 `occurredAt`을 거부하거나 보정한다.
- 명령의 네트워크 지연 때문에 서버 수신 시각부터 타이머를 다시 시작하지 않는다.
- 반복 주기는 기존 만료 시각을 기준으로 전진시켜 지연 누적을 막는다.
- 시간 오차 정책과 허용 범위는 자동 테스트와 운영 지표를 통해 확정한다.

## 인증과 보안

- 시스템 브라우저에서 OIDC Authorization Code + PKCE 로그인을 수행한다.
- access token은 짧게 유지하고 refresh token rotation을 사용한다.
- refresh token은 Windows Credential Manager 등 OS 보안 저장소에 보관한다.
- 토큰, client secret, 인증 응답을 renderer나 로그에 노출하지 않는다.
- 서버는 모든 요청과 WebSocket 연결에서 사용자와 리소스 소유권을 확인한다.
- WebSocket 티켓은 짧은 수명의 일회성 값으로 교환하거나 표준 bearer 인증을
  사용하되 URL query와 일반 로그에는 토큰을 남기지 않는다.
- 사용자·IP·연결 수 기준 rate limit과 payload 크기 제한을 적용한다.
- 로그아웃 시 원격 연결과 토큰을 폐기하되 로컬 타이머는 유지한다.
- 최초 로그인과 계정 전환 시 로컬 데이터의 업로드 대상을 명시적으로 확인한다.

## 단계별 구현 계획

각 단계는 가능하면 독립 PR로 나누며, 정상 흐름과 실패 또는 경합 흐름 테스트를
같은 PR에 포함한다.

### S0 — 로컬 명령 모델 확정

목표: 서버와 동기화할 수 있는 안정된 도메인 경계를 만든다.

- 기존 로드맵의 편집·삭제와 실행 제어를 완성한다.
- 객체 전체 덮어쓰기 대신 명시적인 타이머 명령을 도입한다.
- 모든 변경을 Electron main의 단일 `TimerService`에서 처리한다.
- 빠른 연속 조작, 중복 완료, 삭제 중 조작을 단위 테스트한다.
- 타이머와 장치별 오버레이 설정의 저장 경계를 분리한다.

완료 기준: 로컬에서 지원하는 모든 타이머 변경이 이름과 입력이 정해진 명령으로
표현되고 결정적으로 테스트된다.

### S1 — 동기화 가능한 로컬 저장소

목표: 네트워크가 없어도 변경 기록과 순서를 잃지 않는다.

- 저장 schema version과 기존 JSON migration을 추가한다.
- 타이머별 version, sync 상태, tombstone을 추가한다.
- durable outbox, device ID, last applied server cursor를 저장한다.
- 타이머 변경과 outbox 추가를 원자적으로 처리한다.
- 장기 저장소로 SQLite 도입을 검토하고 짧은 spike로 패키징 호환성을 검증한다.
- 종료와 비정상 종료 후 outbox 복구를 테스트한다.

완료 기준: 임의 시점에 프로세스를 종료해도 확인된 로컬 변경이 다음 실행에서
복구되고 전송 대기 상태로 남는다.

### S2 — 서버 골격과 데이터베이스

목표: 인증 전 환경에서 타이머 명령을 안전하게 직렬화한다.

- Kotlin/Spring Boot API, PostgreSQL, Flyway migration, 환경 설정을 구성한다.
- timers, timer_operations, change_log, outbox_events schema를 만든다.
- OpenAPI와 오류 응답 형식을 확정한다.
- 멱등 명령 처리와 version compare-and-swap을 구현한다.
- 같은 타이머에 대한 병렬 명령 통합 테스트를 작성한다.
- CI에서 lint, typecheck, unit, DB integration test를 실행한다.

완료 기준: 중복 요청은 한 번만 적용되고, 경쟁 요청 중 하나만 예상 버전으로
성공하며, 성공한 변경마다 연속적인 change log가 남는다.

### S3 — 인증과 인가

목표: 사용자별 데이터 경계를 보장한다.

- 검증된 OIDC 공급자와 PKCE 로그인을 연동한다.
- access/refresh token 수명, rotation, revoke 정책을 정한다.
- Electron main에 credential 저장과 자동 갱신을 구현한다.
- API와 WebSocket에 동일한 사용자 소유권 규칙을 적용한다.
- 로그아웃, 만료, 폐기, 계정 전환 테스트를 작성한다.

완료 기준: renderer가 토큰에 접근하지 않고 다른 사용자의 timer ID를 알아도
조회하거나 변경할 수 없다.

### S4 — 단방향 백업과 재시도

목표: 로컬 변경을 사용자 경험을 막지 않고 서버에 보낸다.

- outbox batch push와 명령별 결과 처리를 구현한다.
- 인증 갱신, 네트워크 오류, rate limit별 재시도 정책을 분리한다.
- exponential backoff, jitter, 최대 batch 크기를 적용한다.
- 성공한 operation만 outbox에서 확정 제거한다.
- 로컬 저장됨, 동기화 중, 실패 상태를 UI에 제공한다.

완료 기준: 서버가 장시간 중단되어도 로컬 기능이 정상 동작하고, 복구 후 모든
변경이 중복 없이 업로드된다.

### S5 — 증분 pull과 재연결 복구

목표: 다른 기기의 변경을 유실 없이 가져온다.

- 사용자별 change sequence와 cursor pull API를 구현한다.
- 새 기기를 위한 bootstrap snapshot을 구현한다.
- 앱 시작, 로그인, 네트워크 복구 시 pull을 수행한다.
- 원격 변경과 로컬 repository 갱신을 원자적으로 처리한다.
- cursor 만료와 tombstone 정리를 구현한다.

완료 기준: 임의 구간의 응답과 연결을 유실해도 마지막 cursor 이후 변경을 다시
받아 동일한 상태로 수렴한다.

### S6 — WebSocket 최소 지연 동기화

목표: 온라인 기기 간 변경을 p95 1초 이내에 전달한다.

- 인증된 WebSocket gateway와 사용자별 구독을 구현한다.
- transactional outbox publisher와 gateway fan-out을 구현한다.
- sequence gap 감지, ack, reconnect, pull fallback을 구현한다.
- foreground/background와 절전 복귀 시 연결 상태를 복구한다.
- 여러 API/gateway 인스턴스 환경의 전달을 검증한다.
- 변경 발생부터 원격 적용까지 end-to-end latency metric을 기록한다.

완료 기준: 정상 네트워크에서 초기 지연시간 SLO를 만족하고, WebSocket 메시지를
의도적으로 누락·중복·역순 전달해도 cursor pull을 통해 최종 수렴한다.

### S7 — 충돌 UX와 다중 기기 검증

목표: 충돌이 사용자 변경의 조용한 유실로 이어지지 않게 한다.

- 서버 최신값, 로컬 명령, 발생 기기와 시각을 보여주는 충돌 모델을 구현한다.
- 유지, 서버값 사용, 명시적 재적용 흐름을 제공한다.
- 삭제와 수정, 시작과 일시정지, reset과 complete 경합을 테스트한다.
- 서로 다른 기기의 clock skew와 장기 오프라인 시나리오를 테스트한다.
- 실제 Electron 인스턴스 2개 이상을 이용한 E2E 환경을 만든다.

완료 기준: 정의된 모든 경합에서 서버와 클라이언트 결과가 결정적이며, 사용자의
미반영 변경을 확인할 수 있다.

### S8 — 운영 준비와 출시

목표: 장애를 탐지하고 데이터 손실 없이 복구할 수 있다.

- 요청 ID, operation ID, device ID를 연결한 구조화 로그를 추가한다.
- push/pull 성공률, 충돌률, outbox 적체, 연결 수, 전파 지연을 계측한다.
- SLO burn-rate와 publisher 적체 경보를 구성한다.
- DB backup, point-in-time recovery와 복구 훈련을 수행한다.
- 부하, reconnect storm, 느린 consumer, DB failover를 시험한다.
- 데이터 내보내기, 계정 삭제, 보존 정책을 구현한다.
- staging에서 점진 배포 후 제한된 사용자에게 활성화한다.

완료 기준: 장애 시나리오별 runbook과 복구 결과가 있고, 지연시간과 오류율을
대시보드에서 확인할 수 있다.

## 권장 PR 순서

1. 로컬 `TimerService`와 명령 API
2. 저장 schema version과 migration
3. durable outbox, device ID, sync cursor
4. 서버 schema와 OpenAPI
5. 멱등 처리와 낙관적 잠금
6. change log와 transactional outbox
7. OIDC/PKCE 인증과 인가
8. 클라이언트 push와 재시도
9. bootstrap과 cursor pull
10. WebSocket gateway와 publisher
11. sequence gap 복구와 pull fallback
12. 충돌 UI와 다중 기기 E2E
13. 지연시간 계측, 부하 테스트와 운영 runbook

## 필수 검증 시나리오

- 같은 타이머를 두 기기가 동시에 편집한다.
- 한 기기가 삭제하는 동안 다른 기기가 시작한다.
- pause가 서버에 커밋된 뒤 응답만 유실되어 같은 요청을 재전송한다.
- WebSocket 알림보다 push 응답이 먼저 또는 나중에 도착한다.
- WebSocket 메시지 하나가 누락되거나 중복되거나 역순으로 도착한다.
- 서버 커밋 직후 API 또는 gateway 인스턴스가 종료된다.
- 토큰 갱신 중 outbox에 새 명령이 계속 추가된다.
- 절전 상태에서 여러 변경이 발생한 뒤 클라이언트가 복귀한다.
- 로컬 시계가 서버보다 크게 앞서거나 뒤처진다.
- 장기 오프라인 기기가 정리된 tombstone 이전 상태를 다시 전송한다.
- 다수 클라이언트가 동시에 재연결하는 reconnect storm이 발생한다.
- 느린 기기의 ack가 지연되어도 다른 기기의 전달이 막히지 않는다.

## 출시 게이트

다음 조건을 모두 만족한 뒤 기본 활성화를 고려한다.

- 로컬 전용 모드의 기존 기능과 E2E가 회귀 없이 통과한다.
- 서버를 강제로 차단해도 로컬 타이머 조작과 재실행 복구가 동작한다.
- 동시성, 멱등성, cursor 복구 테스트가 결정적으로 통과한다.
- 두 기기 E2E에서 누락·중복·역순 메시지 후 최종 상태가 일치한다.
- 부하 테스트에서 초기 전파 지연 SLO를 만족한다.
- 인증 토큰이 renderer, URL, 일반 로그에 나타나지 않는다.
- 백업 복원과 publisher 장애 복구 절차를 실제로 실행해 확인한다.
- 원격 기능을 즉시 비활성화할 수 있는 client/server feature flag가 있다.

## 후속 확장 판단 기준

단일 리전 구조로 지연시간 목표를 달성하지 못하거나 사용자가 여러 대륙에
분산되는 시점에 edge connection termination과 다중 리전 read 경로를 검토한다.
다중 리전 쓰기는 충돌 모델과 운영 복잡도를 크게 높이므로 측정된 요구 없이
도입하지 않는다.

WebSocket 연결 비용이 실제 사용 패턴에 비해 크다면 앱 foreground 또는 실행
중인 타이머가 있을 때만 상시 연결하고, 나머지 시간에는 push notification이나
주기적 pull을 사용하는 적응형 연결 정책을 검토한다. 어떤 최적화도 change log와
cursor 기반 복구 경로를 대체하지 않는다.
