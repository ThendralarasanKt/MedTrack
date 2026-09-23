# On-device voice capture; cloud-only language models

Status: Proposed voice design; no local LLM in scope  
Related: [LLM architecture](llm-and-chat-architecture.md), [workflow tasks](../task/02-chat-clinical-workflows.md)

Confirmed initial target: **mid-tier Android phones, English speech**. Exact handset models and Android versions remain to be selected for testing. Do not require a flagship-only AI service. No embedded or device-provided LLM will be used.

Proposed test matrix: representative mid-tier phones with 4 GB, 6 GB and 8 GB RAM, multiple manufacturers, and Android versions both with and without the explicit on-device speech API. These are voice evaluation tiers, not a new minimum system requirement. Include English medical terminology and regional English accents.

## 1. Separate transcription from understanding

Speech recognition converts audio to text. A router/extractor interprets that text. Neither step commits clinical changes: the existing validated command and review path does that.

Proposed flow:

```text
Tap microphone -> device transcription -> editable transcript
  -> doctor sends -> backend Jev decisions + cloud extraction
  -> review/clarification -> patient-record command
```

Local transcription does not require a local LLM or an OpenAI/OpenRouter key. Cloud interpretation, if enabled after Send, still sends the approved text to the configured provider. The UI must distinguish “Audio transcribed on device” from “Text processed in cloud.”

## 2. Device transcription: preferred first voice path

Use the explicit on-device Android speech-recognition API when available. Android added `isOnDeviceRecognitionAvailable` and `createOnDeviceSpeechRecognizer` in API 31 (Android 12). Availability of a service does not itself establish support for every requested language. Check support/model readiness where the API permits and handle unsupported-language results. [Android SpeechRecognizer reference](https://developer.android.com/reference/android/speech/SpeechRecognizer)

The current app supports API 26+, so local speech cannot be assumed on every supported phone. Do not raise the minimum Android version merely to add this optional feature. Keep typed input available.

The generic recognizer and a keyboard microphone are not proof of local processing. `EXTRA_PREFER_OFFLINE` is implementation-dependent and does not establish an offline-only guarantee. [RecognizerIntent reference](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_PREFER_OFFLINE)

Voice UX and processing requirements:

- Explicit tap-to-start/stop, visible recording state, microphone permission, cancellation, and lifecycle cleanup.
- Short doctor-dictated notes first. Ambient continuous ward recording and long multi-speaker transcription are separate future features.
- Show partial results as drafts; only the final doctor-reviewed text is submitted. Avoid submitting duplicate partial/final callbacks.
- Preserve edits, numbers, units, medication names, negation, patient names and time expressions. Do not let an LLM silently rewrite uncertain words as clinical facts.
- Benchmark clinical term error rates separately from ordinary transcription word error rates; include accents, background noise and mixed-language phrases.
- Do not assume speaker identity from a single dictation microphone. The authenticated doctor is the recorder; named participants remain attributed from the content or explicit input.
- Do not retain raw audio by default. If retention is later required, specify encrypted storage, consent, purpose and deletion separately. Retain the approved transcript and its capture provenance.
- Handle interruption, denied permission, missing language download, recognizer busy and timeouts without losing the existing draft.
- If local speech is unavailable, offer typing or an explicitly opted-in cloud transcription path. Never silently fall back to remote audio processing.

## 3. Cloud-only interpretation

The previous optional local LLM experiment is superseded by the user's decision. There will be no embedded model weights, device-provided language model requirement, local LLM runtime, or model-download controls.

After the doctor reviews and submits the transcript, the authenticated backend uses [Jev decisions](jev-orchestration.md) to help select a workflow and a cloud generative model to extract fields or reason clinically. Jev does not transcribe audio or generate clinical text.

On-device speech-language downloads may still be necessary; those are speech-recognition resources, not MedTrack LLM downloads.

## 4. Offline behaviour and boundaries

- Available device transcription can populate a local editable draft without network access.
- Manual forms, patient records, task completion, notification notes and bedside reminders remain local deterministic operations.
- Semantic chat automation and clinical reasoning wait for cloud access. Show queued/unsupported states rather than pretending local reasoning occurred.
- A local-only or cloud-disabled setting never uploads transcripts or patient context; it does not promise offline language understanding.
- If local speech is unavailable, typing remains available. Cloud transcription requires explicit opt-in and is not an automatic fallback.
- API 26-30 keep manual/typed support. Their offline speech support must not be assumed from the Android 12 API path.

## 5. Implementation and evidence

1. Select representative mid-tier phones and Android versions for English dictation.
2. Test explicit local recognition, required language resources, medical vocabulary, accents, interruption and microphone permissions.
3. Integrate editable short dictation into chat and the authenticated notification-note sheet.
4. Verify separately that local-only voice mode sends no audio/text remotely, and cloud interpretation sends only approved text through the backend policy.

No universal offline speech availability or transcription accuracy is promised until device tests run. This capability can follow the chat shell independently of clinical reasoning and does not block deterministic reminders. See WF-18 and WF-19; WF-20 now implements Jev, not a local model.
