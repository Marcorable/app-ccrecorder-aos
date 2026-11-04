# CCRecorder Android 1.0 계약

MAR-16은 Android 코드를 구현하지 않고 후속 작업이 따라야 할 제품·저장·오디오 계약을 확정한다.

- [기능 동등 계약](android-1.0-feature-parity.md): iOS v2 근거, Android 1.0 범위, 사용자 관점 동작과 제외 범위
- [로컬 데이터 계약](local-data-contract.md): Room 엔터티·관계·DAO, DataStore, 단위와 마이그레이션 원칙
- [ADR-0001: 녹음·재생 상태와 복구](adr/0001-audio-session-state-and-recovery.md)
- [ADR-0002: 파일·DB 원자성](adr/0002-local-persistence-and-audio-file-lifecycle.md)

## 계약 해석

문서의 표기는 다음과 같다.

- **iOS 근거**: `Marcorable/CCRecorder`의 iOS v2 기준 커밋 `1171d169`에서 직접 확인한 동작이다.
- **Android 결정**: Room, DataStore, 앱 전용 파일 저장과 Android 생명주기에 맞춰 MAR-16에서 확정한 계약이다.
- **제외**: Android 1.0이 구현하지 않는 동작이다.

충돌 시 ADR이 저장·오디오 실패 처리에 우선하고, 기능 동등 문서가 사용자에게 보이는 범위에 우선한다.
