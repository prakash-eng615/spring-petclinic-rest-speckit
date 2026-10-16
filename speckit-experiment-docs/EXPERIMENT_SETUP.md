# Reproduce the Spring PetClinic REST Spec Kit Experiment Setup

This guide documents how the **local repository used for the recorded GitHub Spec Kit experiment** was prepared. It starts from the official [Spring PetClinic REST repository](https://github.com/spring-petclinic/spring-petclinic-rest), pins an exact historical commit, and creates a **new working branch** so you can follow the experiment without changing the baseline.

This is the **local-only rehearsal/recording setup**. It deliberately does **not** connect a personal GitHub repository or push any experiment work. See the optional publishing note at the end if you want to share your own changes.

## Starting point

| Setting | Value |
| --- | --- |
| Original code | `https://github.com/spring-petclinic/spring-petclinic-rest.git` |
| Pinned commit | `4cd8e1b0cd42578e882247d8801f6be5d402f118` |
| Read-only-by-convention baseline branch | `rehearsal-baseline` |
| New working branch (example) | `speckit-rehearsal-1` |
| Original project's remote name | `upstream` |
| Personal GitHub remote | None |

## Prerequisites

- Git installed.
- A terminal and a parent folder in which to put the clone.
- An unused directory name for this experiment. The provided setup script refuses to reuse an existing directory.
- To build or run PetClinic later, follow the Java/Maven requirements in the upstream project's README.

## Step-by-step Git setup

These commands show what the setup script does, with a portable directory name instead of the recording machine's absolute path.

### 1. Clone the original repository

Open a terminal in a directory where you store projects:

```bash
git clone https://github.com/spring-petclinic/spring-petclinic-rest.git spring-petclinic-rest-rehearsal-1
cd spring-petclinic-rest-rehearsal-1
```

Use a normal clone so the historical baseline commit is available. Start with a fresh, nonexistent target directory; do not reuse an existing experiment clone.

### 2. Rename `origin` to `upstream`

```bash
git remote rename origin upstream
```

This identifies the original project as **upstream** rather than as your own repository. Renaming a remote alone does **not** prevent pushes.

### 3. Disable accidental pushes to the original project

The recording setup script used:

```bash
git remote set-url --push upstream DISABLED
```

`DISABLED` is an intentionally unusable push destination; the HTTPS fetch URL remains intact. As a result, normal `git push upstream ...` operations fail rather than targeting the project's GitHub URL. This protects against accidental pushes, **not** against deliberate Git reconfiguration or explicitly overriding the push URL.

Check the remotes:

```bash
git remote -v
```

Expected shape:

```text
upstream  https://github.com/spring-petclinic/spring-petclinic-rest.git (fetch)
upstream  DISABLED (push)
```

No personal `origin` is added in the recording setup.

### 4. Normalize the cloned default branch name

The script expects the original cloned branch to be either `master` or `main`. If it is `master`, rename the **local** branch:

```bash
git branch --show-current
# Only if the output is master:
git branch -m master main
```

If the cloned default branch is already `main`, leave it alone. This step does not change the upstream repository and does **not** establish the experiment baseline.

### 5. Confirm the pinned commit exists

```bash
git cat-file -e '4cd8e1b0cd42578e882247d8801f6be5d402f118^{commit}'
```

A zero exit status means that commit object exists locally. If this fails, stop; do not silently substitute the current upstream HEAD.

### 6. Create a separate fixed baseline branch

```bash
git checkout -b rehearsal-baseline 4cd8e1b0cd42578e882247d8801f6be5d402f118
```

This is the frozen *starting point* of the experiment. Do not implement features, initialize Spec Kit, or make other experiment changes on `rehearsal-baseline`.

### 7. Create a brand-new working branch from the baseline

```bash
git checkout -b speckit-rehearsal-1 rehearsal-baseline
```

You are now on a **normal named branch**, not a detached HEAD. This is where the Spec Kit workflow, generated artifacts, application changes, and tests belong. To run another independent rehearsal, use a **fresh clone** and a different work-branch name, or create another clean work branch from `rehearsal-baseline`.

### 8. Verify the setup before starting

```bash
git branch --show-current
git rev-parse HEAD
git rev-parse rehearsal-baseline
git remote -v
git status --short --branch
```

Expected checks:

- Current branch: `speckit-rehearsal-1`.
- Both `HEAD` and `rehearsal-baseline` resolve to `4cd8e1b0cd42578e882247d8801f6be5d402f118` **before** experiment changes.
- `upstream` fetch URL points to Spring PetClinic REST; its push URL is `DISABLED`.
- There is **no** personal `origin` remote.
- Working tree is clean.

## Starting the Spec Kit experiment

Open a fresh coding-agent/editor session at the cloned repository directory. Review and test the existing application before changing anything. Then perform the Spec Kit workflow **on the working branch**, not the baseline branch.

The video's example concerns idempotency for the existing visit creation endpoint:

```http
POST /api/owners/{ownerId}/pets/{petId}/visits
```

The intent is to evaluate Spec Kit when enhancing an existing application, rather than creating a new application from scratch.

## Optional: publish your own experiment to GitHub

The recording script intentionally avoids publishing. If you choose to push later, create an **empty repository that you own** and add it as `origin`:

```bash
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git config --local remote.pushDefault origin
# Publish only if you intend to share your work:
git push -u origin speckit-rehearsal-1
```

The original `upstream` push URL remains disabled. Do not push experiment changes to the original Spring PetClinic repository. Preserve required original-project license and attribution notices when redistributing code.

## Notes about the actual recording script

The script used during recording also:

- Accepts optional directory-name and work-branch-name arguments (`spring-petclinic-rest-rehearsal-1` and `speckit-rehearsal-1` by default).
- Uses a recording-machine-specific parent directory; replace it with your own location if reusing the script.
- Checks that Git and the parent directory exist, and stops if the destination directory already exists.
- Stops if the cloned default branch isn't `main` or `master`, or if the pinned commit is absent.
- Checks that `HEAD` is exactly the pinned commit after creating the working branch.
- Prints the remote and branch status and reminds the recorder **not to push** the rehearsal branch.

**Scope:** These steps reproduce the Git starting state, not the completed enhancement or the final video result.
