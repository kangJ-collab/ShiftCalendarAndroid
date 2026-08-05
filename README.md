# ShiftCalendarAndroid v1.2.0

`kangJ-collab/shiftcalendar`의 최신 웹 교대달력을 Android 앱으로 실행하고, Android 네이티브 알람과 홈 화면 위젯을 제공하는 프로젝트입니다.

## 최신 웹앱 자동 반영

Android 앱은 실행 시 아래 주소의 최신 웹앱을 불러옵니다.

- `https://kangj-collab.github.io/shiftcalendar/`

따라서 웹 저장소가 업데이트되면 이미 설치된 Android 앱도 다음 실행부터 최신 화면을 사용합니다.

GitHub Actions는 30분마다 원본 저장소 전체를 다시 받아 `app/src/main/assets/www/`에 복사한 후 APK를 빌드합니다. 원본에 새 폴더나 파일이 추가돼도 별도 목록 수정 없이 자동 포함됩니다.

## 포함 기능

- 최신 웹 교대달력 실행
- 인터넷 연결 실패 시 APK 내 오프라인 복사본 사용
- 근무 종류마다 여러 기상 알람 추가·수정·삭제
- 재부팅·시간대 변경·앱 업데이트 후 알람 재예약
- 2×2 오늘 근무 위젯
- 4×2 오늘·내일 근무 위젯
- 4×4 월간 근무 달력 위젯
- GitHub Actions APK 자동 빌드

## GitHub 업로드

ZIP을 푼 뒤 이 폴더 안의 다음 항목을 저장소 최상단에 업로드합니다.

```text
.github/
app/
build.gradle
gradle.properties
settings.gradle
README.md
.gitignore
```

폴더 전체를 한 단계 더 중첩해 올리면 안 됩니다.

## APK 받기

1. 저장소의 `Actions` 탭을 엽니다.
2. `Build Android APK` 작업이 초록색으로 완료될 때까지 기다립니다.
3. 실행 결과 아래 `Artifacts`의 `ShiftCalendarAndroid-v1.2.0`을 다운로드합니다.
4. ZIP을 풀어 `ShiftCalendarAndroid-v1.2.0-debug.apk`를 설치합니다.

## 웹 원본이 업데이트될 때

별도 Android 코드 수정은 필요하지 않습니다.

- 설치된 앱 화면: 다음 실행부터 온라인 최신본 적용
- 새 오프라인 포함 APK: 최대 약 30분 뒤 Actions에서 자동 생성

`repository_dispatch` 이벤트 `shiftcalendar-web-updated`도 지원하므로, 나중에 저장소 간 토큰을 설정하면 웹 커밋 직후 즉시 빌드하도록 확장할 수 있습니다.
