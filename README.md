# AURA VPN

An open-source Android V2Ray/Xray client focused on reliable tunnel lifecycle management, privacy, and Iran-aware split routing.

> Project status: early development. This repository is being built in phases. No production-ready VPN core or routing guarantee is claimed yet.

## Goals

- Android-only client using Kotlin, Jetpack Compose, and Android `VpnService`.
- Xray and sing-box engine integration behind a capability-aware adapter.
- Import and validate common proxy links and subscription formats.
- Optional Iran-aware domain, IP, DNS, and per-app routing.
- Persian-first RTL interface with locally bundled Vazirmatn fonts.
- Reproducible GitHub Actions builds, tests, checksums, and documented signing.

## Development principles

1. Never report connected until the TUN interface and selected core are healthy.
2. Never log subscription credentials, private keys, or full sensitive share links.
3. Pin native core versions and audit their licenses before distribution.
4. Verify package identifiers from authoritative app listings before adding app-bypass entries.
5. Treat geo-rule updates as reviewed inputs, not trusted executable code.

## Planned documentation

- [Persian README](README.fa.md)
- [Architecture](docs/architecture.md)
- [Design tokens](docs/design-tokens.md)
- [Security notes](docs/security.md)
- [Release signing](docs/signing.md)

## License

The repository's licensing will be finalized after the dependency and native-core license audit. Third-party components retain their respective licenses; see `NOTICE` when populated.
