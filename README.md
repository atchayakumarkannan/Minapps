# Minimal Launcher

A black, text-only Android launcher built for a Redmi Note 12 5G (works on any Android 8+).

## What it does
- **Home:** big clock, date, up to 6 favorite apps, and a few quick notes.
- **Swipe up:** search-first app drawer. Type to filter, press Go to open the top match.
- **Swipe left / right:** opens two apps of your choice (set in Settings).
- **Swipe down:** pulls the notification shade (may not work on every MIUI/HyperOS build; swipe from the top edge then).
- **Quick notes:** tap `+ note` to add, tap a note to mark it done.
- **Focus mode:** tap `focus` on home to toggle. Mark apps as distracting and they turn gray and ask "Open anyway?". Optional automatic schedule.
- **Daily limits:** set minutes per app. Past the limit you get a gentle "Open anyway?" prompt. Needs Usage access.
- **Long-press** an empty spot on home for Settings. **Long-press** an app for its menu (favorite, distracting, limit, info, uninstall).

## Install (no coding needed): build the APK in the cloud
1. Create a free GitHub account and a new empty repository.
2. Upload every file in this folder to it (keep the folder structure, including `.github`).
3. Open the **Actions** tab, choose **Build APK**, tap **Run workflow**.
4. After about 5 minutes, open the finished run and download **minimal-launcher-apk**. Unzip it to get `app-debug.apk`.
5. Copy the APK to your phone, open it, and allow "Install unknown apps" for your file manager when asked.

## Or build with Android Studio
Open this folder in Android Studio, let it sync, then **Build > Build APK(s)**. Or run `gradle assembleDebug` with Gradle 8.7 and JDK 17.

## First-time setup on the phone
1. Press Home and choose **Minimal** > **Always** (or Settings > Apps > Default apps > Home app).
2. Long-press home > **Favorites** to pick your apps.
3. For limits: Settings > **Usage access** > Minimal > allow.

## Xiaomi tips
- Settings > Apps > Manage apps > Minimal > **Autostart: on** and **Battery saver: No restrictions**, so the launcher is not killed in the background.
- If the gesture bar conflicts, use Settings > Home screen > System navigation > buttons or gestures as you prefer.
