# Beatreffer

Android-App zum Üben von Timing. Du tippst den Takt auf einem großen Button nach, ein Klick gibt den Takt vor und lässt sich stufenweise ausblenden. Jeder Tipp wird bewertet, der Verlauf zeigt Fortschritt, Bestwerte und Schwachstellen.

## Voraussetzungen

| Was | Version |
| --- | --- |
| JDK | 17 (z. B. [Temurin](https://adoptium.net/) oder das JDK aus Android Studio) |
| Android SDK | Platform 35 (`platforms;android-35`) und Build-Tools 35 |
| Gradle | wird über den Wrapper (`gradlew`) automatisch geladen, 8.11.1 |

Das Projekt nutzt AGP 8.7, Kotlin 2.1 und Jetpack Compose. Außer AndroidX/Compose gibt es keine externen Bibliotheken.

## SDK einrichten

### Variante A: mit Android Studio

1. [Android Studio](https://developer.android.com/studio) installieren und starten. Der Assistent lädt SDK und JDK.
2. Projektordner öffnen (File > Open). Android Studio legt `local.properties` mit dem SDK-Pfad an.

### Variante B: nur Kommandozeile

1. JDK 17 installieren und `JAVA_HOME` darauf setzen.
2. Die [Command-line tools](https://developer.android.com/studio#command-line-tools-only) herunterladen und nach `<SDK>/cmdline-tools/latest/` entpacken.
3. Pakete installieren und Lizenzen akzeptieren:

   ```sh
   sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   sdkmanager --licenses
   ```

4. Im Projektordner eine Datei `local.properties` anlegen (wird nicht eingecheckt):

   ```properties
   # Windows: Backslashes verdoppeln
   sdk.dir=C\:\\Users\\<name>\\AppData\\Local\\Android\\Sdk
   # Linux/macOS
   # sdk.dir=/home/<name>/Android/Sdk
   ```

   Alternativ die Umgebungsvariable `ANDROID_HOME` setzen.

## Bauen

Unter Windows `gradlew.bat` statt `./gradlew` verwenden.

```sh
./gradlew assembleDebug        # Debug-APK
./gradlew assembleRelease      # Release-APK
./gradlew testDebugUnitTest    # Unit-Tests der Kernlogik (core/)
./gradlew installDebug         # auf angeschlossenes Handy installieren
```

Die APKs liegen danach unter:

- `app/build/outputs/apk/debug/Beatreffer-<Version>-debug.apk`
- `app/build/outputs/apk/release/Beatreffer-<Version>-release.apk`

Der erste Build lädt Gradle und alle Abhängigkeiten herunter und dauert einige Minuten.

**Signierung:** Der Release-Build wird derzeit mit dem Debug-Schlüssel signiert (siehe `app/build.gradle.kts`). Das reicht zum Installieren und Testen, für eine Veröffentlichung im Play Store braucht es einen eigenen Keystore.

**Version:** `versionCode` und `versionName` stehen in `app/build.gradle.kts`.

## Aufs Handy bringen

- **Per USB:** Am Handy Entwickleroptionen und USB-Debugging aktivieren, anschließen, dann `./gradlew installDebug` oder `adb install -r <apk>`.
- **Ohne Kabel:** APK aufs Handy kopieren und öffnen. Android fragt einmal, ob Installationen aus dieser Quelle erlaubt sind.

Mindestens Android 8.0 (API 26).

## Projektaufbau

```text
app/src/main/java/de/simon/beatreffer/
  core/    Reine Kotlin-Logik (Takt-Plan, Bewertung, Fortschritt, Kalibrierung, CSV), per JUnit getestet
  audio/   Klick-Erzeugung und sample-genaue Wiedergabe über AudioTrack
  data/    Speichern/Laden (SharedPreferences, JSON-Zeilen), Export
  ui/      ViewModel und Compose-Screens
app/src/test/  Unit-Tests für core/
```

## Funktionen

- Einfacher Modus: 4/4, alle Schläge, Tempo, Dauer (Minuten oder Takte), Ton an / vorzählen dann still / komplett still
- Erweiterter Modus: freie Taktart mit Betonungsgruppen, Unterteilungen, Rhythmus-Muster, Zielschlag fest oder rotierend, sechs Ausblendstufen, Toleranzfenster, Fehltipp-Wertung
- Latenz-Kalibrierung
- Live-Feedback pro Tipp, Auswertung nach jeder Übung
- Verlauf mit Diagrammen, Vergleich mit 7 und 30 Tagen, Bestwerte, Schwachstellen-Analyse, Stufen-Vorschläge
- CSV-Export (optional mit jedem einzelnen Tipp)
- Deutsch und Englisch, je nach Systemsprache
