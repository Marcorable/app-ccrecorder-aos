# MAR-14 오디오 검증 기록

## 정책 기준

- 앱은 `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`을 선언하고 service를 `microphone` 유형으로 등록한다.
- 녹음 시작은 사용자가 볼 수 있는 화면에서 `RECORD_AUDIO`를 허용한 뒤 `startForegroundService()`로 요청한다. Android의 while-in-use 제한이나 백그라운드 시작 제한에 걸리면 시작 실패로 반환한다.
- service는 즉시 `ServiceCompat.startForeground()`를 호출하고 microphone 유형을 전달한다. 프로세스 종료 뒤 녹음을 추측 복구하지 않도록 `START_NOT_STICKY`를 사용한다.
- Android 13 이상에서 `POST_NOTIFICATIONS` 상태는 별도로 노출한다. 이 권한이 거부되어도 foreground service 시작 자체를 막지는 않는다.
- Media3 ExoPlayer에 speech용 `AudioAttributes`와 자동 오디오 포커스 관리를 설정하고, 포커스 손실 콜백을 받으면 명시적으로 pause하여 자동 재개를 막는다.

참고한 Android 공식 문서:

- [Foreground service types - microphone](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)
- [Launch a foreground service](https://developer.android.com/develop/background-work/services/fgs/launch)
- [Manage audio focus](https://developer.android.com/media/optimize/audio-focus)
- [MediaRecorder API](https://developer.android.com/reference/android/media/MediaRecorder)

## 자동 검증

- `RecordingControllerTest`: 준비/시작/일시정지/재개/종료, monotonic 경과 시간, 잘못된 전이, 오래된 session 명령, 시작·종료 실패 cleanup, 취소 멱등성을 검증한다.
- `PlaybackControllerTest`: 파일 누락 오류, 준비/재생/일시정지, seek clamp, 배속 범위, 자연 종료, 포커스 중단 1회 이벤트와 무자동재개, release 멱등성과 늦은 callback 무시를 검증한다.
- `AppAudioSourceResolverTest`: `filesDir/audio/recordings/` 안의 기존 파일만 허용하고 경로 탈출과 파일 누락을 구분하는지 검증한다.
- 병합 manifest와 APK에는 microphone foreground service 유형 및 네 권한이 포함되어야 한다.

## 에뮬레이터 검증

- `Issue29_API_36` AVD(Android API 36)에 debug APK를 설치하고 `MainActivity`의 cold start 성공을 확인했다.
- 설치된 패키지는 `targetSdk=36`이며 `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`을 요청한다.
- APK manifest에서 `RecordingForegroundService`가 `exported=false`, `foregroundServiceType=microphone(0x80)`으로 병합된 것을 확인했다.
- 제품 녹음 화면은 MAR-13 범위이므로 에뮬레이터에서 permission 요청과 service command 전체 흐름은 실행하지 않았다.
- 에뮬레이터의 가상 마이크는 제조사별 입력·오디오 포커스·잠금 화면 동작을 대표하지 않으므로 실제 녹음 품질 판단에는 사용하지 않는다.

## 실제 기기에서 확인할 항목

다음 항목은 제품 녹음 화면이 MAR-13에서 연결된 뒤 물리 기기에서 검증한다.

- 마이크 허용/거부/다시 묻지 않음과 앱 설정 복귀 흐름
- 실제 마이크로 녹음한 AAC/MPEG-4 파일의 재생 가능성, 길이와 음질
- 홈 이동과 화면 잠금 중 녹음 지속, 알림 표시와 앱 복귀 후 같은 session 재결합
- 전화, 음성 비서, 다른 미디어 앱으로 인한 포커스/오디오 중단 시 pause 및 무자동재개
- 장시간 녹음, 저장 공간 부족, 제조사별 background 제한에서의 stop/cleanup
- 유선·Bluetooth 오디오 경로 변경과 실제 출력 장치에서의 배속/seek 동작
