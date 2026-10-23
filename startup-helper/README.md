# Detox Startup

This is a custom helper for opening an existing TV Detox shortcut after a full Android TV restart. It is not an official TV Detox or Key Mapper release. The current build was compiled and reviewed for Android TV 11/API 30, but has not been tested on a TV or emulator.

The helper preserves the installed TV Detox app and layout, Google TV, setup apps and the existing Home-button mapping. It does not disable or delete other apps. Google TV may appear briefly before the saved shortcut opens TV Detox.

## Download and verify

The prepared [version 1.0 APK](../releases/detox-startup-1.0.apk) is 16,811 bytes. Its SHA-256 is:

```
f175b14b02a1cb390221ca372c76c1f9aefba37d95f3c40b6eb5952b73aa40f0
```

The same hash is in [SHA256SUMS](../releases/SHA256SUMS). The package name is `app.tvdetox.startup`; its app label is **Detox Startup**. It targets API 30 and declares minimum API 21, but this version's startup behavior is intended for Android TV 11.

The dedicated release certificate's SHA-256 fingerprint is:

```
dab29d184981de1d82d32c5ef3bc9efb72d986e6ba32b48e7b648132832fd67a
```

## Permission and behavior

The only requested permission is `android.permission.RECEIVE_BOOT_COMPLETED`. It has no Internet, overlay, Accessibility, storage, notification, root or privileged settings permissions, and adds no background service.

- A new installation starts **OFF**, with no saved map ID.
- After explicit configuration and activation, `BOOT_COMPLETED` schedules one non-waking exact alarm about **20 seconds** later. Android 11 does not require the Android 12+ exact-alarm permission. If the device sleeps first, delivery waits rather than waking it.
- The alarm targets a private receiver, which rechecks the saved enabled state and validates the map ID before sending one explicit request to Key Mapper. There are no retries.
- Key Mapper's Accessibility service must already be enabled, bound and responsive. The selected key map must be enabled and its action must open TV Detox. The helper never launches an activity from its boot receiver itself.
- A full restart produces the boot event; waking from ordinary standby does not. The helper does not replace the system's native Home resolver.
- Sending a request does not confirm execution. Startup timing and actual opening require a visual test on the target TV.

## Configure and test

Use an existing, working Key Mapper shortcut that opens TV Detox directly. Key Mapper FOSS **4.6.2** was used when reviewing this API; other versions are not established by this review.

1. Review the source, hash, permission and runtime limitations before installing this additional custom app. Install without replacing an unexpected existing package. Open **Detox Startup** once; automatic startup remains OFF.
2. In Key Mapper, open the working map and choose **Options → Trigger by intent**. Its **Extra value** is the map's UUID. Copy the code block or obtain that UUID without sharing it publicly.
3. Enter the UUID in Detox Startup. **Paste copied map ID** can extract one UUID from copied command text. The helper reads the clipboard only when this button is pressed while its configuration screen is visible.
4. Select **Save ID — leaves startup OFF**. This validates and saves the ID, cancels any pending startup request and leaves automatic startup off.
5. Select **Test saved shortcut once** and visually confirm TV Detox opens. A manual test does not enable startup. A valid UUID is not proof that the map exists or that Key Mapper executed it.
6. Return to the helper and select **Enable automatic startup**. This affects the next full boot; it does not launch the shortcut immediately.
7. Wait at least 30 seconds after the last change before one restart test. Allow boot plus the 20-second startup delay, then confirm TV Detox opens automatically. If it fails, inspect the helper's diagnostic message and Key Mapper status before another change or restart.
8. Finish normal TV, Home-button and app checks, then turn off USB, Network and Wireless debugging or Developer options. ADB does not need to remain enabled for this helper.

## Undo

Select **Disable automatic startup** in Detox Startup. It cancels the pending alarm and saves OFF. Saving another map ID also cancels the pending alarm and leaves startup OFF. The private receiver checks the state again before sending.

**Key Mapper's global Paused setting does not stop this external API trigger.** Disable startup in this helper instead. Disabling the individual Key Mapper map or its Accessibility service also prevents the action, but changes the working Home-button shortcut too.

The helper can be disabled without deleting its data using authorized ADB access. Replace `TV_IP:5555` with the TV's address:

```powershell
.\adb.exe -s TV_IP:5555 shell pm disable-user --user 0 app.tvdetox.startup
```

The corresponding app re-enable command is:

```powershell
.\adb.exe -s TV_IP:5555 shell pm enable --user 0 app.tvdetox.startup
```

Open the helper and inspect its saved startup setting after re-enabling it. Keep Google TV and setup apps enabled throughout.

## API and build

The request uses Key Mapper's explicit receiver:

```
component: io.github.sds100.keymapper/io.github.sds100.keymapper.api.TriggerKeyMapsBroadcastReceiver
action: io.github.sds100.keymapper.ACTION_TRIGGER_KEYMAP_BY_UID
string extra: io.github.sds100.keymapper.EXTRA_KEYMAP_UID
value: the locally configured map UUID
```

Run `sh build.sh` with Java 17, Android SDK platform 34 and build-tools 34.0.0. Set `ANDROID_HOME` to the SDK directory. Build output, hash, APK badging and signature-verification metadata are written to `out/`.

The script creates a dedicated local signing key and random password under ignored `private/`. Retain them privately for same-signer updates; never commit or distribute them. A fresh local key produces a different certificate and APK hash from the prepared download.

Compilation, ShellCheck, APK ZIP alignment and v1/v2/v3 signature verification passed for the prepared APK. These checks do not establish Android 11 boot ordering, Key Mapper initialization timing, remote-control UI behavior or successful automatic startup. No physical-device or emulator runtime test has been performed.
