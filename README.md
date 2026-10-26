# ARGENTAS - Invitados

Android project split into two APKs:

- **ARGENTAS - Invitados** (`:client`): simplified guest-facing app with a prominent action button, connection status, and saved tablet address/access code.
- **ARGENTAS - Control** (`:server`): tablet-side dashboard that receives client commands.

## Build

GitHub Actions runs `gradle assembleDebug` and uploads the generated APKs as the artifact `argentas-invitados-debug`. The artifact contains both module APKs.

## Important prototype limitations

The dedicated server currently records requested ON/OFF state but **does not yet toggle the real Android/HyperOS hotspot**. The 10-minute auto-off behavior also still needs implementation in this server module. Do not treat the requested state as proof of the actual hotspot state. These features require implementation and testing on the target Redmi Pad.

The client and tablet must be reachable over the same local network. The current TCP protocol is not encrypted; use only on a trusted network and do not reuse a sensitive password as the access code.

- `client/`: guest app
- `server/`: tablet controller app
- `app/`: legacy combined prototype retained for reference; it is no longer included in the build.
