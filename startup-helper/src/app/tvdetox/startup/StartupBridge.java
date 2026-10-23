package app.tvdetox.startup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One boot alarm and one explicit Key Mapper request. No service or activity launching. */
final class StartupBridge {
    static final String ALARM_ACTION = "app.tvdetox.startup.RUN_SAVED_MAP";
    static final String KEY_MAPPER_PACKAGE = "io.github.sds100.keymapper";
    static final String KEY_MAPPER_RECEIVER = "io.github.sds100.keymapper.api.TriggerKeyMapsBroadcastReceiver";
    static final String KEY_MAPPER_ACTION = "io.github.sds100.keymapper.ACTION_TRIGGER_KEYMAP_BY_UID";
    static final String KEY_MAPPER_EXTRA = "io.github.sds100.keymapper.EXTRA_KEYMAP_UID";
    static final long DELAY_MILLIS = 20_000L;
    private static final String PREFS = "startup";
    private static final String ENABLED = "enabled";
    private static final String MAP_UID = "map_uid";
    private static final String LAST_STATUS = "last_status";
    private static final String LAST_TIME = "last_time";
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern CLIP_UUID = Pattern.compile(
            "(?<![0-9a-fA-F])" + UUID.pattern() + "(?![0-9a-fA-F])");

    private StartupBridge() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean(ENABLED, false);
    }

    static String uid(Context context) {
        return prefs(context).getString(MAP_UID, "");
    }

    static String canonicalUid(String text) {
        String value = text == null ? "" : text.trim();
        if (!UUID.matcher(value).matches()) {
            throw new IllegalArgumentException("Enter the complete Key Mapper map ID (a UUID).");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    static String uidFromClipboard(String text) {
        Matcher match = CLIP_UUID.matcher(text == null ? "" : text);
        if (!match.find()) throw new IllegalArgumentException("No map ID found in the copied text.");
        String found = match.group();
        if (match.find()) throw new IllegalArgumentException("Copied text contains more than one map ID.");
        return canonicalUid(found);
    }

    /** Saving always leaves automatic startup off, even if it was previously on. */
    static void saveUid(Context context, String rawUid) {
        String value = canonicalUid(rawUid);
        cancel(context);
        requireSaved(prefs(context).edit().putBoolean(ENABLED, false).putString(MAP_UID, value).commit());
    }

    static void enable(Context context) throws PackageManager.NameNotFoundException {
        canonicalUid(uid(context));
        checkReceiver(context);
        // Enabling does not launch anything now or schedule an alarm in this boot.
        requireSaved(prefs(context).edit().putBoolean(ENABLED, true).commit());
    }

    static void disable(Context context) {
        cancel(context);
        requireSaved(prefs(context).edit().putBoolean(ENABLED, false).commit());
    }

    private static void requireSaved(boolean saved) {
        if (!saved) throw new IllegalStateException("Could not save. Turn this helper off in TV app settings.");
    }

    private static PendingIntent alarmIntent(Context context, int mode) {
        Intent intent = new Intent(context, StartupReceiver.class).setAction(ALARM_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, mode | PendingIntent.FLAG_IMMUTABLE);
    }

    static void cancel(Context context) {
        PendingIntent existing = alarmIntent(context, PendingIntent.FLAG_NO_CREATE);
        if (existing != null) {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) alarms.cancel(existing);
            existing.cancel();
        }
    }

    static void scheduleAfterBoot(Context context) {
        cancel(context);
        if (!enabled(context)) return;
        try {
            canonicalUid(uid(context));
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) throw new IllegalStateException("Alarm service unavailable.");
            PendingIntent alarm = alarmIntent(context, PendingIntent.FLAG_UPDATE_CURRENT);
            alarms.setExact(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + DELAY_MILLIS, alarm);
            record(context, "One startup request scheduled, about 20 seconds after boot.");
        } catch (RuntimeException error) {
            record(context, "Startup was not scheduled: " + message(error));
        }
    }

    static void runScheduled(Context context) {
        // A queued alarm cannot run a map after startup has been switched off.
        if (!enabled(context)) return;
        try {
            send(context);
        } catch (Exception error) {
            record(context, "Startup request failed: " + message(error));
        }
    }

    /** User-initiated test is allowed while automatic startup is off. */
    static void test(Context context) throws PackageManager.NameNotFoundException {
        send(context);
    }

    private static void send(Context context) throws PackageManager.NameNotFoundException {
        String value = canonicalUid(uid(context));
        checkReceiver(context);
        Intent intent = new Intent(KEY_MAPPER_ACTION)
                .setComponent(new ComponentName(KEY_MAPPER_PACKAGE, KEY_MAPPER_RECEIVER))
                .putExtra(KEY_MAPPER_EXTRA, value);
        context.sendBroadcast(intent);
        // Sending a broadcast is not confirmation that Key Mapper executed it.
        record(context, "Request sent once. Check that TV Detox opened.");
    }

    private static void checkReceiver(Context context) throws PackageManager.NameNotFoundException {
        ActivityInfo receiver = context.getPackageManager().getReceiverInfo(
                new ComponentName(KEY_MAPPER_PACKAGE, KEY_MAPPER_RECEIVER), 0);
        if (!receiver.enabled || !receiver.applicationInfo.enabled) {
            throw new IllegalStateException("Key Mapper is disabled. Enable it before using this helper.");
        }
    }

    private static void record(Context context, String status) {
        // Diagnostic-only metadata. It never enables startup or changes the map ID.
        prefs(context).edit().putString(LAST_STATUS, status)
                .putLong(LAST_TIME, System.currentTimeMillis()).apply();
    }

    static String lastStatus(Context context) {
        String status = prefs(context).getString(LAST_STATUS, "No request sent yet.");
        long time = prefs(context).getLong(LAST_TIME, 0L);
        if (time == 0L) return status;
        return java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(time)) + "\n" + status;
    }

    static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
