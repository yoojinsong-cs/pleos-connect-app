# SAI (Space Across Identities)

차량의 시트, 조명, 공조, 사운드를 연동하여, 일상 속 역할 전환에 필요한 이완·휴식·각성을 제공하는 차량 맞춤형 리추얼 앱입니다.

## 실행 방법

### 방법 1 — APK 직접 설치
1. 아래 GitHub Release 페이지에서 APK 파일을 다운로드합니다.
2. Pleos Connect 에뮬레이터에 APK를 설치합니다.
3. 앱을 실행하면 홈 화면에서 릴렉스·냅핑·각성 모드를 선택할 수 있습니다.
4. 모든 차량 제어 기능은 정차(기어 P) 상태에서만 동작합니다.

### 방법 2 — Android Studio에서 직접 빌드
1. 저장소를 클론합니다: git clone https://github.com/yoojinsong-cs/pleos-connect-app.git
2. Android Studio에서 프로젝트를 엽니다.
3. Pleos Connect 에뮬레이터를 실행합니다.
4. Android Studio에서 Run 버튼을 눌러 앱을 실행합니다.

## 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|------|------|
| 개발 언어 | Kotlin (Android 네이티브) |
| compileSdk | 37 |
| minSdk | 28 |
| targetSdk | 35 |
| ai.pleos.playground:Vehicle | 2.3.2 |
| ai.pleos.playground:TextToSpeech | 3.3.1 |
| com.airbnb.android:lottie | 6.6.0 |

## Pleos Connect 에뮬레이터

| 항목 | 내용 |
|------|------|
| Pleos Vehicle SDK | 2.3.2 |
| Pleos TextToSpeech SDK | 3.3.1 |
| 화면 규격 | 14.6인치 / ㅁㄴㄻㄴㅇㄻㄴㅇㄻㄴㅇㄻㄴㅇㄻㄴㅇㄻㄴㅇㄻㄴㅇㄻㄴㅇㄻㅇㄻㄴㅇㄻㄴㅇㄻㄴㅇㄹ 이거 에뮬레이터에서 확인해줘야함 |

## APK 다운로드

[GitHub Release 페이지에서 다운로드](https://github.com/yoojinsong-cs/pleos-connect-app/releases/tag/v1.0.0)
