# Zayron Mini — Research Foundation

**Project:** Zayron AI  
**Experiment:** Zayron Mini 001  
**Status:** Foundation / planning; no model selected, trained, or deployed yet.

## Mission

Build a small, useful conversational AI capability that can be improved in measured versions. The first target is deliberately narrow: basic question answering, clear explanations, simple instruction following, and short multi-turn conversations.

This is a research track alongside the existing Android app. It does not replace, rename, or restart the Zayron AI application.

## What counts as the first milestone

A one-day prototype milestone means:
1. Select a small model only after checking its exact license, language support, hardware needs, and ability to run in the available free environment.
2. Run a baseline test before training.
3. Prepare a small, reviewed, rights-cleared instruction dataset.
4. Attempt a short LoRA/QLoRA fine-tuning experiment if the available GPU and memory support it.
5. Save the adapter/checkpoint and training configuration.
6. Run the same evaluation prompts against the base model and adapted model.
7. Record whether the experiment improved, regressed, or made no meaningful difference.

A working prototype in one day is a target, not a guarantee. Access to free GPU hardware, model downloads, memory, and runtime quotas can prevent or interrupt training.

## First-version capability boundary

### In scope
- Basic factual and explanatory conversation.
- Simple, clearly stated instructions.
- Short conversation context.
- Honest uncertainty when the answer is unknown.
- Respectful, safe responses and refusal of harmful requests.

### Out of scope for Mini 001
- Claims of expert-level reasoning or universal knowledge.
- Autonomous actions or access to a user's device.
- Medical, legal, financial, or other high-stakes decision-making.
- Image/video generation, document ingestion, voice, or app building.
- Continual self-training from user conversations.

These capabilities may be researched in later versions, with separate tests and explicit privacy safeguards.

## Architecture direction

Keep three layers separate:

1. **Model:** an appropriately licensed pretrained base model plus an optional Zayron adapter.
2. **Zayron intelligence layer:** system instructions, curated knowledge, evaluation, safety checks, and later tools/memory.
3. **Zayron product:** Android app and authenticated backend.

The Android app must call a provider/model-independent backend contract. Model credentials and private inference infrastructure must never be embedded in the Android APK.

## Training principles

- Prefer a small, suitable open-weight model and parameter-efficient fine-tuning (LoRA/QLoRA) for the first experiment.
- Do not describe an adapter fine-tune as a foundation model trained from scratch.
- Verify the exact model-version license before downloading, training, distributing, or commercial use.
- Use only data we own, have permission to use, or whose license permits the intended use.
- Do not use private user conversations for training without explicit, informed consent.
- Keep a baseline and compare it with the tuned model using the same prompts.
- Preserve checkpoints, configuration, dataset version, and evaluation results.
- No paid GPU, subscription, billing, or cloud upgrade without the owner's explicit approval.

## Versioning

- **Mini 001:** narrow conversational prototype and baseline evaluation.
- **Mini 002+:** only add capabilities when a measured evaluation shows the current version is reliable enough.

A larger model is not automatically a better model. Every expansion must earn its place through testing.

## Initial experiment checklist

- [ ] Confirm research branch and preserve the Android app.
- [ ] Compare candidate models and exact licenses.
- [ ] Confirm a free remote GPU is actually available.
- [ ] Define a small, reviewed training dataset.
- [ ] Create a fixed evaluation set before training.
- [ ] Run base-model baseline.
- [ ] Run a bounded LoRA/QLoRA experiment if hardware allows.
- [ ] Compare base vs. tuned outputs.
- [ ] Save artifacts and a reproducible experiment report.
- [ ] Decide whether to continue, revise, or reject the experiment.

## Honest status

At this stage, this repository contains the research foundation only. No model weights have been trained, no inference endpoint has been deployed, and no Android integration has been completed by this research track.
