# CCRecorder Android

CCRecorder Android는 다음 기술로 구성한 멀티모듈 프로젝트다.

- 언어: Kotlin
- UI: Jetpack Compose, Material 3
- 의존성 주입: Hilt
- 화면 탐색: Navigation Compose
- 빌드 구성: Kotlin DSL, Gradle Version Catalog
- SDK: `compileSdk 36`, `targetSdk 36`, `minSdk 26`

API 36 지원 범위를 유지하기 위해 다음 버전을 고정한다.

- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- Kotlin: 2.3.21
- Compose BOM: 2026.02.00
- Navigation Compose: 2.9.7
- Hilt: 2.58
  - AGP 8을 지원하면서 Kotlin 2.3 메타데이터를 처리하는 마지막 안정 버전이다.

## 빌드

JDK 17과 Android SDK 36을 준비한 뒤 저장소 루트에서 실행한다.

```sh
./gradlew test
./gradlew :app:assembleDebug
```

## 모듈 구조

```text
app
└── feature:{recorder,recordings,conversation,settings}
    ├── core:designsystem
    ├── core:model
    └── core:data
        ├── core:database
        ├── core:datastore
        ├── core:media
        ├── core:model
        └── core:common
```

- `app`: Hilt 애플리케이션과 Navigation Compose 진입점
- `feature:*`: 기능별 Compose 화면 골격
- `core:designsystem`: 공통 Material 3 테마
- `core:data`: Repository 계약과 데이터 계층 조합 지점
- `core:database`, `core:datastore`, `core:media`: 저장소·설정·오디오 기술 경계
- `core:model`, `core:common`: 플랫폼 독립 모델과 공통 타입

상위 모듈만 하위 모듈을 참조하며, 기능 모듈끼리는 직접 의존하지 않는다.
