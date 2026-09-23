# JV-01: OpenRouter Jev contract (2026-09-20)

Sources checked (no live credential used in this workspace):

- Decisions endpoint: `POST https://openrouter.ai/api/alpha/decisions`
- Versioned model: `typesafe/jev-1.13`
- Latest alias: `~typesafe/jev-latest` (do not pin thresholds to the alias)
- Chat completions (generative, separate): `POST https://openrouter.ai/api/v1/chat/completions`
- Official reference: [Submit a Decisions request](https://openrouter.ai/docs/api/api-reference/alphadecisions/submit-a-decisions-questions-and-answers-request)
- Model pages: [Jev 1.13](https://openrouter.ai/typesafe/jev-1.13), [Jev latest](https://openrouter.ai/~typesafe/jev-latest)

## Request

`model`, `state` (string/object/array), `questions` map. Question types: `noul`, `choice`, `score`. Optional `provider.allow_fallbacks`.

## Response

`answers[id].noul` is a yes probability in `[0,1]` with **no** confidence field. Choice/Score include `probabilities` and optional `confidence`. A chat-completions payload must not parse as a decision envelope.

## Live-test status

Workbench LIVE_SYNTHETIC failed (run-90af723dc381) with OpenRouter `400` on Decisions because
question set used instructions-only noul / flat choice keys. Official Decisions schema requires
`criteria` (`true`/`false` for noul, option map for choice, ordered array for score). Fixed in
`QUESTION_SET_V1` and workbench `intent-v1.json`. Adapter now includes truncated provider body in
`INVALID_INPUT` errors.

## Adapter

`backend/medtrack_gateway/adapters/openrouter.py` implements the published Decisions URL and keeps generation on chat completions.
