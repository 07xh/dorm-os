# DORM NFC Manager V1.0 Preview (KernelSU only)

One dedicated Android app + one KernelSU module. The app uses a native Compose UI: script list, search, editor, SAF import, root execution, NFC tag binding, settings and logs.

The module has no WebUI, no service.sh, no boot-time startup and no NFC polling. Android receives tags, the app passes a validated UUID, and KernelSU dispatches only the user's registered script. No Magisk/LSPosed compatibility is targeted.

Personal scripts are stored outside the module at /data/adb/dormos_nfc_bridge/scripts/<UUID>.sh with owner-only permissions; the original filename is saved as Base64 metadata. Scripts are limited to 16 KiB and syntax-checked before saving. The app asks for explicit confirmation before Root execution or NFC binding. Files are not executed during import.

Existing UUID-based v2 tags still work after binding; old built-in scene handling is retained for migration. There is no special campus-network screen: any privately managed script is a normal SH file. Logs store only a timestamp, script ID and result; script output is displayed in the app.

This is a preview, not Ace 6 Android 16 device-tested. Verify Root approval, NFC app launch permissions, write/re-read and module installation before real use. Imported scripts run with full Root privileges; only use code you have reviewed.
