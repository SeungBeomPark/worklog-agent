# 업무일지 확인 에이전트 (Java 21 / Spring Boot 3 / Gradle)

평일 오후 5시에 업무일지 작성 여부를 확인하고, 미작성이면 알림을 보냅니다.
매주 금요일 5시 30분에는 그 주의 업무일지를 LLM으로 요약해 전송합니다.
알림은 **텔레그램 / Slack / Discord** 중 설정한 채널로 **동시에** 나갑니다.

## 동작 개요

```
[일일] 평일 17:00  → 영업일 판별 → 그 달 엑셀에서 오늘 행 확인 → 미작성이면 알림
[주간] 금 17:30    → 이번 주(월~금) 항목 조회 → LLM 요약 → 알림 전송
```

## 사전 준비

### 1) 알림 채널 (원하는 것만, 여러 개 동시 가능 — 전부 무료)

**텔레그램**
1. `@BotFather` → `/newbot` → 봇 토큰 발급
2. 봇과 대화 시작 → `https://api.telegram.org/bot<토큰>/getUpdates` 에서 `chat.id` 확인

**Slack**
1. Slack Apps → "Incoming Webhooks" 활성화 → 채널 선택 → Webhook URL 발급

**Discord**
1. 채널 설정(톱니) → 연동 → 웹후크 → "새 웹후크" → URL 복사

### 2) 공휴일 API (공공데이터포털)
1. https://www.data.go.kr/data/15012690/openapi.do 에서 "특일정보" 활용신청
2. 마이페이지에서 서비스키 확인 → **디코딩된 일반 키** 사용 권장
3. `HOLIDAY_API_KEY` 환경변수에 입력 (`HOLIDAY_USE_API=true` 면 API 사용, 실패 시 하드코딩 폴백)

### 3) 환경변수 설정
```bash
export WORKLOG_DIR="C:/worklog"             # 엑셀 파일 폴더

# 알림 채널 (쓸 것만 채우면 됨)
export TELEGRAM_BOT_TOKEN="123456:ABC..."
export TELEGRAM_CHAT_ID="987654321"
export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/..."
export DISCORD_WEBHOOK_URL="https://discord.com/api/webhooks/..."

# 공휴일 API
export HOLIDAY_USE_API=true
export HOLIDAY_API_KEY="발급받은_서비스키"

# LLM 요약 (요약 안 쓰면 생략 가능) — 제공자 선택 가능
export LLM_PROVIDER="anthropic"             # anthropic 또는 openai
export ANTHROPIC_API_KEY="sk-ant-..."
export ANTHROPIC_MODEL="claude-sonnet-4-6"  # 생략 시 기본값
# (OpenAI 를 쓸 경우)
# export OPENAI_API_KEY="sk-..."
# export OPENAI_MODEL="gpt-4o"
```

### 4) 엑셀 파일 규칙
- 월별로 한 파일: `MmList_202607.xls` 또는 `MmList_20260724.xls`
- 양식: 1행 헤더(날짜|업무유형|프로젝트|업무 내용), 2행부터 데이터
- 날짜는 `yyyyMMdd` 형식 (예: `20260724`)
- `MmList_` + `yyyyMM` 으로 시작하는 파일을 그 달 파일로 자동 인식

## 실행

```bash
./gradlew bootRun
```

## 테스트 (5시까지 기다리지 않고 즉시 확인)

앱 실행 후 브라우저에서:
- `http://localhost:8080/test/notify` — 설정된 모든 알림 채널로 테스트 전송
- `http://localhost:8080/test/check` — 오늘 점검 즉시 실행
- `http://localhost:8080/test/check?date=2026-07-25` — 특정 날짜 점검 (미작성 케이스)
- `http://localhost:8080/test/holiday?date=2026-08-15` — 공휴일 판별 확인
- `http://localhost:8080/test/summary` — 이번 주 요약 즉시 실행

## 알림 채널 동작 방식

- 설정값(토큰/URL)이 **채워진 채널만 자동 활성화**됩니다. 비워두면 그 채널은 건너뜁니다.
- `CompositeNotifier` 가 활성 채널 전부에 동시 전송하며, 한 채널이 실패해도 나머지는 계속 전송합니다.
- 새 채널 추가: `Notifier` 인터페이스를 구현하고 `@Component` 만 붙이면 자동 편입됩니다.

## 공휴일 판별 방식

- `HOLIDAY_USE_API=true` → 공공데이터포털 특일정보 API로 그 해 전체 공휴일을 조회(연 1회 호출 후 캐싱).
- API 실패(키 오류/장애) 시 → `HolidayService` 의 하드코딩 목록으로 자동 폴백.
- 회사 자체 휴무일은 하드코딩 목록(`FALLBACK_HOLIDAYS`)에 추가하세요.
- ⚠️ 서비스키는 **디코딩 키** 사용을 권장합니다. 인코딩 키를 쓰면 이중 인코딩으로 인증 오류가 날 수 있습니다.


## IntelliJ 에서 실행 (환경변수 포함)

1. 프로젝트를 IntelliJ 로 Open (Maven 이면 pom.xml, Gradle 이면 build.gradle 자동 인식)
2. Project SDK 가 21 인지 확인: File -> Project Structure -> Project -> SDK = 21
3. 환경변수는 cmd 를 거치지 않고 실행 설정에 바로 넣는 것이 편합니다:
   Run -> Edit Configurations -> 해당 실행 구성 -> Environment variables 에
   ANTHROPIC_API_KEY=...;TELEGRAM_BOT_TOKEN=...;TELEGRAM_CHAT_ID=... 처럼 세미콜론으로 구분해 입력
4. 실행 버튼으로 시작 -> 브라우저에서 http://localhost:8080/test/notify 로 알림 확인

> yaml 은 수정 불필요합니다. 환경변수 치환 문법이 값을 자동으로 읽어옵니다.

## LLM 제공자 선택

주간 요약에 쓸 LLM 을 설정으로 고를 수 있습니다. (Anthropic / OpenAI)

```bash
# Anthropic 을 쓸 경우
LLM_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-sonnet-4-6   # 생략 시 기본값 사용

# OpenAI 를 쓸 경우
LLM_PROVIDER=openai
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o                 # 생략 시 기본값 사용
```

- `LLM_PROVIDER` 로 어느 제공자를 쓸지 고르고, 해당 제공자의 키/모델만 채우면 됩니다.
- 모델명(`*_MODEL`)을 바꾸면 같은 제공자 안에서 다른 모델로 교체됩니다.
- 새 제공자(예: Gemini)를 추가하려면 `LlmProvider` 인터페이스를 구현한 클래스에
  `@Component` 만 붙이고 `providerName()` 을 정해주면 자동으로 편입됩니다.

## 운영 시 주의

- **앱이 24시간 떠 있어야** 스케줄이 동작합니다. 상시 서버에 배포하세요.
  (대안: OS 스케줄러로 5시에 앱 실행)
- **LLM 요약**은 업무 내용을 외부 API로 전송합니다. 민감정보 정책을 확인하세요.
- 운영 배포 시 `TestController` 는 제거하거나 접근을 제한하세요.