# 触序 · TapFlow — NFC 场景与 KernelSU 个人 SH 管理

TapFlow（触序）是个人 Android NFC 场景与 Shell 脚本管理工具，可联动 DORM OS。项目包含原生 Android Compose 应用与 KernelSU 模块。 The app manages a personal library of Shell scripts; the module stores and executes them with Root when explicitly requested.

## Responsibilities

- Android app: create, import, edit, search, save, run, delete and bind scripts to NFC tags.
- KernelSU module: protected script storage, syntax validation, fixed dispatcher, UUID-to-script bindings and bounded logs.
- NFC: Android's native NDEF dispatch; a tag stores only an opaque UUID, never a command or path.
- Campus network: no dedicated feature. Import a personally reviewed `校园网.sh` like any other script.

No KernelSU WebUI, Magisk/LSPosed layer, side-key hooks, boot-time service, polling loop or cloud account is included.

## Independent installation (recommended)

The APK and KernelSU ZIP are intentionally separate deliverables. Install the TapFlow APK through Android's normal package installer, then install the matching `dormos-nfc-ksu.zip` from the KernelSU app. Open TapFlow, grant its Root request in KernelSU, and use Refresh to verify that the module is detected.

The module does not install, replace, or silently update the APK. This keeps app updates independent from Root-module updates, avoids package-signature/version conflicts during module installation, and ensures uninstalling or updating the module cannot unexpectedly replace the user-facing app. User scripts and bindings remain in the module's separate data directory.

If Android refuses an in-place APK update because the signing certificate differs, export/backup the app's user data before replacing the old build. Do not uninstall the app just to update the KernelSU module.
