# ADR-0002: 로컬 DB와 오디오 파일 수명주기

- 상태: Accepted
- 날짜: 2026-11-04
- 이슈: MAR-16

## 맥락

Room 트랜잭션과 파일 시스템 rename/delete는 하나의 원자적 트랜잭션으로 묶을 수 없다. 단순히 파일을 먼저 삭제하거나 DB를 먼저 쓰면 다음 실패 창이 생긴다.

- 파일은 있으나 Conversation이 없는 고아 파일
- Conversation은 있으나 파일이 없는 재생 불가 레코드
- 취소한 임시 파일이 앱 재시작 뒤 남음
- 대화 삭제 중 파일 오류가 나서 목록과 파일의 상태가 계속 어긋남

iOS `RecordingUsecaseConcrete`는 DB 저장 실패 시 파일을 지우고, 대화 삭제에서는 파일을 먼저 지운 뒤 DB를 삭제한다. Android는 같은 사용자 결과를 유지하되 crash recovery를 명시적으로 보강한다.

## 결정

### 1. 경로와 ID

- 녹음 진입 때 UUID를 하나 생성해 `sessionId == conversationId`로 사용한다.
- 임시 경로: `filesDir/audio/pending/<id>.pending.m4a`.
- 최종 경로: `filesDir/audio/recordings/<id>.m4a`.
- Room에는 `audio/recordings/<id>.m4a` 상대 경로만 저장한다.
- 임시와 최종 디렉터리는 같은 파일 시스템에 둔다. finalization은 recorder close 이후 atomic rename을 사용한다.
- 절대 경로, URI, `..`, 외부 저장소 경로는 DB에 저장하지 않는다.

### 2. 새 녹음 commit

```text
prepare temp
   ↓
record / pause / resume
   ↓ finish
stop + close recorder
   ↓
validate temp exists and non-empty
   ↓
atomic rename temp → final
   ↓
Room transaction: insert Conversation + Pin + Party snapshots
   ↓
success
```

순서의 불변 조건은 다음과 같다.

1. DB insert 전에 final 파일이 같은 상대 경로에 존재해야 한다.
2. Room transaction이 실패하면 final 파일 삭제를 즉시 시도한다.
3. 즉시 삭제가 실패하거나 프로세스가 rename과 insert 사이에서 종료되면 시작 시 orphan reconciliation이 삭제한다.
4. Room transaction commit 뒤에는 정상 앱 흐름이 파일을 자동 삭제하지 않는다.
5. DB insert는 `ABORT` 충돌 정책을 사용한다. 기존 row/file을 덮어쓰지 않는다.

이 순서에서는 crash 위치별 결과가 다음과 같다.

| crash 위치 | 재시작 상태 | 복구 |
| --- | --- | --- |
| recorder stop 전 | pending만 있을 수 있음, DB 없음 | pending 전체 삭제 |
| stop 후 rename 전 | pending, DB 없음 | pending 삭제 |
| rename 후 DB commit 전 | final orphan, DB 없음 | orphan scan으로 삭제 |
| DB transaction 중 | final, transaction rollback | orphan scan으로 삭제 |
| DB commit 후 | final + 완전한 Aggregate | 유지 |

### 3. 취소와 준비/저장 실패

취소는 다음 순서로 멱등 실행한다.

1. recorder stop/release
2. foreground service stop
3. 해당 session의 pending 삭제
4. DB commit 전 만들어진 해당 session final 삭제
5. 실행 중 세션 상태 제거

파일이 이미 없으면 성공이다. cleanup용 delete는 `FileNotFound`를 실패로 취급하지 않는다. 사용자가 성공 저장한 Conversation ID와 같은 세션을 취소 경로가 뒤늦게 받으면 DB 존재를 확인하고 final 파일을 삭제하지 않는다.

### 4. 대화 삭제

대화 삭제는 파일을 먼저 지우지 않는다. 다음처럼 정리 큐를 사용한다.

```text
Room transaction
  1. pending_file_deletions(relativePath) INSERT OR IGNORE
  2. conversations DELETE
     └─ pins / party snapshots / note links CASCADE
commit
   ↓
delete file (missing == success)
   ↓ success
delete pending_file_deletions row
```

- 트랜잭션 실패: Conversation과 파일 모두 유지되어 사용자가 재시도할 수 있다.
- 트랜잭션 성공·파일 삭제 실패: Conversation은 목록에서 사라지고 파일은 사용자에게 접근 불가한 orphan이다. cleanup row로 재시도한다.
- 파일 삭제 성공·cleanup row 삭제 실패: 다음 재시도에서 파일 없음은 성공으로 처리하고 row를 제거한다.
- 노트는 Conversation 삭제 트랜잭션에서 삭제하지 않는다.

### 5. 시작 시 reconciliation

앱의 Data 초기화에서 UI에 목록을 내보내기 전에 한 번 실행한다. 장시간 작업이면 목록 읽기를 막지 않고 별도 worker로 계속할 수 있지만 같은 파일에 대한 저장 작업과는 Mutex로 직렬화한다.

1. 녹음 service에 활성 세션이 없음을 확인하고 `audio/pending/`의 regular file을 삭제한다. Android 1.0은 녹음 초안을 복구하지 않는다.
2. `pending_file_deletions`을 오래된 순서로 처리한다.
3. Room의 모든 `audio_relative_path` 집합을 읽는다.
4. `audio/recordings/`의 regular file 중 참조되지 않은 파일을 삭제한다.
5. DB가 참조하지만 없는 파일은 DB를 자동 삭제하지 않는다. 진단에 기록하고 해당 Conversation을 `AudioMissing`으로 읽는다.
6. 허용 디렉터리 밖, 심볼릭 링크, 알 수 없는 확장자는 따라가거나 삭제하지 않고 진단한다.

Room 참조 경로 집합을 완전히 읽지 못하면 final orphan scan을 실행하지 않는다. DB 오류를 빈 참조 집합으로 해석하면 안 된다.

정상 저장과 reconciliation이 동시에 실행되지 않게 애플리케이션 범위 파일 저장소가 단일 Mutex를 사용한다. 시작 시 pending 일괄 정리는 녹음 service를 시작하기 전에만 수행한다. WorkManager의 주기 정리는 같은 잠금으로 cleanup queue와 final orphan만 처리하고 활성 세션의 pending 파일은 건드리지 않는다.

### 6. 외부 손상과 백업

- 사용자가 앱 데이터를 지우거나 OS가 파일을 손상한 경우 DB row만 남을 수 있다. 메타데이터와 노트는 보존하고 재생 불가를 표시한다.
- 앱 전용 파일과 Room의 Android Auto Backup 포함 여부는 같은 정책으로 설정해야 한다. 1.0에서 백업/복원을 제품 기능으로 보장하지 않으므로 둘 중 하나만 복원되는 구성을 만들지 않는다. MAR-15는 둘 다 제외하는 설정을 기본으로 검증한다.
- 사용자가 파일을 직접 고르는 Storage Access Framework 경로는 이 계약에 없다.

## Repository 오류 계약

Data 계층은 최소한 다음 오류 의미를 Domain에 매핑한다.

| 오류 | 의미 |
| --- | --- |
| `AudioPathInvalid` | 상대 경로 검증 실패 |
| `AudioFileMissing` | DB가 가리키는 파일 없음 |
| `AudioFinalizeFailed` | recorder close 뒤 atomic rename 실패 |
| `ConversationConflict` | ID 또는 final path 충돌 |
| `PersistenceFailed` | Room transaction 실패 |
| `CleanupPending` | 사용자 데이터 삭제는 commit됐으나 파일 정리가 재시도 대기 중 |

`CleanupPending`은 대화 삭제를 UI에서 되돌리는 오류가 아니다. 목록에서 삭제는 성공으로 처리하고 진단/백그라운드 재시도를 이어간다. 반면 Room transaction 실패는 삭제 실패로 표시하고 항목을 유지한다.

## 대안과 이유

- DB insert 후 파일 이동: crash 시 DB가 없는 파일을 가리키는 사용자 가시 손상이 생겨 채택하지 않았다.
- 대화 파일 삭제 후 DB delete: DB 실패 시 재생 불가 Conversation이 남아 채택하지 않았다.
- 파일 실패를 무시하고 큐 없이 종료: 영구 orphan을 검출할 근거가 없어 채택하지 않았다.
- 모든 시작 시 DB row와 누락 파일을 함께 삭제: 노트와 메타데이터까지 예고 없이 유실하므로 채택하지 않았다.
- 외부/public storage: 권한과 사용자 이동으로 경로 불변 조건을 보장하기 어려워 1.0에서 제외했다.

## 결과

MAR-15는 cleanup table, startup reconciliation, 실패 주입 테스트를 구현해야 한다. MAR-14는 recorder가 파일을 완전히 닫은 뒤에만 finalize를 요청한다. MAR-13은 `AudioMissing`과 저장 실패를 사용자에게 표시하고, 완료 응답 전에는 녹음 화면을 성공으로 닫지 않는다.
