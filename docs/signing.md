# Release signing

Signing is not configured in this foundation phase.

Planned GitHub Actions secrets:
- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

Never commit keystores or passwords. When release secrets are absent, CI should publish explicitly labeled debug artifacts, not pretend they are signed release builds.
