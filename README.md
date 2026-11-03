# Finovo

Finovo ist eine **private, offline funktionierende Finanz-App für eine Person**.
Keine Registrierung, kein Konto, keine Cloud, keine Werbung, kein Tracking.
Alle Daten liegen in einer lokalen SQLite-Datenbank auf dem Gerät.

> Von Herzen – AZ Studio

---

## Inhalt

- [Funktionsumfang](#funktionsumfang)
- [Technik](#technik)
- [Voraussetzungen](#voraussetzungen)
- [Einrichtung](#einrichtung)
- [Bauen](#bauen)
- [Testen](#testen)
- [Release & Signierung](#release--signierung)
- [Datenbank & Migrationen](#datenbank--migrationen)
- [Sicherung & Wiederherstellung](#sicherung--wiederherstellung)
- [Sprachen](#sprachen)
- [Projektstruktur](#projektstruktur)
- [Umgebungsvariablen](#umgebungsvariablen)
- [Datenschutz & Offline](#datenschutz--offline)

---

## Funktionsumfang

**Basis**
- Ausgaben, Einnahmen, Rechnungen mit Fälligkeit und Bezahlstatus
- Wiederkehrende Zahlungen (täglich, wöchentlich, monatlich, jährlich, benutzerdefiniert)
- Kategorien mit **Unterkategorien**, Fixkosten-Kennzeichnung, Bereiche (z. B. Persönlich, Auto)
- Budgets (gesamt und pro Kategorie) mit Warnstufen
- Sparziele, Abos, Auto-Kosten
- Dashboard mit frei an- und umsortierbaren Widgets
- Finanzanalyse mit Diagrammen, Monats-/Jahresvergleich
- Kalender, Suche, Was-wäre-wenn-Rechnung

**Erweiterte Module**
- Intelligente Kategorie-Erkennung, die aus Korrekturen lernt
- Automatische Erkennung wiederkehrender Buchungen
- Monatsend-Prognose, Cashflow-Analyse, Nettovermögen (Vermögen − Schulden)
- Finanz-Alarme (9 einzeln ein-/ausschaltbar, werden als echte Benachrichtigungen gemeldet)
- Beleg- und Rechnungsscanner mit OCR, **Pflicht-Prüfbildschirm** vor dem Speichern
- Dokumenten-Center mit Suche, Filtern, Tags, Vorschau, Export
- Jahresbericht als **PDF** (offline erzeugt) und CSV
- CSV-/Excel-Import mit Spaltenzuordnung und Vorschau
- Finovo AI (regelbasiert, offline, liest ausschließlich echte Daten) mit Bestätigungsschritt vor jeder Änderung
- Duplikat-Erkennung (löscht **nie** selbstständig)
- Änderungsverlauf, Datenprüfung, Fehlerprotokoll, App-Sperre (PIN/Biometrie)
- Multi-Währung (EUR, USD, GBP, CHF, ALL), Haushaltsmodus, Mehrsprachigkeit

---

## Technik

| Bereich | Technologie |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architektur | Single-Activity, `AppViewModel` + Repository |
| Datenbank | Room über SQLite (Datei `finovo.db`) |
| Nebenläufigkeit | Coroutines & Flow |
| Texterkennung | ML Kit Text Recognition (on-device) |
| PDF | Android `PdfDocument` (kein Server) |
| Build | Gradle 8.9 (Kotlin DSL), KSP für Room |
| Tests | JUnit 4, Robolectric, Compose-UI-Tests |

**Frontend und Backend sind kombiniert:** Finovo ist eine einzige native Android-App.
Es gibt **keinen separaten Server** und kein Backend-Repository. Die Bank- und
KI-Schnittstellen im Code sind vorhanden, aber **abgeschaltet**
(`bank/BankFeature.kt` → `LIVE = false`); würde man sie aktivieren, sprechen sie
ausschließlich ein eigenes Backend über HTTPS an – nie direkt eine Bank und nie
mit Anbieter-Geheimnissen in der App.

---

## Voraussetzungen

| Werkzeug | Version |
|---|---|
| JDK | **21** (z. B. Temurin 21) |
| Android SDK | **compileSdk 35**, Build-Tools 35.x, Plattform 35 |
| Gradle | 8.9 – wird automatisch über den Wrapper geladen |
| Android-Gerät/Emulator | Android **8.0 (API 26)** oder höher |

`minSdk = 26`, `targetSdk = 35`, `versionCode = 1`, `versionName = "1.0"`.

---

## Einrichtung

```bash
git clone <DEIN-REPO> finovo
cd finovo
```

Lege eine `local.properties` an (diese Datei gehört **nicht** ins Repository):

```properties
sdk.dir=/Pfad/zu/deinem/Android/sdk
```

Danach:

```bash
./gradlew tasks            # zeigt alle verfügbaren Aufgaben
```

In Android Studio: **File → Open** → Ordner `finovo` auswählen, Gradle-Sync abwarten, Start auf Gerät oder Emulator.

---

## Bauen

```bash
# Debug-APK
./gradlew assembleDebug

# Release-APK (unsigniert)
./gradlew assembleRelease

# Tests + Lint-freundliche Kompilierung
./gradlew compileDebugKotlin
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk` bzw. `.../release/app-release-unsigned.apk`.

> Auf wenig Arbeitsspeicher helfen `./gradlew --max-workers=1` und die in
> `gradle.properties` gesetzten Werte (`org.gradle.jvmargs=-Xmx700m`).

---

## Testen

```bash
./gradlew testDebugUnitTest
```

Stand dieses Repositories: **294 Tests, 0 Fehler.** Sie prüfen u. a.

- Geldberechnungen (durchgehend `Long`-Cents, keine Fliesskommafehler)
- CSV-Import und Datumsformate (de/en/it/sq/tr)
- Rechnungserkennung (Parser, IBAN-Prüfsumme, Konfidenz)
- Datenbank-Migration **7 → 8** gegen eine echte Datenbankdatei
- Sicherung/Wiederherstellung inklusive Dokumenten und Vermögenswerten
- Rauchtests: **jeder** Bildschirm wird leer und mit echten Daten aufgebaut
- Finanz-Alarme erzeugen wirklich eine Systembenachrichtigung

Berichte: `app/build/reports/tests/testDebugUnitTest/index.html`

---

## Release & Signierung

Es liegen **keine** Schlüssel oder Passwörter im Repository. So signierst du:

```bash
keytool -genkeypair -v \
  -keystore finovo-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias finovo
```

Die Zugangsdaten gehören in die Umgebung oder in `local.properties`
(beides ist in `.gitignore` ausgeschlossen), zum Beispiel:

```bash
export FINOVO_KEYSTORE=/pfad/zu/finovo-release.jks
export FINOVO_KEYSTORE_PASSWORD=...
export FINOVO_KEY_ALIAS=finovo
export FINOVO_KEY_PASSWORD=...
```

Danach ausrichten und signieren:

```bash
./gradlew assembleRelease
zipalign -f -v 4 app/build/outputs/apk/release/app-release-unsigned.apk Finovo.apk
apksigner sign --ks "$FINOVO_KEYSTORE" --ks-key-alias "$FINOVO_KEY_ALIAS" \
  --ks-pass env:FINOVO_KEYSTORE_PASSWORD --key-pass env:FINOVO_KEY_PASSWORD \
  --out Finovo-signed.apk Finovo.apk
apksigner verify Finovo-signed.apk
```

> **Wichtig:** denselben Schlüssel für alle Updates verwenden – sonst lässt sich
> die App nur mit Datenverlust neu installieren.

---

## Datenbank & Migrationen

- **Datenbank:** lokale SQLite-Datei `finovo.db`, verwaltet von **Room**.
- **Schema-Version:** **8** (Definition: `app/src/main/java/com/finovo/app/data/Database.kt`).
- **Tabellen:** `categories`, `expenses`, `incomes`, `bills`, `bill_payments`,
  `recurring_rules`, `budgets`, `notifications`, `settings`, `areas`,
  `saving_goals`, `subscriptions`, `category_budgets`, `bank_connections`,
  `bank_accounts`, `bank_transactions`, `category_rules`, `documents`,
  `audit_log`, `assets`.
- **Migrationen:** `2→3`, `3→4`, `4→5`, `5→6`, `6→7`, `7→8` –
  **ausschließlich erweiternd** (`ALTER TABLE … ADD COLUMN … DEFAULT …`,
  neue Tabellen, Austausch eines Index). Bestandsdaten bleiben immer erhalten.
- Entitäten: `app/src/main/java/com/finovo/app/data/Model.kt`.
- Geldbeträge sind überall **`Long` in Cent**; Formatierung und Eingabe laufen über `util/Money.kt`.

---

## Sicherung & Wiederherstellung

`backup/Backup.kt` schreibt eine **JSON-Datei** (atomar: erst `.tmp`, dann Umbenennen)
mit Kategorien, Ausgaben, Einnahmen, Rechnungen, Zahlungen, Regeln, Budget,
Einstellungen, Bereichen, Sparzielen, Abos, Kategorie-Budgets, gelernten Regeln,
**Dokumenten** und **Vermögenswerten**.

- Modus **Ersetzen**: alles wird vorher geleert (Dateien der Dokumente bleiben bewusst erhalten).
- Modus **Zusammenführen**: vorhandene Daten bleiben, es wird ergänzt – ohne Duplikate.
- Ältere Sicherungen ohne Dokumente/Vermögenswerte bleiben lesbar.

In der App: **Mehr → Daten**.

---

## Sprachen

Alle Texte liegen in `app/src/main/res/values*/strings.xml` (Strings und Plurals):

| Code | Sprache |
|---|---|
| `de` | Deutsch (Standard) |
| `en` | Englisch |
| `it` | Italienisch |
| `sq` | Albanisch |
| `tr` | Türkisch |

Alle Sprachen enthalten **dieselben Schlüssel**; die Auswahl erfolgt in
**Mehr → Einstellungen → Sprache** (oder folgt der Systemsprache).
Kein sichtbarer Text steht fest im Code.

---

## Projektstruktur

```
app/src/main/java/com/finovo/app/
├── AppViewModel.kt          # Zustand und Aktionen der App
├── MainActivity.kt          # einzige Activity
├── FinovoApp.kt           # Application, tägliche Aktualisierung
├── data/                    # Entitäten, DAO, Room-Datenbank, Migrationen
├── ui/
│   ├── Route.kt             # alle Navigationsziele
│   ├── AppRoot.kt           # Navigation, Tabs, Dialoge
│   ├── screens/             # Bildschirme (Dashboard, Ausgaben, Module …)
│   ├── components/          # wiederverwendbare Bausteine, Diagramme
│   └── theme/               # Farben, Typografie, Logo
├── util/                    # Geld, Datum, Statistik, Suche, Prognose, AI-Regeln
├── ai/                      # Finovo AI (offline, regelbasiert)
├── scan/                    # Rechnungserkennung (Kamera, OCR, Parser)
├── bank/                    # optionale Bank-Anbindung (abgeschaltet)
├── backup/                  # Sicherung/Wiederherstellung (JSON)
├── export/                  # PDF- und CSV-Export
└── notifications/           # Benachrichtigungen, tägliche Arbeit
```

Tests: `app/src/test/java/com/finovo/app/`

---

## Umgebungsvariablen

Zum **Bauen und Benutzen der App werden keine Variablen benötigt.** Die App
speichert alles lokal; es gibt keine API-Schlüssel, keine `.env`-Datei und keinen
Server. Notwendig sind nur die Werkzeug-Pfade:

| Variable | Bedeutung |
|---|---|
| `JAVA_HOME` | JDK 21 |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | Android SDK |
| `GRADLE_USER_HOME` | optional, Standard ist `~/.gradle` |
| `FINOVO_KEYSTORE`, `FINOVO_KEYSTORE_PASSWORD`, `FINOVO_KEY_ALIAS`, `FINOVO_KEY_PASSWORD` | **optional**, nur für signierte Releases |

---

## Datenschutz & Offline

- Keine Registrierung, kein Nutzerkonto, keine Werbung, kein Tracking.
- Finanzdaten verlassen das Gerät nicht.
- Im Manifest steht `INTERNET` ausschließlich für die **ruhende** Bank-Anbindung
  (`bank/BankFeature.kt`, `LIVE = false`). Solange sie aus ist, findet kein
  Netzwerkverkehr statt.
- App-Sperre per PIN oder Fingerabdruck (PIN wird **nicht** im Klartext gespeichert).
- Vor dem Löschen aller Daten erscheint eine ausdrückliche Rückfrage; vor dem
  Wiederherstellen ein Hinweis, dass Daten ersetzt oder zusammengeführt werden.
