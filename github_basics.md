# GitHub basics — create repo and push (CLI)

Commands used to initialize this project and push it to GitHub as **Neuro1729**, over **SSH**, with **`gh`**.

## Prerequisites

- Git installed
- `gh` logged in (`gh auth status`)
- SSH set as Git protocol (`gh` → Git operations protocol: ssh)

## 1. Init local git

```powershell
cd C:\Users\aarya\Java_Project\resource-entitlement-engine
git init -b main
git add -A
git status
```

## 2. First commit

```powershell
git commit -m @"
chore: initial commit of resource entitlement engine

"@
```

## 3. Create GitHub repo and push

Creates a **private** repo, sets `origin` to SSH, and pushes `main`:

```powershell
gh repo create resource-entitlement-engine --private --source=. --remote=origin --push --description "Generic in-memory hierarchical resource entitlement engine"
```

## 4. Verify

```powershell
git status
git remote -v
gh repo view --json url,nameWithOwner,visibility
```

Expected:

| Item        | Value |
|-------------|--------|
| Remote      | `git@github.com:Neuro1729/resource-entitlement-engine.git` |
| Branch      | `main` tracking `origin/main` |
| Visibility  | `PRIVATE` |
| URL         | https://github.com/Neuro1729/resource-entitlement-engine |

## Later: push more changes

```powershell
git add -A
git commit -m @"
feat: describe your change

"@
git push
```

## Optional: make the repo public

```powershell
gh repo edit Neuro1729/resource-entitlement-engine --visibility public --accept-visibility-change-consequences
```
