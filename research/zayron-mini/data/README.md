# Dataset and privacy rules

Before adding any training example, record its source, owner, license/permission, intended use, language, review status, and any transformations.

Allowed:
- Original examples written for Zayron.
- Public datasets only when their exact license permits the intended training and use.
- Material explicitly licensed or authorized for this purpose.

Not allowed by default:
- Private user conversations, personal messages, account records, or uploaded private documents.
- Scraped content with unclear rights.
- Personal or sensitive data that is not necessary for the training goal.

Every example must be reviewed for correctness, clarity, bias, privacy, safety, and duplication. Keep evaluation examples separate from training examples to reduce test leakage.

Suggested metadata fields:
- example_id
- source
- license_or_permission
- language
- task_category
- reviewer_status
- privacy_review
- safety_review
- dataset_version

Do not upload secrets, API keys, passwords, access tokens, or private user data to a notebook or dataset.
