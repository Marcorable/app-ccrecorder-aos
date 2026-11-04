---
name: maintain-pr-series
description: 게시된 stacked Pull Request 시리즈에서 선행 PR의 변경·merge 이후 남은 PR의 base와 커밋 관계를 갱신한다. "stacked PR 정리", "다음 PR로 넘겨줘", "부모 PR 머지 후 스택 갱신" 요청에 사용한다. 새 PR 생성이나 최초 분할 계획에는 사용하지 않는다.
---

# PR 시리즈 유지

## 확인

```bash
git status
gh pr list --state all -L 30 --json number,title,headRefName,baseRefName,state,mergeCommit
git log --graph --oneline --decorate --all
```

1. 저장소 규칙과 PR 본문에서 전체 시리즈 순서와 현재 선행 PR을 확인한다.
2. 선행 PR이 열려 있으면 base를 유지하고, 변경된 선행 head 위에서 후속 PR이 여전히 유효한지 확인한다.
3. 선행 PR이 merge됐으면 실제 merge 방식과 merge commit을 확인해 다음 살아 있는 base를 결정한다.
4. 작업 트리가 깨끗하고 사용자가 갱신할 PR 범위를 확인했는지 점검한다.

PR 제목이나 브랜치 이름만으로 관계를 추정하지 않는다.

## 갱신

1. base 변경만으로 후속 PR diff가 올바르면 PR base만 갱신한다.
2. squash 또는 rebase merge로 후속 브랜치에 선행 커밋이 중복되면 필요한 브랜치 재정렬과 force push 범위를 먼저 제시한다.
3. 승인받은 범위만 rebase하고 `--force-with-lease`로 갱신한다. 다른 사람의 커밋이나 확인하지 않은 브랜치를 재작성하지 않는다.
4. 각 PR을 자신의 새 base와 비교해 변경 파일, 코드량, 빌드와 검증 범위가 의도대로인지 확인한다.
5. PR 본문의 시리즈 순서와 선행·후속 링크를 현재 상태로 갱신한다.

충돌하거나 예상하지 못한 커밋이 나타나면 후속 PR 갱신을 중단하고 현재까지 변경한 base, branch와 PR을 보고한다.

## 완료 조건

- 열린 PR의 head와 base가 남은 의존 순서와 일치한다.
- 각 PR diff에 이미 merge된 선행 변경이 중복되지 않는다.
- 재작성과 force push가 있었다면 대상과 결과를 명시한다.
- 갱신한 PR URL과 다음 merge 순서를 보고한다.
