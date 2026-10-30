# Architecture

AURA separates UI, profile parsing, routing policy, tunnel lifecycle, and native engines.

- Compose UI observes immutable state exposed by ViewModels.
- TunnelController owns the connection state machine.
- Android VpnService owns the TUN interface and foreground lifecycle.
- Xray and sing-box are isolated behind engine adapters selected by capability.
- Routing policy is compiled into engine configuration and Android per-app exclusions.
- Secrets must be redacted from logs and excluded from analytics (the app has no analytics).

The current foundation commit is only a UI shell. Native engines, TUN forwarding, parsers, and routing are not implemented yet.
