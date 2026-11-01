# DORM NFC Manager — KernelSU personal SH manager

This project contains one standalone Android Compose app and one KernelSU module. The app manages a personal library of Shell scripts; the module stores and executes them with Root when explicitly requested.

## Responsibilities

- Android app: create, import, edit, search, save, run, delete and bind scripts to NFC tags.
- KernelSU module: protected script storage, syntax validation, fixed dispatcher, UUID-to-script bindings and bounded logs.
- NFC: Android's native NDEF dispatch; a tag stores only an opaque UUID, never a command or path.
- Campus network: no dedicated feature. Import a personally reviewed `校园网.sh` like any other script.

No KernelSU WebUI, Magisk/LSPosed layer, side-key hooks, boot-time service, polling loop or cloud account is included.