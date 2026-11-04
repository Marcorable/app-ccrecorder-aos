# ADR-0001: 녹음·재생 상태와 복구 계약

- 상태: Accepted
- 날짜: 2026-11-04
- 이슈: MAR-16

## 맥락

iOS v2는 `RecordScreen.Root.Feature.Phase`와 `PlaybackPanel.Feature.State`로 화면 상태를 관리하고, 실제 오디오 객체는 `RecorderServiceConcrete`와 `PlayerServiceConcrete`가 소유한다. Android는 녹음 foreground service, 런타임 권한, 오디오 포커스, UI 재생성 때문에 화면 Boolean만으로 상태를 표현하면 중복 명령과 파일 유실 경로가 생긴다.

특히 다음 결과를 고정해야 한다.

- 녹음 시작·일시정지·재개·완료·취소의 합법 전이
- 저장 실패와 화면 이탈 때 녹음물이 의도치 않게 남지 않는 규칙
- 앱이 백그라운드로 갔을 때와 프로세스가 사라졌을 때의 차이
- 재생 준비·중단·완료·해제의 의미

## 결정

### 1. 소유권

- 실행 중 녹음 세션의 정본은 MAR-14의 foreground service다. UI는 service state를 관찰하고 command를 보낸다.
- 저장된 Conversation과 파일 수명주기의 정본은 MAR-15의 Data 계층이다.
- 화면 ViewModel/Reducer는 service/Repository 결과를 상태로 투영할 뿐 녹음기나 플레이어 객체를 직접 소유하지 않는다.
- 녹음 command에는 `sessionId == conversationId`를 포함한다. 현재 세션과 다른 ID의 늦은 응답은 무시한다.

### 2. 녹음 상태

```text
NoSession
   │ record entry + permission granted
   ▼
Preparing ──success──▶ Ready ──start──▶ Recording ◀──resume── Paused
   │                    │                  │   │                 │
   │ failure            │ cancel           │   └──pause─────────┘
   ▼                    └──────────────┐   │
PrepareFailed                           │   │ finish confirmation
   │ close                             │   ▼
   └──────────────────────────────▶ Cancelling      Saving
                                      │               │
                                      ▼        success│ failure
                                  Finished ◀──────────┤
                                                      ▼
                                                 SaveFailed
                                                      │ close
                                                      ▼
                                                  Finished
```

상태 의미와 허용 명령은 다음과 같다.

| 상태 | 의미 | 허용 사용자 명령 |
| --- | --- | --- |
| `NoSession` | 서비스와 파일 없음 | 녹음 진입 |
| `Preparing` | 디렉터리, 임시 파일, recorder, FGS 준비 중 | 취소만. 중복 prepare 금지 |
| `Ready` | recorder 준비, 아직 유효 샘플 없음 | 시작, 취소 |
| `Recording` | 샘플 기록 중 | 일시정지, 핀 추가, 완료, 취소 |
| `Paused` | 같은 파일에 이어 녹음 가능 | 재개, 완료, 취소 |
| `Saving` | recorder 종료 및 파일/DB commit 중 | 없음. 중복 저장·취소 금지 |
| `PrepareFailed` | 준비 실패, 임시 자원 정리 대상 | 닫기 |
| `SaveFailed` | 저장 실패, 해당 세션 복구 불가 | 닫기 |
| `Cancelling` | service stop과 파일 정리 중 | 없음 |
| `Finished` | 단말 상태, 서비스/임시 자원 없음 | 화면 닫기만 |

추가 규칙:

- `Ready → Saving`은 금지한다. `elapsedMs > 0`인 Recording/Paused에서만 완료할 수 있다.
- 완료 버튼은 먼저 확인을 받고 `Saving`으로 전이한다. Recording이면 recorder stop이 완료 동작에 포함된다.
- 취소 버튼과 완료 전 화면 이탈은 확인 여부만 다르고 같은 cleanup use case를 사용한다.
- pause/start 명령은 현재 상태가 맞지 않으면 오류 없이 성공한 것처럼 상태를 바꾸지 않고 무시 또는 `InvalidState`를 반환한다.
- ticker는 100ms 수준으로 UI를 갱신할 수 있으나 업무 시간은 service의 monotonic elapsed time이 정본이다.
- 핀 `offsetMs`는 service가 보고한 현재 경과 시간으로 생성한다. UI 애니메이션 시간으로 만들지 않는다.
- `Preparing`, `Saving`, `Cancelling`은 같은 세션에서 한 번만 실행한다.

### 3. 녹음 오류

| 오류 | 사용자 결과 | 정리 |
| --- | --- | --- |
| `PermissionDenied` | 녹음 화면에 진입하지 않고 권한 안내/설정 이동 | session 생성 안 함 |
| `PrepareFailed` | 준비 실패 안내 후 닫기 | recorder release, FGS stop, temp 삭제 |
| `StartFailed` | 준비 실패와 같은 단말 처리 | recorder release, FGS stop, temp 삭제 |
| `FinishFailed` | 저장 실패 안내 | recorder release, temp/final 삭제, DB row 없음 |
| `FileCommitFailed` | 저장 실패 안내 | temp/final 삭제, DB row 없음 |
| `DatabaseCommitFailed` | 저장 실패 안내 | final 삭제 시도, 실패하면 startup reconciliation이 정리 |
| `InvalidState` | 중복 탭이면 무시, 구현 오류이면 진단 | 현재 안전 상태 유지 |

저장 실패 뒤 같은 세션의 재시도는 허용하지 않는다. recorder가 이미 종료되었거나 파일이 부분 확정되었을 수 있기 때문이다. 오류 대화상자의 닫기는 cleanup 완료를 기다린 뒤 화면을 닫는다.

### 4. 백그라운드, UI 재생성, 앱 재시작

- 사용자가 홈으로 이동하거나 화면이 잠겨도 이미 Recording/Paused인 세션은 foreground service가 유지한다.
- Activity/Compose가 재생성되면 service의 `sessionId`, 상태와 elapsed time을 다시 관찰한다. 핀과 제목·주제·참여자 draft는 화면 SavedState로 같은 프로세스 구성 변경을 견딘 뒤 동일한 `sessionId`에 다시 결합한다.
- 사용자가 녹음 화면을 명시적으로 닫으면 완료되지 않은 세션을 취소한다.
- service는 sticky 재시작으로 녹음을 추측 복원하지 않는다. 프로세스 종료/강제 종료 뒤 Android 1.0은 녹음을 이어서 복구하지 않는다.
- 다음 앱 초기화에서 `audio/pending/`을 정리한다. final 파일과 DB commit 경계는 ADR-0002를 따른다.
- 프로세스 종료가 DB commit 이후라면 저장된 Conversation은 유지된다. 이전이면 사용자에게 완료된 대화로 보이지 않는다.

### 5. 재생 상태

```text
Released ──open detail──▶ Preparing ──success──▶ Ready
                              │                    │  ▲
                              │ failure            │  │ pause / natural end
                              ▼                    ▼  │
                            Error                Playing

Ready/Playing ──leave detail──▶ Released
Playing ──focus loss/interruption──▶ Ready + Interrupted event
```

| 상태 | 계약 |
| --- | --- |
| `Released` | player 없음, position/duration 0, rate 1.0 |
| `Preparing` | 상대 경로 검증, 존재 확인, Media3 prepare 중; 컨트롤 비활성 |
| `Ready` | player 준비, 일시정지 상태; seek/rate/play 가능 |
| `Playing` | ticker로 service position을 표시 |
| `Error` | player release, 컨트롤 비활성, 누락/준비 실패 표시 |

재생 규칙:

- seek는 `[0, durationMs]`로 clamp한다.
- rate는 UI 허용값 중 하나만 받으며 service 방어 범위는 `[0.5, 2.0]`이다.
- 자연 종료는 `Ready(position=0)`으로 간다.
- 일시적 오디오 포커스 손실 또는 interruption은 pause하고, 직전에 Playing이었다면 `Interrupted` 사용자 이벤트를 한 번 보낸다. 자동 재개하지 않는다.
- 상세 화면 이탈은 prepare/ticker/seek/rate job을 취소하고 player를 release한다.
- 늦은 prepare/ticker 응답은 현재 media/session token이 다르면 무시한다.

### 6. 동시성·멱등성

- 녹음 service command와 저장 orchestration은 단일 직렬 command queue 또는 Mutex로 보호한다.
- `cancel(sessionId)`는 여러 번 호출해도 최종적으로 session/file이 없는 성공 상태가 된다.
- `releasePlayback(mediaId)`도 멱등이다.
- `finish(sessionId)`는 한 번만 commit을 시작한다. 같은 sessionId의 두 번째 호출은 첫 결과를 공유하거나 `InvalidState`로 거부한다.
- coroutine cancellation은 사용자 취소와 다르다. 저장 coroutine이 취소되어도 `finally`/별도 non-cancellable cleanup이 파일 불변 조건을 완성해야 한다.

## 대안과 이유

- UI가 녹음기 객체를 소유: 구성 변경과 백그라운드에서 정본이 둘로 갈라져 채택하지 않았다.
- 저장 실패 뒤 같은 recorder 재사용: recorder stop 이후 안전한 재개를 보장할 수 없어 채택하지 않았다.
- 프로세스 재시작 뒤 임시 녹음 복구: 컨테이너 완결성과 사용자 기대를 검증할 제품/UI가 없어 1.0에서 제외했다.
- 오디오 포커스 회복 시 자동 재생: iOS가 자동 재개하지 않고 의도치 않은 소리를 낼 수 있어 채택하지 않았다.

## 결과

MAR-14는 이 상태와 오류를 service/Media3 구현 및 테스트에 그대로 사용한다. MAR-13 UI는 파생 Boolean을 임의 조합하지 않고 상태 enum으로 버튼 활성화와 화면 이탈을 결정한다. 파일과 DB의 구체 commit/rollback은 ADR-0002가 담당한다.
