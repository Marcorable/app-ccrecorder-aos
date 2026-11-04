---
name: publish-pr-series
description: 확정된 연속 커밋 경계에 따라 브랜치를 push하고 여러 Pull Request를 독립 또는 stacked 시리즈로 생성한다. 여러 PR 게시를 요청했거나 단일 PR 사전 판단에서 제안한 시리즈를 사용자가 승인했을 때 사용한다. 범위 판단만 필요하거나 단일 PR을 생성할 때는 사용하지 않는다.
---

# PR 시리즈 게시

이 스킬은 범위 판단과 단일 PR 생성 스킬을 조합한다. 제목과 본문 형식을 반복해서 정의하지 않는다.

## 준비

1. `agent-common/marcorable-github/skills/assess-pr-scope/SKILL.md`로 분할 경계와 의존 순서를 확정한다.
2. 작업 트리가 깨끗하고 게시할 변경이 모두 커밋됐는지 확인한다.
3. 각 PR이 연속된 커밋 범위이고 경계 SHA, head, base와 게시 순서가 확정됐는지 확인한다.
4. 사용자가 여러 PR 게시를 직접 요청했거나 제안한 분할안을 승인했는지 확인한다.

기존 커밋 경계만으로 분할할 수 없으면 커밋을 임의로 재작성하지 않고 중단한다. 한 커밋을 한 PR로 강제하거나 떨어진 커밋을 재배열하지 않는다.

## 게시

1. stacked PR은 저장소의 브랜치 규칙에 따라 각 경계 SHA를 가리키는 head 브랜치를 만든다.
2. 독립 PR은 공통 base에서 이미 준비된 head만 사용하고, stacked PR은 직전 head 브랜치를 다음 PR의 base로 사용한다. 선형 히스토리를 독립 PR로 바꾸기 위한 cherry-pick이나 커밋 재작성은 이 스킬에서 수행하지 않는다.
3. 각 PR의 변경 파일 수와 코드량을 자신의 base와 다시 비교해 확정한 계획과 일치하는지 확인한다.
4. 각 head가 자신의 base 위에서 빌드되고 후속 PR이 의존 순서를 유지하도록 브랜치를 push한다.
5. 각 PR은 `agent-common/marcorable-github/skills/pr/SKILL.md`의 제목·본문·검증 기준으로 생성한다. `pr`의 단일 범위 사전 판단은 이미 확정된 각 경계에 한해 충족된 것으로 본다.
6. 본문에 전체 시리즈 순서, 선행·후속 PR, 현재 리뷰 범위와 검증 결과를 기록한다.
7. 하나의 이슈를 다루는 stack이라면 중간 PR은 이슈를 조기에 닫지 않고 마지막 PR만 종료 문구를 사용한다.

일부 push나 PR 생성이 실패하면 후속 생성을 중단하고 이미 생성된 브랜치와 PR을 정확히 보고한다.

## 완료 조건

- 모든 PR의 head와 base가 확정한 의존 순서와 일치한다.
- 자동 리뷰 도구의 제한을 확인했다면 각 PR의 변경 파일 수가 그 안에 있다.
- PR마다 실제 검증 결과와 시리즈 위치가 기록됐다.
- 생성된 PR URL을 순서대로 보고한다.

게시 후 선행 PR의 merge나 변경에 맞춰 남은 stack을 갱신하는 일은 `agent-common/marcorable-github/skills/maintain-pr-series/SKILL.md`로 연결한다.
