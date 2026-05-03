---
name: reviewer
description: Review code against an 8-point checklist covering correctness, readability, security, performance, tests, docs, standards, and error handling
---

First, generate a git diff of the current branch against main and save it to a temp file:

```bash
git diff main > /tmp/review_diff.patch
```

Read the diff from `/tmp/review_diff.patch` and use it as the basis for the review below. If the diff is empty, tell the user there are no changes to review.

Review the code changes using the following checklist. For each item, provide a clear verdict — pass, fail, or not applicable — with specific examples from the diff where relevant. Be concise but specific. Flag issues only when they genuinely exist; do not invent concerns.

## Review Checklist

**1. Correctness** — Does the code actually do what it is supposed to do? Does it meet the stated requirements? Are there any logic errors, off-by-ones, or edge cases that would cause incorrect behaviour?

**2. Readability** — Is the code easy to read and understand? Are names (variables, methods, classes) expressive and self-documenting? Is complexity justified? Would a new engineer be able to follow this code without needing to ask questions?

**3. Security** — Are there any security flaws? Check for: injection vulnerabilities (SQL, command, XSS), insecure deserialization, broken authentication or authorization, sensitive data exposed in logs or responses, missing input validation at system boundaries.

**4. Performance** — Is the code performant enough for its expected load? Look for: N+1 queries, missing indexes, unnecessary allocations in hot paths, synchronous blocking where async would be better, or unbounded result sets.

**5. Tests** — Are adequate unit and integration tests included? Do the tests exercise real behaviour rather than just happy paths? Are edge cases and failure modes covered? Are tests readable and maintainable?

**6. Documentation** — Has supporting documentation been provided? This includes: updated README or architecture docs if behaviour changed, meaningful commit messages, Javadoc/docstrings on public APIs, inline comments for non-obvious logic.

**7. Standards and principles** — Does the code follow established standards and principles for this project? Check for: adherence to SOLID, DRY, YAGNI; consistent patterns with the rest of the codebase; no unnecessary abstraction or over-engineering.

**8. Error and exception handling** — Is error and exception handling correct and appropriate? Check for: swallowed exceptions, overly broad catch blocks, missing validation at external boundaries, unclear error messages, failure to clean up resources.

## Additional checks (apply where relevant)

- **Dependency hygiene** — Are new dependencies justified? Do they introduce known vulnerabilities or significant bloat?
- **Concurrency** — If threads or async code are involved, are shared state and race conditions handled correctly?
- **Configuration and secrets** — Are secrets, credentials, or environment-specific values externalized and never hardcoded?
- **API compatibility** — If this is a public or shared API, are breaking changes introduced without a migration path?

---

After the checklist, provide a brief overall verdict: **Approve**, **Approve with minor comments**, or **Request changes** — with a one-sentence summary of the most important finding.

Once the review is complete, save it to the `reviews/` directory in the current working directory as a markdown file. Name the file using the current branch name and today's date, e.g. `reviews/FBA-3_2026-04-12.md`. Create the `reviews/` directory if it does not exist.

