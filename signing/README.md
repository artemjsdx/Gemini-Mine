# Gemini Mine Development Signing

This key is only the Gemini Mine development/debug signing identity.
It exists so local and CI debug APKs can update each other without uninstalling.
Release signing is separate and must never reuse this development signingConfig.
Do not regenerate the development key casually because doing so breaks update continuity.
The authoritative certificate SHA-256 is development-cert-sha256.txt.
