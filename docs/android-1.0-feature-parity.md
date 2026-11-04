# Android 1.0 기능 동등 계약

## 1. 근거 범위

조사 기준은 별도 iOS 저장소 `Marcorable/CCRecorder`의 `develop` 커밋 `1171d16902e83e846c6fc74607486ef70021d44e`이다. 작업 지시에는 `CCRecorder/`와 `Project/`가 iOS v2 정본으로 적혀 있으나 이 커밋에는 `CCRecorder/` 디렉터리가 없다. 따라서 실제 존재하는 `Project/App/`의 Domain, Data, Presentation, App 코드를 v2 근거로 사용했다. `CasualConversation/`은 Android 1.0 계약의 근거로 사용하지 않았다.

Android 구현은 iOS 프레임워크 타입을 복제하지 않는다. 아래 사용자 결과와 업무 불변 조건을 맞추고, Android 기술 선택은 [로컬 데이터 계약](local-data-contract.md)과 ADR을 따른다.

## 2. 기능 동등 매트릭스

| 영역 | iOS v2 근거 | Android 1.0 계약 | 범위 |
| --- | --- | --- | --- |
| 첫 실행 | `MainScreen.Root.Feature.State`, `Key.isFirstLaunched`, `OnboardScreen.Root` | 최초 설치에서 온보딩을 표시하고 완료 후 다시 자동 표시하지 않는다. 설정에서 다시 볼 수 있다. | 필수 |
| 메인 탐색 | `MainScreen.Root`, `MainScreen.Tab` | 대화 목록과 전체 노트 목록의 두 탭, 중앙 녹음 진입, 설정 진입을 제공한다. | 필수 |
| 마이크 권한 | `MainScreen.Root.Feature`, `AudioSessionClient.Status` | 녹음 진입 시 권한 상태를 확인한다. 미결정이면 시스템 요청, 거부면 안내와 앱 설정 이동을 제공한다. 요청 중 중복 진입을 막는다. | 필수 |
| 녹음 준비 | `RecordScreen.Root.Feature`, `RecordingUsecaseConcrete.prepare()` | 앱 전용 임시 파일과 녹음 서비스를 준비한 뒤에만 시작 버튼을 활성화한다. 준비 실패 시 파일을 정리하고 닫기 가능한 오류를 표시한다. | 필수 |
| 시작·일시정지·재개 | `RecordScreen.Root.Feature.Phase`, `RecorderServiceConcrete` | 준비→녹음, 녹음→일시정지, 일시정지→재개를 지원한다. 경과 시간은 일시정지 중 증가하지 않는다. | 필수 |
| 핀 | `Conversation.pins`, `RecordScreen.PinGrid`, `PlaybackPanel.nextPinTapped` | 녹음 중 현재 시점에 핀 추가, 저장 전 핀 삭제, 재생 중 현재 위치보다 뒤의 가장 가까운 핀으로 이동을 지원한다. | 필수 |
| 녹음 완료 | `RecordScreen.Root.Feature.saveConfirmed`, `RecordingUsecaseConcrete.finish(_:)` | 0ms보다 긴 녹음만 저장할 수 있다. 확인 후 녹음을 중지하고 파일 확정과 대화 저장을 완료한 뒤 화면을 닫는다. | 필수 |
| 녹음 취소 | `confirmCancel`, `RecordingUsecaseConcrete.cancel()` | 확인 후 서비스와 임시 파일을 정리한다. 대화·핀·참여자 레코드를 만들지 않는다. 화면을 임의로 닫은 경우도 완료 전이면 취소와 같다. | 필수 |
| 저장 실패 | `RecordScreen.Root.AlertState.saveFailed`, `RecordingUsecaseFailure` | 실패 사실을 표시하고 해당 세션을 단말 상태로 만든다. 사용자 확인 후 닫으며, 파일과 DB 어느 쪽에도 불완전 결과를 남기지 않는다. 같은 세션의 재시도는 지원하지 않는다. | 필수 |
| 백그라운드 | iOS는 오디오 세션을 활성화하고 화면 생명주기에서 명시적 취소만 수행 | 녹음 중 홈 이동·화면 꺼짐은 foreground service로 녹음을 계속한다. UI 복귀 시 같은 실행 프로세스의 세션에 재연결한다. | 필수 |
| 앱/프로세스 재시작 | iOS에는 녹음 초안 복구 계약이 없음 | 완료되지 않은 녹음을 복구하지 않는다. 다음 초기화에서 임시·미참조 파일을 정리한다. DB 커밋이 끝난 저장만 유지한다. | 필수 |
| 대화 목록 | `MainScreen.ConversationList.Feature` | 생성 시각 오름차순(오래된 항목 먼저)으로 표시한다. 제목, 주제, 참여자 수, 생성일, 정보 완성 여부를 표시한다. | 필수 |
| 대화 삭제 | `ManagingConversationUsecaseConcrete.deleteConversations` | 대화와 관계 행을 제거하고 오디오 파일 삭제를 보장한다. 노트 자체는 보존한다. 실패는 사용자에게 알리고 재시도 가능한 정리 작업으로 남긴다. | 필수 |
| 대화 상세 | `ConversationDetailScreen.Root.Feature` | 제목·주제·참여자를 편집하고 노트를 추가·삭제하며 오디오를 재생한다. 편집은 1초 디바운스하고 화면 이탈 전에 최신 값을 저장 요청한다. | 필수 |
| 참여자 | `Party`, `Conversation.Record.PartySnapshot`, `PartyReferenceTests` | 입력 순서와 중복을 보존한다. 대화에는 이름 스냅샷을 남긴다. 전역 Party가 삭제되어도 과거 대화의 이름은 유지되고 선택 가능 상태만 사라진다. | 필수 |
| 노트 추가 | `ManagingConversationUsecaseConcrete.note(content:)` | 빈 입력은 무시한다. 입력 언어와 공백 단위 수로 original/translation 및 vocabulary/sentence 초깃값을 정한다. | 필수 |
| 노트 편집 | `NoteDetailScreen.Root.Feature` | original과 translation을 편집한다. 둘 다 빈 값인 상태는 저장하지 않는다. 편집 결과로 카테고리를 다시 계산한다. 실패 시 입력을 유지하고 오류를 표시한다. | 필수 |
| 전체 노트 목록 | `MainScreen.NoteList.Feature`, `Note.isDone` | 미완성 노트를 먼저, 같은 완성 상태에서는 생성 시각 오름차순으로 표시한다. 대화가 삭제되어도 노트는 남는다. | 필수 |
| 노트 삭제 | `ManagingNoteUsecaseConcrete`, `ManagingConversationUsecaseConcrete.deleteNotes` | 대화 상세나 전체 목록에서 노트 자체를 삭제한다. 모든 대화 연결도 함께 제거한다. | 필수 |
| 재생 준비 | `PlayingRecordUsecaseConcrete.prepare(fileName:)` | 상대 경로가 유효하고 파일이 존재하며 디코딩 가능한 경우에만 컨트롤을 활성화한다. 누락·손상은 구분 가능한 오류로 표시한다. | 필수 |
| 재생 제어 | `PlaybackPanel.Feature`, `PlayerServiceConcrete` | 재생, 일시정지, 슬라이더 탐색, 앞·뒤 건너뛰기, 핀 이동, 0.5x~2.0x 배속을 제공한다. | 필수 |
| 재생 중단 | `AudioSessionClient.Event.interruptionBegan`, `PlaybackPanel.Feature` | 오디오 포커스를 잃으면 일시정지한다. 재생 중이었다면 중단 안내를 표시하며 자동 재개하지 않는다. | 필수 |
| 화면 설정 | `DisplayMode`, `SkipInterval`, `SettingScreen.Root.Feature` | 시스템/라이트/다크 모드, 5/10/15/30초 건너뛰기, 화면 자동 잠금 방지를 저장하고 재시작 후 복원한다. | 필수 |
| 앱 정보 | `SettingScreen.InformationSection`, `TeamSection` | 앱 버전, 오픈소스 라이선스, 문의·프로젝트 링크, 튜토리얼 다시 보기를 제공한다. 플랫폼에 맞는 목적지를 사용한다. | 필수 |

## 3. 사용자 관점 세부 계약

### 3.1 녹음 생성

1. 사용자가 녹음을 누르면 `RECORD_AUDIO` 권한을 확인한다.
2. 권한을 확보한 뒤 임시 파일과 녹음 서비스를 준비한다. 준비 완료 전 녹음·저장·핀 입력은 비활성이다.
3. 사용자는 시작, 일시정지, 재개를 반복할 수 있다. 핀은 실제 녹음 중에만 추가할 수 있다.
4. 제목, 주제, 참여자 입력은 녹음 상태와 독립적이다.
5. 완료는 경과 시간이 0ms보다 클 때만 가능하며 확인 대화상자를 거친다.
6. 저장 제목은 앞뒤 공백과 줄바꿈을 제거한다. 결과가 비면 저장 시각의 현지 날짜 표시 문자열을 기본 제목으로 사용한다. `createdAt` 자체는 UTC epoch millisecond로 저장한다.
7. 주제와 참여자 이름은 사용자가 입력한 문자열을 보존한다. Android 1.0은 암묵적 중복 제거를 하지 않는다.
8. 저장 성공 뒤 현재 탭을 다시 읽는다. 저장 실패는 성공처럼 목록을 갱신하지 않는다.

권한이 녹음 화면 진입 사이에 사라진 경우 화면을 닫고 임시 세션을 취소한 뒤 권한 안내를 표시한다. 권한 요청 결과를 기다리는 동안 녹음 버튼을 다시 눌러도 요청을 중복 실행하지 않는다.

### 3.2 대화와 참여자

- 대화 생성일과 오디오 경로는 생성 뒤 바꾸지 않는다.
- 상세 편집은 제목, 주제, 참여자 목록만 바꾼다. 핀 편집은 Android 1.0에 없다.
- iOS `Party.isAvailable`은 해당 ID의 전역 Party가 현재 저장소에 존재하는지를 뜻한다. Android도 `livePartyId != null`로 같은 의미를 표현한다.
- 직접 입력한 참여자는 이름 스냅샷이다. 전역 참여자 선택 UI가 없으므로 Android 1.0에서 새 `PartyEntity`를 자동 생성하지 않는다.
- 녹음 화면에서 추가한 참여자는 iOS처럼 최신 항목을 맨 앞에 넣고, 대화 상세에서 추가한 참여자는 맨 뒤에 붙인다. 저장된 `position`이 이후 표시 순서의 정본이다.
- 참여자 스냅샷 ID와 이름은 대화가 존재하는 동안 유지된다. 전역 Party 이름이 바뀌면 연결된 스냅샷의 표시 이름은 최신 전역 이름을 우선하고, 전역 Party가 삭제되면 저장 당시 이름으로 돌아간다.

### 3.3 노트

노트 생성 규칙은 iOS와 동일하게 다음 순서로 적용한다.

1. Swift의 `components(separatedBy: " ")`와 동등하게 ASCII 공백(`U+0020`)으로 나눈 항목 수가 3개 이상이면 `SENTENCE`, 아니면 `VOCABULARY`다.
2. 한글 완성형 범위(`[가-힣]`)가 하나라도 있고 영문자(`[A-Za-z]`)도 있으면 혼합 입력이다.
3. 한글만 또는 혼합 입력이면 입력을 `translation`에 두고 `original`은 빈 문자열로 둔다.
4. 그 외 입력은 `original`에 두고 `translation`은 빈 문자열로 둔다.
5. 편집 저장 시 original 또는 translation 중 하나라도 위 기준으로 3항목 이상이면 `SENTENCE`, 아니면 `VOCABULARY`로 다시 분류한다.

노트는 독립적으로 조회·편집되는 데이터다. 대화 삭제는 연결만 끊고 노트를 보존한다. 노트 삭제는 노트와 모든 대화 연결을 제거한다. 현재 UI에서는 한 노트를 둘 이상의 대화에 연결하지 않는다.

### 3.4 목록 정렬과 빈 상태

- 대화: `createdAt ASC`.
- 전체 노트: `isComplete ASC`와 같은 효과로 미완성 우선, 각 그룹에서 `createdAt ASC`.
- 대화 상세 노트: 대화 연결의 `position ASC`.
- 데이터가 없으면 대화/노트별 빈 상태를 표시한다.
- 최초 읽기 실패는 재시도 동작을 제공한다. 삭제 실패는 항목을 성공한 것처럼 UI에서 제거하지 않는다.

### 3.5 재생

- 파일 준비가 완료될 때까지 모든 재생 컨트롤을 비활성화한다.
- 탐색 위치는 `[0, duration]`으로 제한한다.
- 건너뛰기는 설정된 5/10/15/30초를 사용하고 범위를 벗어나지 않는다.
- 다음 핀은 `pin > currentPosition`을 만족하는 최솟값이다. 없으면 핀 버튼을 비활성화한다.
- 배속 선택지는 `0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0`이다. 재생 화면 진입 때마다 1.0x에서 시작하며 저장하지 않는다.
- 자연 종료 시 위치를 0ms로 되돌리고 준비 상태는 유지한다. 상세 화면 이탈 시 플레이어를 완전히 해제하고 위치·길이·재생 여부를 초기화한다.
- 오디오 포커스 중단 때 일시정지하고 자동 재개하지 않는다.

### 3.6 설정과 권한

- DataStore 설정은 [로컬 데이터 계약](local-data-contract.md)의 기본값과 허용값을 따른다.
- 마이크 권한은 운영체제가 정본이며 DataStore에 복제하지 않는다.
- 단순 거부이고 시스템 재요청이 허용되면 다음 녹음 시 재요청할 수 있다. 다시 묻지 않음/정책상 요청 불가 상태에는 앱 상세 설정 이동을 제공한다.
- 권한이 없어도 기존 대화·노트 조회와 재생은 가능하다.
- 화면 자동 잠금 방지는 앱이 전경에 보이는 동안에만 적용하며 시스템 전역 설정을 바꾸지 않는다.

## 4. Android 1.0 제외 범위

- iOS/Core Data/SwiftData 데이터 가져오기와 플랫폼 간 마이그레이션
- 클라우드 동기화, 계정, 공유, 내보내기, 백업 복원
- 완료되지 않은 녹음의 앱 재시작 후 복구 또는 이어 녹음
- 알림 액션을 통한 원격 일시정지·완료·취소
- 전역 Party 목록의 생성·검색·선택·이름 변경·삭제 UI
- 저장된 핀 추가·이동·삭제
- 노트의 다른 대화로 이동 또는 여러 대화에 연결하는 UI
- 파형, 음성 인식, 자동 번역, 북마크 메모
- 재생 위치와 배속의 영속화
- 강제 업데이트 화면(현재 iOS도 실제 업데이트 계약 없이 자리만 존재)
- iOS v1 `CasualConversation/`의 기능 및 레거시 저장소 동작

## 5. 핵심 iOS 근거 목록

| 근거 | 타입/동작 |
| --- | --- |
| `Project/App/Domain/Sources/Entity/Conversation.swift` | `Conversation`: 파일명, 핀, 제목, 주제, noteIds, parties |
| `Project/App/Domain/Sources/Entity/Note.swift` | `Note`, `Category`, `isDone` |
| `Project/App/Domain/Sources/Entity/Party.swift` | `Party.id/name/isAvailable` |
| `Project/App/Domain/Sources/Usecase/RecordingUsecaseConcrete.swift` | 준비·완료·취소와 파일 정리 순서 |
| `Project/App/Domain/Sources/Usecase/PlayingRecordUsecaseConcrete.swift` | 파일 누락·준비 실패와 플레이어 정리 |
| `Project/App/Domain/Sources/Usecase/ManagingConversationUsecaseConcrete.swift` | 대화 삭제, 노트 연결, 노트 초깃값 분류 |
| `Project/App/Data/Sources/Repository/ConversationRepositoryConcrete.swift` | Party 스냅샷과 현재 Party 병합 |
| `Project/App/Data/Sources/Repository/RecordFileRepositoryConcrete.swift` | iOS `Record/*.m4a` 이름과 파일명 검증 |
| `Project/App/Presentation/Sources/UserInterface/Sheet/RecordScreen/Root/RecordScreen.Root.Feature.swift` | 녹음 화면 단계·버튼·오류·기본 제목 |
| `Project/App/Presentation/Sources/UserInterface/NavigationStack/RootStack/ConversationDetailScreen/PlaybackPanel/ConversationDetailScreen.PlaybackPanel.Feature.swift` | 재생, 탐색, 배속, 핀, 중단 |
| `Project/App/Presentation/Sources/UserInterface/NavigationStack/RootStack/MainScreen/Root/MainScreen.Root.Feature.swift` | 권한, 온보딩, 목록 갱신 |
| `Project/App/Presentation/Sources/Model/Key.swift` | UserDefaults 키 |
| `Project/App/Presentation/Sources/Model/SkipInterval.swift` | 건너뛰기 허용값 |
| `Project/App/Data/Tests/Repository/PartyReferenceTests.swift` | Party 이름 최신화와 삭제 후 스냅샷 유지 |
| `Project/App/Domain/Tests/Usecase/RecordingUsecaseTests.swift` | 준비·저장 실패와 취소 정리 기대값 |

## 6. 후속 작업 인수 조건

- MAR-15는 Room 스키마, DataStore, 파일 수명주기와 정리 큐를 구현하고 계측 가능한 복구 테스트를 만든다.
- MAR-14는 상태와 오류 의미를 유지하면서 foreground service, MediaRecorder/동등 녹음기, Media3, 오디오 포커스를 구현한다.
- MAR-13은 이 문서의 필수 행을 녹음→저장→목록→상세→재생 흐름으로 연결한다.
- 대화·참여자·노트 전체 UI는 별도 후속 작업에서 구현하더라도 이 스키마와 삭제 의미를 바꾸지 않는다.
