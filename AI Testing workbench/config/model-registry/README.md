# Model registry (synthetic evaluation)

Pinned `model_id` values must be currently available free OpenRouter slugs for LIVE_SYNTHETIC.
Verified against `GET /api/v1/models` on 2026-09-22:

| Route | Model |
| --- | --- |
| administrative / clinical extraction | `nvidia/nemotron-3-super-120b-a12b:free` |
| clinical reasoning | `nvidia/nemotron-3-ultra-550b-a55b:free` |
| clarification | `inclusionai/ling-3.0-flash-sante:free` |
| handover summary | `qwen/qwen3.8-27b:free` |
| document extraction | `google/gemma-4-31b-it:free` |

`openai/gpt-oss-120b:free` is no longer offered (paid slug only). Do not use `openrouter/free` for pinned evaluation — it selects randomly.
Experiment configs may list additional approved `:free` fallbacks when the primary returns 404/unavailable.
