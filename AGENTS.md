# AGENTS.md

## What this repo is

Hackathon brief for an AI-driven campaign-management UI (MVP). Spec and partial mocks live in `case-readonly/`. Implementation target is `frontend/` + `backend/` as described below. Parallel tasks are `prompt-01-agent.md`, `prompt-02-server.md`, `prompt-03-llm.md`, `prompt-04-frontend.md`. Read the spec before writing code.

- `case-readonly/task.md` — domain model, API surface, lifecycle, AI requirements, evaluation criteria.
- `case-readonly/mock-data.md` — dataset walkthrough, per-campaign examples, consistency checklist.
- `case-readonly/mocks/*.json` — seed data and contracts.
- `case-readonly/Кейсы для хакатона SibКод.docx` — original case file; `.md` files are the usable version.

**Treat everything under `case-readonly/` as read-only input.** Do not create, modify, rename, or delete any file there. Put all implementation, generated mocks, and docs outside `case-readonly/` (repo root or a new sibling directory).

Some paths contain Cyrillic and spaces; quote them in shell commands.

## Architecture

Simple on purpose. The domain is the campaign model in `task.md` (not a separate budget/regions model). Kotlin code decides the next step and what may be saved. The LLM only extracts a JSON patch from the user message and phrases short replies.

- Agent is a finite-state machine. No free-form tool calling.
- Every write (campaign, tag, place, segments, creative, `to-moderation`) waits for an explicit user confirmation.
- Catalog is small (11 topics, 6 tags, 40 segments, 8 places, 2 banner types, 3 channels). Put the whole catalog into the prompt as compact text. No vector DB and no FTS.
- One async mechanism: `POST` a chat message returns `jobId`, the UI polls `GET /api/jobs/{id}`.
- `LlmClient` and `ImageGenClient` are interfaces. Default runtime is `MockLlmClient` plus a solid-color PNG. GigaChat is a drop-in client; prompts stay out of the client.
- Stack: Kotlin, Ktor, Exposed, SQLite, kotlinx.serialization; React + TypeScript + Vite. No Docker. JVM target 17.

Layout:

- `frontend/` — Vite chat. Owns no prompts and does not build banner JSON.
- `backend/` Gradle multi-project.
  - `:ai` — library, no Ktor and no database. Packages: `model`, `agent`, `llm`, `prompt`, `image`.
  - `:server` — Ktor, Exposed, SQLite, repositories, routes, job runner, catalog snapshot, campaign save, CBRO stub. Depends on `:ai`.

`AgentTurn.handle` is a pure function: draft + user text + catalog snapshot + today + light campaign list in, `AssistantReply` + new draft + optional `CommitRequest` out. The server loads the session, calls the turn, and is the only component that writes SQLite. `CommitRequest.action` is `SAVE_CAMPAIGN`, `CREATE_TAG`, `CREATE_PLACE`, or `TO_MODERATION`.

Intents: `CAMPAIGN_EDIT`, `CONFIRM`, `CANCEL`, `GENERATE_CREATIVE`, `LIST_CAMPAIGNS`, `OTHER`. A patch sets only fields the user actually said. Segment codes and place ids come from the catalog. An unknown segment is reported and never invented. An unknown tag or place becomes `pendingProposal` (`TAG` or `PLACE` only). Relative dates («на 2 недели», «до конца года») are computed in code from `today`. Missing dates, times, and segments stay empty until the user answers. `weight=0` and `initialCtr=15` are applied by the server when the draft leaves them null.

Ready to save requires `name`, `startDate`, `startTime`, `topic`, `audienceType`, and at least one place. `SEGMENTED` also needs one `ACTIVE` included segment. `ALL_USERS` requires empty segment lists. Creatives are not required for moderation (warning only). Inactive places and missing tags are warnings and do not block save.

UI blocks (`type` discriminator): `campaign_draft`, `quick_replies`, `entity_proposal`, `creative_preview`, `campaign_list`, `warnings`. Quick replies send plain text (`подтверждаю` / `отмена`). Banner preview is CSS over the text fields; the model does not draw letters.

Chat API:

- `POST /api/sessions`
- `GET /api/sessions/{id}/messages`
- `POST /api/sessions/{id}/messages` → `202 {jobId}`
- `GET /api/jobs/{jobId}` → `PENDING | DONE | FAILED` plus `AssistantReply`

Campaign and dictionary routes follow `task.md` §3 in MVP scope. `POST /api/campaigns/{id}/confirm` and a chat `SAVE_CAMPAIGN` share one save path. Every request carries `X-User` and `X-Role`.

First slice stores dictionaries from `mock-data.md` §1 and banners mapped from `BannerDraft`. Historical campaigns 101–115 are a later regression load. PROMO content trees and live image generation stay behind the existing interfaces and are not on the first path.

File ownership for parallel agents:

- `prompt-01-agent.md` — `model` + `agent` and their tests
- `prompt-02-server.md` — Gradle, `:server`, root `README.md`
- `prompt-03-llm.md` — `llm` + `prompt` + `image` and their tests
- `prompt-04-frontend.md` — `frontend/**` only

## Mock-data trap

Docs call for 18 mock files, but 7 are absent: `topics.json`, `tags.json`, `segments.json`, `segment_types.json`, `places.json`, `validation_rules.json`, `lifecycle_transitions.json`. The spec still lists their contents (topics/tags/segments/places tables in `mock-data.md` §1; error codes in `task.md` §4). Do not claim validation is data-driven unless you create these files; do not invent values beyond the spec.

Also: `mocks/campaigns.json` is storage-level data, **not** the `GET /campaigns/{id}` response shape. Response shape is `CampaignDetailsDto` in `task.md` §3.

## Contract gotchas

- Write bodies (`POST`/`PUT`) wrap dictionary fields as `{value, label}`; `GET` returns raw `string`/`[id]` (`task.md` §2.2).
- Places: `label` is the place `code`, not its description; GET `places` is `[id]` (`task.md` §2.5).
- All `PUT`s fully replace the object/view; partial edits are `GET` → modify → `PUT` (`task.md` §3).
- Segments are flat code lists with OR semantics for `included`; only `state=ACTIVE` segments are selectable (`SEG_DRAFT_WINTER` is `NEW`, count 0).
- `ALL_USERS` forbids non-empty segment lists. Clear segments before switching `audienceType` to `ALL_USERS`, else `SEGMENTS_FORBIDDEN_FOR_ALL_USERS`.
- Channel codes are case-sensitive: `iOS`, `ANDROID`, `WEB`. STORY allows only `iOS`/`ANDROID`.
- Banner minimal valid content: `{"actionType":"default"}`. `link` is required for `externalLink`/`openSmartLink` and forbidden for `closeCampaign`/`doneCampaign`.
- Title/text limits come from `banner_types.json` (STORY 100/500, SMART_BANNER 250/1000).
- PROMO content root is `{role:"promo", component:{role:"linearContainer", axis:"v", components:[...]}}`; only `text`/`image`/`button` roles.
- Missing referenced ids/entities return 404 `ENTITY_NOT_FOUND`, not 400 (`task.md` §3).

## Lifecycle and roles

- `published` is not a state. Only `activate` sets it true, only `stop` sets it false. It can be true regardless of `state`.
- Editing `APPROVED` drops approval to `DRAFT`; editing `ACTIVE`/`ACTIVE_EDITING` moves to `ACTIVE_EDITING`. `ON_MODERATION`/`STOPPED` are not editable.
- `STOPPED` is terminal in MVP; no resume/archive. Delete only from `DRAFT`/`NEEDS_WORK` with `published=false`.
- Every request sends `X-User` and `X-Role` (`campaignManager` | `campaignModerator`). `decline`/`approve` require moderator. Role check runs before state check, so role violations win.
- Goal of the demo is `ON_MODERATION`, not `ACTIVE`; activation requires human moderator approval plus explicit user confirmation.
- CBRO export is a stub that always succeeds; only backend validation errors are in scope.

## Agent behavior requirements

- Human-in-the-loop: preview assembled campaign and get explicit confirmation before every write; never silently fill ambiguous dates/segments.
- Validate banner/content JSON against §2.6–§2.7 before calling the API; convert backend errors/warnings into human-readable fixes.
- Keep draft state across messages; support the fix-after-decline loop (campaign 104 is the reference).
- Warnings never block moderation. `PLACE_NO_CREATIVE` means a place has no creative whose banner type equals `place.defaultBanner`; places created via `POST /places` have no `defaultBanner` and are exempt.
- LLM must sit behind an abstraction with prompts separated from code (target bank model is GigaChat).
- Deliverable should include a README with run instructions and described tool schemas/dialogs (`task.md` §8).

## Reference campaigns

Use these for regression checks: 101 full payload, 103 minimal, 104 fix-after-decline, 102 full lifecycle/audit, 114 stopped, 106/109 active editing with `published=true`.
