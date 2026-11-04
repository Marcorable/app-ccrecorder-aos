# Android 1.0 로컬 데이터 계약

## 1. 원칙

- Domain 모델은 Room, DataStore, `File`, Android 프레임워크 타입을 노출하지 않는다.
- Room record와 Domain entity 변환은 Data 계층이 담당한다.
- Aggregate root는 `Conversation`과 `Note`다. `Pin`과 대화 참여자 스냅샷은 Conversation 안에서만 변경한다.
- 여러 테이블을 바꾸는 한 업무 동작은 `RoomDatabase.withTransaction` 하나에서 실행한다.
- DB와 파일 시스템은 원자적 트랜잭션을 공유하지 않으므로 [ADR-0002](adr/0002-local-persistence-and-audio-file-lifecycle.md)의 순서와 복구 작업을 사용한다.
- ID와 상대 경로는 Domain 값이다. 절대 경로와 Android 저장소 객체는 Data 내부에 둔다.

## 2. 공통 표현

| 값 | 저장 표현 | 계약 |
| --- | --- | --- |
| ID | `TEXT NOT NULL` | RFC 4122 UUID의 소문자 하이픈 문자열. 앱에서 생성하며 Room auto id를 쓰지 않는다. |
| 생성 시각 | `INTEGER NOT NULL` | Unix epoch millisecond UTC (`Long`). 표시할 때만 현재 locale/time zone을 적용한다. |
| 경과·핀·재생 길이 | `INTEGER` | millisecond (`Long`). 음수가 될 수 없다. 초 단위 실수는 DB 경계에서 반올림하지 않고 녹음/플레이어의 millisecond 값을 사용한다. |
| 순서 | `INTEGER NOT NULL` | 0부터 시작하는 연속 정수. 쓰기 트랜잭션에서 재번호를 부여한다. |
| Boolean | `INTEGER NOT NULL` | SQLite/Room Boolean 0 또는 1. |
| enum | `TEXT NOT NULL` | 아래에 명시한 안정적인 영문 토큰. 알 수 없는 값은 조용히 기본값으로 바꾸지 않는다. |

오디오 `durationMs`는 Room v1에 저장하지 않는다. 재생 준비 때 Media3가 계산하며 UI 상태에서만 사용한다. Conversation의 존재 조건은 유효한 메타데이터와 확정 상대 경로이고, 외부 손상으로 파일이 누락된 경우 메타데이터는 보존한 채 재생 불가로 표시한다.

## 3. Room v1 스키마

Room 데이터베이스 이름은 `ccrecorder.db`, 초기 `version = 1`, schema export는 활성화한다. 모든 foreign key를 Room schema에 선언하고 실제 SQLite foreign key enforcement가 켜진 상태를 계측 테스트로 확인한다.

### 3.1 `conversations`

| 열 | 타입/제약 | 의미 |
| --- | --- | --- |
| `id` | TEXT PK | Conversation ID |
| `created_at_epoch_ms` | INTEGER NOT NULL | 생성 시각 |
| `audio_relative_path` | TEXT NOT NULL UNIQUE | `audio/recordings/<conversation-id>.m4a` |
| `title` | TEXT NOT NULL | 표시 제목 |
| `topic` | TEXT NOT NULL DEFAULT `''` | 주제 |

인덱스: `(created_at_epoch_ms, id)` 및 unique `audio_relative_path`.

### 3.2 `conversation_pins`

| 열 | 타입/제약 | 의미 |
| --- | --- | --- |
| `conversation_id` | TEXT NOT NULL FK → conversations.id ON DELETE CASCADE | 소유 대화 |
| `position` | INTEGER NOT NULL CHECK >= 0 | 녹음 중 추가 순서 |
| `offset_ms` | INTEGER NOT NULL CHECK >= 0 | 대화 시작 기준 위치 |

기본키는 `(conversation_id, position)`이다. 같은 millisecond의 핀을 둘 이상 허용하므로 `offset_ms`를 키로 쓰지 않는다. 읽기는 `position ASC`, 다음 핀 탐색은 `offset_ms ASC`로 수행한다.

### 3.3 `notes`

| 열 | 타입/제약 | 의미 |
| --- | --- | --- |
| `id` | TEXT PK | Note ID |
| `created_at_epoch_ms` | INTEGER NOT NULL | 생성 시각 |
| `original` | TEXT NOT NULL DEFAULT `''` | 영어 원문 |
| `translation` | TEXT NOT NULL DEFAULT `''` | 한국어 번역/입력 |
| `category` | TEXT NOT NULL CHECK IN (`VOCABULARY`, `SENTENCE`) | 노트 분류 |

`original == '' && translation == ''`는 생성·편집 저장에서 거부한다. 기존 데이터 읽기 호환을 위해 DB CHECK로 두 문자열의 조합을 막지는 않는다.

### 3.4 `conversation_notes`

| 열 | 타입/제약 | 의미 |
| --- | --- | --- |
| `conversation_id` | TEXT NOT NULL FK → conversations.id ON DELETE CASCADE | 연결 대화 |
| `note_id` | TEXT NOT NULL FK → notes.id ON DELETE CASCADE | 연결 노트 |
| `position` | INTEGER NOT NULL CHECK >= 0 | 대화 안 표시 순서 |

기본키는 `(conversation_id, note_id)`다. `note_id`에 UNIQUE 인덱스를 두어 Android 1.0에서는 한 노트가 최대 한 대화에만 속하도록 한다. `(conversation_id, position)`도 UNIQUE다.

- 대화 삭제: cross-reference만 cascade 삭제하고 `notes`는 보존한다.
- 노트 삭제: note와 cross-reference를 cascade 삭제한다.
- 대화 상세에서 노트 삭제: 전체 노트 삭제와 같은 의미다.

### 3.5 `parties`

| 열 | 타입/제약 | 의미 |
| --- | --- | --- |
| `id` | TEXT PK | 전역 Party ID |
| `name` | TEXT NOT NULL | 현재 이름 |

Android 1.0 UI는 전역 Party 관리나 선택을 제공하지 않는다. 테이블은 iOS 모델 의미와 향후 호환을 위해 존재하며, 직접 입력한 참여자를 자동 등록하지 않는다.

### 3.6 `conversation_parties`

| 열 | 타입/제약 | 의미 |
| --- | --- | --- |
| `conversation_id` | TEXT NOT NULL FK → conversations.id ON DELETE CASCADE | 소유 대화 |
| `position` | INTEGER NOT NULL CHECK >= 0 | 표시 순서 |
| `snapshot_party_id` | TEXT NOT NULL | 대화에 저장된 Party UUID. Party 삭제 후에도 유지 |
| `snapshot_name` | TEXT NOT NULL | 대화 저장 당시 이름 |
| `live_party_id` | TEXT NULL FK → parties.id ON DELETE SET NULL | 현재 전역 Party 참조 |

기본키는 `(conversation_id, position)`다. `(conversation_id, snapshot_party_id)`에는 일반 인덱스를 둔다. 동일 Party/이름 중복을 허용하므로 UNIQUE로 만들지 않는다.

Domain 변환은 다음과 같다.

```text
Party.id          = snapshot_party_id
Party.name        = parties.name if live_party_id resolves else snapshot_name
Party.isAvailable = live_party_id != null and referenced party exists
```

전역 Party와 연결할 때 `snapshot_party_id == live_party_id`여야 한다. 직접 입력은 새 UUID를 `snapshot_party_id`로 만들고 `live_party_id = null`로 저장한다.

### 3.7 `pending_file_deletions`

| 열 | 타입/제약 | 의미 |
| --- | --- | --- |
| `relative_path` | TEXT PK | 삭제해야 하는 검증된 오디오 상대 경로 |
| `requested_at_epoch_ms` | INTEGER NOT NULL | 정리 요청 시각 |
| `attempt_count` | INTEGER NOT NULL DEFAULT 0 CHECK >= 0 | 실패 횟수 |

이 테이블은 사용자 Domain entity가 아닌 Data 복구 레코드다. 대화 삭제 트랜잭션은 이 행 삽입과 Conversation 삭제를 함께 커밋한다. 파일 삭제 성공 또는 이미 없음 확인 뒤 행을 제거한다. 실패 시 횟수를 올리고 다음 앱 초기화/백그라운드 정리에서 재시도한다.

## 4. 관계와 삭제 규칙

```text
Conversation 1 ──* Pin                    (대화 삭제 시 삭제)
Conversation 1 ──* ConversationParty      (대화 삭제 시 삭제)
Party        0..1 ──* ConversationParty   (Party 삭제 시 live 참조만 null)
Conversation 1 ──* ConversationNote *──1 Note
                                            (대화 삭제 시 Note 보존,
                                             Note 삭제 시 연결 삭제)
```

`ConversationWithDetails` 읽기는 Conversation, pins, party snapshots + optional live Party, notes를 한 일관된 Room snapshot에서 조합한다. UI가 여러 DAO를 순서대로 읽어 Aggregate를 직접 조립하지 않는다.

## 5. DAO 및 Repository 동작

정확한 Kotlin 이름은 모듈 규칙에 맞출 수 있으나 아래 업무 단위와 원자성은 바꾸지 않는다.

### Conversation 저장소

- `observeConversations()`: `created_at_epoch_ms ASC, id ASC`; 목록용 projection을 반환한다.
- `getConversation(id)`: 관계를 포함한 한 Aggregate를 반환한다.
- `createConversation(aggregate)`: conversation, pins, party snapshots를 한 트랜잭션에서 insert한다. ID나 경로 충돌은 실패다. `REPLACE`를 쓰지 않는다.
- `updateConversationInfo(id, title, topic, parties)`: 제목·주제·참여자 스냅샷만 한 트랜잭션에서 바꾼다. 생성 시각, 파일 경로, 핀, 노트 연결을 덮어쓰지 않는다.
- `addNote(conversationId, note)`: Note insert와 마지막 position의 cross-reference insert를 한 트랜잭션에서 수행한다.
- `deleteNotes(noteIds)`: Notes 삭제로 연결을 cascade 제거하고, 영향을 받은 대화의 position을 같은 트랜잭션에서 재번호한다.
- `requestConversationDeletion(id)`: cleanup row insert와 Conversation 삭제를 한 트랜잭션에서 수행한다. 파일 삭제는 커밋 뒤 수행한다.

### Note 저장소

- `observeNotes()`: 미완성 우선, 생성 시각 오름차순, ID 오름차순의 안정된 정렬을 제공한다.
- `getNote(id)`, `updateNote(note)`, `deleteNotes(ids)`를 제공한다.
- update/delete에서 대상이 없으면 성공으로 숨기지 말고 `NotFound` 업무 오류를 반환한다. delete의 명시적 재시도는 idempotent하게 별도 `deleteIfPresent` 동작을 사용할 수 있다.

### 파일 저장소

- Domain과 Presentation에는 상대 경로만 전달한다.
- `resolve(relativePath)`는 정규화 뒤 허용된 `audio/recordings/` 또는 내부 정리 경로 아래인지 검증한다.
- 입력에 절대 경로, `..`, 빈 세그먼트, 역슬래시, 심볼릭 링크 탈출이 있으면 거부한다.
- 파일 존재 확인과 삭제는 Data 계층에서 한다. 임의 사용자 경로를 받지 않는다.

## 6. Preferences DataStore

Preferences DataStore 이름은 `user_preferences`이고 생성 파일은 `user_preferences.preferences_pb`다. 값은 다음 계약을 지킨다.

| 키 | 타입 | 기본값 | 허용값/의미 |
| --- | --- | --- | --- |
| `onboarding_completed` | Boolean | `false` | 첫 온보딩 완료 여부 |
| `display_mode` | String | `SYSTEM` | `SYSTEM`, `LIGHT`, `DARK` |
| `skip_interval_seconds` | Int | `5` | `5`, `10`, `15`, `30` |
| `keep_screen_awake` | Boolean | `false` | 앱 전경에서 화면 자동 잠금 방지 |

알 수 없는 enum/범위 밖 숫자는 읽기 시 기본값으로 노출하되, 진단을 남기고 다음 정상 쓰기에서 정규화한다. 권한 상태, 녹음 중 여부, 현재 위치, 배속, 절대 파일 경로는 DataStore에 저장하지 않는다.

설정 쓰기는 실패할 수 있는 비동기 동작이다. UI는 낙관적으로 바꿀 수 있지만 실패 시 이전 값으로 되돌리고 사용자에게 알린다.

## 7. 오디오 경로와 형식

| 구분 | 계약 |
| --- | --- |
| base | `Context.filesDir` |
| 임시 디렉터리 | `audio/pending/` |
| 임시 파일 | `audio/pending/<conversation-id>.pending.m4a` |
| 최종 디렉터리 | `audio/recordings/` |
| 최종 파일 | `audio/recordings/<conversation-id>.m4a` |
| DB 저장값 | 최종 상대 경로만 저장 |
| container/codec | MPEG-4 `.m4a`, AAC, mono, 요청 sample rate 44,100Hz. 기기가 기록한 실제 format은 재생 가능해야 하며 구체 encoder bit rate는 사용자 계약이 아니다. |

Conversation ID는 녹음 준비 전에 한 번 생성해 임시·최종 이름과 DB 키에 공통으로 사용한다. 파일명은 시각 기반이 아니므로 같은 초에 시작한 녹음도 충돌하지 않는다.

임시와 최종 디렉터리는 같은 `filesDir` 파일 시스템에 두어 원자 rename/move가 가능하게 한다. rename 전에 recorder를 정상 종료하고 출력 스트림을 닫는다.

## 8. 마이그레이션 원칙

- 모든 릴리스의 Room schema JSON을 버전 관리한다.
- 배포된 버전 사이에는 명시적 `Migration`과 계측 마이그레이션 테스트를 둔다. `fallbackToDestructiveMigration`은 release 빌드에서 금지한다.
- 새 필수 열은 안전한 기본값 또는 create-copy-validate-swap 절차로 추가한다.
- enum 토큰 이름을 바꾸지 않는다. 변경이 필요하면 이전 토큰을 명시적으로 변환한다.
- 파일 경로 규칙 변경은 DB migration만으로 끝내지 않는다. 재시작 가능하고 idempotent한 별도 file migration 상태를 두며, DB가 새 경로를 가리키기 전에 파일이 존재하도록 순서를 설계한다.
- DataStore 키 이름은 안정적인 저장 계약이다. 이름 변경 시 기존 키를 읽고 새 키를 쓴 뒤 이전 키를 제거하는 일회성 migration을 제공한다.
- Android 1.0은 iOS·SwiftData·Core Data 파일을 가져오지 않는다.

## 9. 필수 검증 시나리오

MAR-15는 최소한 다음 테스트를 자동화한다.

1. Aggregate 생성/조회에서 핀, 참여자 순서, 노트 순서가 보존된다.
2. 전역 Party 이름 변경 시 연결 대화가 최신 이름을 표시한다.
3. Party 삭제 시 `live_party_id`만 null이 되고 snapshot ID/name은 남는다.
4. 대화 삭제 시 pins/party/cross-reference는 없어지고 Note는 남으며 cleanup 행이 생긴다.
5. Note 삭제 시 모든 cross-reference가 사라진다.
6. DB insert 실패 뒤 최종·임시 파일이 남지 않는다.
7. 파일 삭제 실패 뒤 대화는 보이지 않고 cleanup 행이 유지되며 재시도 성공 뒤 제거된다.
8. 시작 시 pending 파일과 미참조 final 파일을 정리한다.
9. DB가 참조하는 파일이 없으면 Conversation은 삭제하지 않고 `AudioMissing`으로 보고한다.
10. 각 DataStore 키의 기본값, 허용값, 재시작 유지, 손상 값 fallback을 검증한다.
11. v1 schema export를 기준으로 identity hash와 migration test 기반을 고정한다.
