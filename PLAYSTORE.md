# Finovo – Play-Store-Checkliste

Stand: 03.11.2026. Alles mit ⚠️ musst du selbst entscheiden oder in der Play Console ausfüllen.

## 1. Blocker vor dem ersten Upload

| # | Thema | Stand |
|---|---|---|
| 1 | **Target API 36** (Android 16). Google nimmt seit dem 31.08.2026 (Verlängerung bis 01.11.2026) nur noch Apps mit Ziel API 36 an. | `compileSdk`/`targetSdk` = 36, AGP 8.9.1, Gradle 8.11.1 eingetragen. ⚠️ **Noch nicht gebaut** – bitte `./gradlew testDebugUnitTest bundleRelease` laufen lassen und Fehler prüfen. |
| 2 | **App Bundle statt APK.** Play verlangt `.aab`. | `./gradlew bundleRelease` |
| 3 | **Eigener Upload-Schlüssel.** Der bisher mitgelieferte Schlüssel `mykosten-release.jks` lag in einem weitergegebenen Archiv. | ⚠️ Neuen Schlüssel erzeugen, offline sichern, **Play App Signing** aktivieren. Alten Schlüssel nicht mehr verwenden. |
| 4 | **Paketname.** Umbenannt von `com.mykosten.app` auf `com.finovo.app` (ändert sich nach dem Upload nie wieder). | ✅ Umgesetzt. ⚠️ Eine bereits installierte alte Version wird nicht aktualisiert, sondern parallel installiert: vorher in der alten App ein Backup erstellen und in der neuen wiederherstellen. |
| 5 | **Datenschutzerklärung als öffentliche URL.** Pflicht, weil die App Finanzdaten verarbeitet. | Entwurf unten. ⚠️ Auf einer eigenen Webseite veröffentlichen. |
| 6 | **Neues privates Entwicklerkonto?** Dann verlangt Google einen Closed Test (nach meinem Stand mindestens 12 Tester über 14 Tage) vor der Produktion. | ⚠️ In der Play Console prüfen. |

Signieren und bauen:

```bash
keytool -genkeypair -v -keystore finovo-upload.jks -alias finovo -keyalg RSA -keysize 4096 -validity 10000
export FINOVO_KEYSTORE=$PWD/finovo-upload.jks
export FINOVO_KEYSTORE_PASSWORD=...   # nicht in Dateien oder Chats ablegen
export FINOVO_KEY_ALIAS=finovo
export FINOVO_KEY_PASSWORD=...
./gradlew bundleRelease               # app/build/outputs/bundle/release/app-release.aab
```

## 2. Was im Code geändert wurde (Sicherheit und Play-Regeln)

- `allowBackup="false"` und `usesCleartextTraffic="false"`: kein Google-Cloud-Backup der Finanzdaten, kein unverschlüsselter Netzverkehr.
- `ACCESS_NETWORK_STATE` bleibt erhalten (vorher entfernt, obwohl der Code sie braucht).
- `USE_EXACT_ALARM` entfernt (bei Google Play nur für Wecker- und Kalender-Apps erlaubt), `USE_FINGERPRINT` entfernt (veraltet).
- PIN: Sperre nach 5 Fehlversuchen (30 s, verdoppelt sich bis 15 min).
- Backups enthalten keinen PIN-Hash mehr; eine Wiederherstellung kann die App-Sperre weder ändern noch entfernen. Vor „Ersetzen“ wird automatisch eine lokale Sicherung angelegt.
- Backend-Adresse muss `https://` sein.
- Backend: zeitkonstanter Token-Vergleich, Rate-Limit, Eingabeprüfung, Redirect nur auf `finovo://bank/callback`, verschlüsselter Speicher (AES-256-GCM, wenn `FINOVO_ENC_KEY` gesetzt ist), atomares Schreiben, Container läuft nicht mehr als root.

## 3. Noch offen (bewusst nicht ungeprüft geändert)

- **Release-Optimierung** (`isMinifyEnabled = true`, `shrinkResources`): erst mit einem echten Release-Test aktivieren, sonst können Room/ML Kit brechen.
- **Backup-Datei verschlüsseln** (Passwort): Exporte liegen weiterhin als lesbares JSON vor. Nutzer sollten sie nicht in unsichere Ablagen kopieren.
- **Backend mit mehreren Nutzern:** aktuell gibt es ein gemeinsames Token. Für ein öffentliches Angebot braucht jeder Nutzer eigene Zugangsdaten und eigene Verbindungen. Für den Eigenbetrieb reicht der jetzige Stand.
- **Android 16:** Randlos-Darstellung ist Pflicht und die Hochformat-Sperre wird auf Tablets und Faltgeräten (ab 600 dp) ignoriert. ⚠️ Auf einem Tablet-Emulator ansehen.
- **Bildschirmlayout** laut `Finovo_Design_Vorschau.html` / neuem Design ist noch nicht in Compose umgesetzt.

## 4. Play Console – Angaben

**Datensicherheit (Vorschlag, bitte gegen den echten Funktionsumfang prüfen)**

- Daten werden **nicht** erhoben oder geteilt, solange die Bankanbindung nicht benutzt wird; alles bleibt lokal.
- Mit aktivierter Bankanbindung gehen Kontodaten (Name, IBAN, Salden, Umsätze) an das **Backend des Nutzers** und den Open-Banking-Anbieter (GoCardless). Das als *optional*, *Finanzinfos*, *Verarbeitung zum Betrieb der App-Funktion*, *verschlüsselt bei der Übertragung* angeben.
- Keine Werbung, keine Analyse, kein Absturzbericht an Dritte.
- Löschung: Nutzer können alle Daten in der App löschen und die Bankverbindung trennen.

**Weitere Formulare:** Zielgruppe (ab 18, keine Kinder), Inhaltseinstufung, Anzeigen = nein, Erklärung zu **Finanzfunktionen** (persönliche Finanzverwaltung, Konto-Verknüpfung), Erklärung zu exakten Alarmen (Erinnerungen an Rechnungsfälligkeit; ohne Berechtigung greift ein ungefährer Alarm).

**Grafiken:** `branding/finovo_playstore_512.png` (Icon), `branding/finovo_feature_graphic_1024x500.png`; Screenshots (mind. 2, Telefon) fehlen noch.

## 5. Datenschutzerklärung – Entwurf (Deutsch)

> Platzhalter in [eckigen Klammern] ersetzen. Das ist kein Rechtsrat; bitte vor der Veröffentlichung prüfen lassen.

**Verantwortlicher:** [Name / AZ Studio], [Anschrift], [E-Mail]

**Grundsatz.** Finovo speichert Ausgaben, Einnahmen, Rechnungen und Einstellungen ausschließlich lokal auf deinem Gerät. Es gibt kein Benutzerkonto, keine Werbung und kein Tracking.

**Belegscanner.** Die Texterkennung läuft auf dem Gerät (Google ML Kit, Modell wird beim Installieren über die Play-Dienste geladen). Fotos von Belegen werden nicht an uns übertragen.

**Optionale Bankanbindung.** Wenn du die Bankanbindung einrichtest, kommuniziert die App nur mit dem von dir eingetragenen Backend. Dieses ruft über den Open-Banking-Anbieter GoCardless (Bank Account Data) Kontoname, IBAN, Kontostand und Umsätze ab. Deine Bank-Zugangsdaten sehen weder die App noch das Backend; die Anmeldung erfolgt direkt bei deiner Bank. Der Zugriff gilt maximal 90 Tage und kann jederzeit in der App getrennt werden. Rechtsgrundlage ist deine Einwilligung (Art. 6 Abs. 1 lit. a DSGVO).

**App-Sperre.** Die PIN wird nur als gesalzener Hash gespeichert. Biometrische Daten verlassen das Betriebssystem nicht.

**Datensicherung.** Exporte und Sicherungsdateien legst du selbst ab; wir haben darauf keinen Zugriff. Die automatische Android-Cloud-Sicherung ist für die App deaktiviert.

**Speicherdauer und Löschung.** Daten bleiben, bis du sie in der App löschst oder die App deinstallierst. Beim Trennen der Bankverbindung werden die Verbindung und die Zugangsdaten im Backend gelöscht.

**Deine Rechte.** Auskunft, Berichtigung, Löschung, Einschränkung, Datenübertragbarkeit, Widerruf der Einwilligung und Beschwerde bei einer Aufsichtsbehörde. Kontakt: [E-Mail].

## 6. Store-Text (Vorschlag)

**Titel:** Finovo – Haushaltsbuch & Budget

**Kurzbeschreibung (80 Zeichen):** Deine Finanzen. Einfach. Privat. Ohne Konto, ohne Cloud.

**Beschreibung:**
Finovo ist dein privates Haushaltsbuch. Erfasse Ausgaben und Einnahmen, behalte Rechnungen und Fälligkeiten im Blick und plane dein Budget – alles bleibt auf deinem Gerät.

• Übersicht: Was ist diesen Monat noch frei?
• Budgets pro Kategorie mit Warnungen
• Rechnungen, Abos und wiederkehrende Zahlungen mit Erinnerungen
• Belegscanner mit Texterkennung auf dem Gerät
• Analyse: Monatsvergleich, Sparquote, größte Kategorien
• App-Sperre mit PIN oder Fingerabdruck
• Sicherung, Export (CSV/PDF) und Import
• Hell- und Dunkelmodus, Deutsch, Englisch, Italienisch, Türkisch, Albanisch
• Optional: Bankkonto über Open Banking verbinden (eigenes Backend nötig)

Keine Werbung. Kein Tracking. Kein Konto.
