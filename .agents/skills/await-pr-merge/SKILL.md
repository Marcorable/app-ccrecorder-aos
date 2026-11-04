---
name: await-pr-merge
description: Pull Request를 직접 merge하지 않고 코드 리뷰, 상태 검사와 사용자의 merge를 기다려 최종 상태를 확인한다. "PR 지켜봐줘", "리뷰 기다려줘", "머지될 때까지 확인해줘"처럼 열린 PR의 지속 관찰을 요청할 때 사용한다. 리뷰 수정, merge와 브랜치 삭제는 수행하지 않는다.
---

# PR merge 대기

## 대상 확인

PR 번호가 없으면 현재 브랜치에 연결된 PR을 사용한다. URL, head SHA와 base를 기록하고 이름만으로 대상을 추정하지 않는다.

```bash
gh pr view <번호> --json number,url,state,isDraft,headRefName,baseRefName,headRefOid,mergeStateStatus,reviewDecision,statusCheckRollup,mergedAt,mergeCommit
gh pr view <번호> --json reviews,comments
```

CodeRabbit 같은 자동 리뷰가 일반 comment, review 또는 check 중 어디에 결과를 남기는지 저장소의 최근 PR에서 확인한다.

## 대기

- PR을 merge하거나 승인하지 않고 관찰만 한다.
- 열린 동안 draft, check, review와 merge 가능 상태를 구분해 확인한다.
- head SHA가 바뀌면 이전 SHA의 check와 review 결과를 현재 결과로 간주하지 않는다.
- 사용 가능한 wait 또는 monitoring 기능으로 기다리고, 짧은 간격의 busy polling은 하지 않는다.
- 상태가 바뀔 때만 진행 상황을 보고한다. 장시간 대기 중에도 현재 세션이 계속 관찰 중인지 사용자가 알 수 있게 한다.
- 사용자의 확인은 추정하지 않는다. 사용자가 직접 merge한 `MERGED` 상태를 완료 신호로 사용한다.

check 실패, changes requested 또는 충돌은 merge가 아니라 현재 blocker로 보고한다. 사용자가 수정까지 요청하지 않았다면 코드를 변경하지 않는다.

## 종료

- `MERGED`: merge commit, merge 시각과 head branch를 반환한다.
- `CLOSED`: 병합되지 않았음을 보고하고 정리 workflow로 넘기지 않는다.
- 관찰을 지속할 수 없는 환경: 마지막 상태와 다시 확인할 조건을 기록하고 대기 완료로 보고하지 않는다.

이 스킬은 상태를 확인할 뿐 리모트 브랜치나 worktree를 제거하지 않는다.
