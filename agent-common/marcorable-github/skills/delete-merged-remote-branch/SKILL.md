---
name: delete-merged-remote-branch
description: 병합된 Pull Request의 head 리모트 브랜치가 다른 열린 PR에 필요하지 않은지 확인한 뒤 안전하게 삭제한다. "머지된 리모트 브랜치 정리", "원격 브랜치 삭제" 요청이나 승인된 PR 생명주기 정리에 사용한다. 로컬 브랜치와 worktree는 삭제하지 않는다.
---

# 병합된 리모트 브랜치 삭제

## 대상 확인

현재 브랜치 이름으로 추정하지 말고 병합된 PR에서 정확한 head 저장소와 브랜치를 얻는다.

```bash
gh pr view <번호> --json number,url,state,mergedAt,headRefName,headRepository,headRepositoryOwner,baseRefName,mergeCommit
gh repo view --json nameWithOwner,defaultBranchRef
gh api 'repos/<head-owner>/<head-repo>/branches/<url-encoded-head-branch>'
git remote -v
git ls-remote --heads <remote> <head-branch>
gh api --paginate 'repos/<owner>/<repo>/pulls?state=open&per_page=100'
```

다음을 모두 확인한다.

- PR 상태가 `MERGED`다.
- head 저장소와 삭제할 remote가 정확히 대응한다. fork나 remote가 불명확하면 중단한다.
- 대상은 저장소의 기본 브랜치가 아니며 보호 규칙이 삭제를 막지 않는다.
- 같은 브랜치를 head로 쓰는 다른 열린 PR이 없다.
- 같은 브랜치를 base로 쓰는 열린 stacked PR이 없다.
- 사용자가 이 브랜치 삭제를 직접 요청했거나 PR 생명주기 workflow에서 대상과 정리 범위를 승인했다.

하나라도 확인할 수 없으면 삭제하지 않고 blocker를 보고한다. stacked PR이 의존하면 먼저 `maintain-pr-series`로 후속 PR을 갱신한다.

## 삭제

리모트 브랜치가 이미 없으면 멱등한 완료로 보고한다. 남아 있으면 확인한 remote와 branch만 삭제한다.

```bash
git push <remote> --delete <head-branch>
```

삭제 뒤 `git ls-remote --heads`로 부재를 확인한다. 로컬 브랜치는 삭제하거나 이름을 바꾸지 않는다.
