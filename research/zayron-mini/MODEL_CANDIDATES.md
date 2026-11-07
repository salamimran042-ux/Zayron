# Mini 001 model candidates — initial screening

This is a shortlist, not a final approval to train. Re-check the exact model card, license file, dependencies, and runtime before downloading or using any checkpoint.

## Candidate A — Qwen2.5-0.5B-Instruct

- Model ID: `Qwen/Qwen2.5-0.5B-Instruct`
- Listed license: Apache-2.0.
- Approximate main weight file shown on the model repository: 988 MB.
- Why consider it: small instruction-tuned model; suitable size for a first constrained experiment; model card provides Transformers usage.
- Cautions: 0.5B models have limited reasoning and factual reliability. Evaluate English first; test Nigerian English and any additional languages separately rather than assuming support.

Model card: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct

## Candidate B — SmolLM2-360M-Instruct

- Model ID: `HuggingFaceTB/SmolLM2-360M-Instruct`
- Listed license: Apache-2.0.
- Why consider it: very small instruction-tuned model; useful as a lightweight comparison baseline.
- Cautions: its model card says the family primarily understands and generates English and warns that outputs may be inaccurate or inconsistent.

Model card: https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct

## Selection rule

Do not select based on parameter count alone. Run a baseline on the fixed Zayron Mini benchmark and compare:
- response quality and instruction compliance
- context handling
- factual caution and refusal behavior
- latency and memory use on the available GPU
- language quality relevant to the intended first release
- exact license and redistribution obligations

**Provisional experiment preference:** evaluate Candidate A first, then Candidate B as a comparison if time and GPU availability permit. This is not a claim that Candidate A is best or that training will finish in one day.
