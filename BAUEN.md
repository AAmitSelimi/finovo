# Finovo – APK bauen (Windows)

Stand: 03.11.2026. Das Projekt wurde umgestellt auf Android 16 (API 36), Android-Gradle-Plugin 8.9.1 und Gradle 8.11.1.
Es wurde noch **nie** mit diesen Versionen gebaut. Rechne mit ein paar Fehlern beim ersten Lauf und schick mir die Meldung.

Der APK, den du direkt aufs Handy installieren kannst, ist der **Debug-APK**. Er ist automatisch signiert.
Für den Play Store brauchst du später ein Release-Bundle (siehe PLAYSTORE.md).

## Weg A: Android Studio (am einfachsten)

1. Android Studio installieren: https://developer.android.com/studio
2. „Open“ und diesen Ordner wählen (den, in dem `settings.gradle.kts` liegt).
3. Beim ersten Öffnen lädt Android Studio SDK 36, Gradle und alle Bibliotheken (etwa 1–2 GB). Lizenzen bestätigen.
4. Menü **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Fertig: `app\build\outputs\apk\debug\app-debug.apk`

## Weg B: GitHub baut den APK für dich

1. Auf github.com ein **privates** Repository anlegen.
2. Diesen Ordner hochladen (Inhalt des Ordners `finovo`, inklusive `.github`).
3. Reiter **Actions → Build Finovo APK → Run workflow**.
4. Nach ein paar Minuten unter „Artifacts“ **Finovo-debug-apk** herunterladen (ZIP, darin der APK).

## Weg C: Kommandozeile (JDK 17 und Android SDK 36 installiert)

    gradlew.bat assembleDebug

## APK aufs Handy

1. APK per USB, Cloud oder Mail aufs Handy kopieren.
2. Öffnen und „Installation aus dieser Quelle zulassen“ bestätigen.
3. Der Debug-APK hat die Kennung `com.finovo.app.debug`. Er läuft neben anderen Finovo-Versionen, ihre Daten sind getrennt.
   Alte Daten: in der alten App ein Backup erstellen und in der neuen wiederherstellen.
