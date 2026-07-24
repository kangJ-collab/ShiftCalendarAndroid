# 교대달력 Android

기존 `index.html` 기반 교대달력을 Android WebView 앱으로 패키징하고, Android 네이티브 근무 알람을 연결한 프로젝트입니다.

## 포함된 기능

- 기존 교대달력 HTML/CSS/JavaScript 유지
- 주간·야간·각 OT·일근·교육·출장별 개별 기상 시각 설정
- 근무 종류마다 알람 사용 여부 개별 설정
- 향후 120일 근무표를 계산해 날짜별 정확한 알람 예약
- 조 변경, 일근 기간, 날짜별 근무 변경 시 알람 자동 재예약
- 휴대폰 재부팅·시간대 변경·앱 업데이트 후 알람 재예약
- 잠금화면 전체 알람 화면
- 기본 알람음 반복 재생 및 진동
- 알람 끄기 버튼
- 10초 뒤 테스트 알람
- Android 앱에서 JSON 백업을 `다운로드/교대달력` 폴더에 저장
- JSON 백업 파일 불러오기 지원

## 알람 UX

`설정 → 근무 알람`에서 직접 지정합니다.

예시:

- 주간: 05:30
- 야간: 15:30
- 주OT: 05:20
- 야+반OT: 13:30
- 일근08~17: 06:20

알람은 `근무 시작 몇 분 전`을 계산하지 않습니다. 각 근무일 당일에 사용자가 입력한 시각에 울립니다.

## Android Studio에서 실행

1. Android Studio에서 이 폴더를 엽니다.
2. Gradle 동기화를 진행합니다.
3. Android SDK 37과 Build Tools 36.0.0 설치 요청이 나오면 설치합니다.
4. USB 디버깅을 켠 Android 휴대폰을 연결합니다.
5. 상단의 Run 버튼을 누릅니다.

프로젝트 기준:

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- JDK 17
- compileSdk / targetSdk 37
- minSdk 26

## APK 만들기

테스트 APK:

```text
Build → Build APK(s)
```

직접 배포용 서명 APK:

```text
Build → Generate Signed App Bundle or APK
→ APK
→ Create new keystore
→ release
```

서명키 파일과 비밀번호는 잃어버리면 기존 앱을 업데이트할 수 없으므로 반드시 별도 백업하세요.

## APK 직접 배포

생성된 `app-release.apk`를 GitHub Releases 또는 본인 홈페이지에 올리면 됩니다. 사용자는 최초 설치 시 브라우저의 `알 수 없는 앱 설치 허용`을 승인해야 합니다.

## 권한

앱 최초 설정에서 다음 상태를 확인합니다.

- 알림 권한
- 정확한 알람 권한
- 잠금화면 전체 알람 표시 권한

Android 13 이상에서는 알림 권한을 직접 승인해야 합니다. 잠금화면 전체 표시가 제한돼도 포그라운드 알람 서비스가 알람음과 진동을 실행하도록 구성했습니다.

## 파일 위치

- 웹앱: `app/src/main/assets/www/index.html`
- Android 메인 화면: `MainActivity.java`
- JavaScript 연결: `AndroidAlarmBridge.java`
- 알람 예약: `AlarmScheduler.java`
- 알람 수신: `AlarmReceiver.java`
- 알람음 서비스: `AlarmRingingService.java`
- 잠금화면 알람: `AlarmActivity.java`

## 주의

현재 첨부된 파일에는 GAS 설정 안내용 원본 이미지 3개가 포함되지 않아 자리표시 이미지가 들어 있습니다. 기존 프로젝트의 아래 파일로 교체하면 됩니다.

```text
app/src/main/assets/www/assets/gas-auth-1.png
app/src/main/assets/www/assets/gas-auth-2.png
app/src/main/assets/www/assets/gas-auth-3.png
```
