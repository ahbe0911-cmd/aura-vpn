# Third-party notices

## AndroidLibXrayLite

- Project: https://github.com/2dust/AndroidLibXrayLite
- Pinned release: v26.10.29
- Artifact: `libv2ray.aar`
- License: GNU Lesser General Public License v3.0 (LGPL-3.0)
- Upstream license: https://github.com/2dust/AndroidLibXrayLite/blob/main/LICENSE

The AAR is downloaded by the GitHub Actions workflow from the upstream release
and its SHA-256 digest is verified before Gradle consumes it. The application
does not claim that the Xray runtime is operational merely because the AAR is
packaged. Runtime controller integration, TUN descriptor lifecycle, traffic
forwarding, and end-to-end tests are separate required work.

The upstream project includes Xray-core. Review and retain all upstream notices
and corresponding source obligations when redistributing modified or combined
binaries. The upstream LGPL-3.0 license text is available at the URL above.
