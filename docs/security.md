# Security notes

- This project is early-stage and is not yet suitable for protecting production traffic.
- Never log credentials, private keys, subscription URLs, or complete share links.
- Pin and audit native core releases and transitive dependencies.
- Verify geo-rule source, checksums, and review changes before shipping.
- Android VPN permission and always-on settings are controlled by the operating system.
- A successful service start is not proof of a working tunnel; verify core health and packet forwarding.
