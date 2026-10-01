<p align="center"><img src="docs/logo.svg" alt="Beatreffer" width="360"></p>

# Beatreffer

Android-App zum Üben von Timing. Du tippst den Takt auf einem großen Button nach, ein Klick gibt den Takt vor und lässt sich stufenweise ausblenden. Jeder Tipp wird bewertet, der Verlauf zeigt Fortschritt, Bestwerte und Schwachstellen.

## Voraussetzungen

| Was | Version |
| --- | --- |
| JDK | 17 (z. B. [Temurin](https://adoptium.net/) oder das JDK aus Android Studio) |
| Android SDK | Platform 36 (`platforms;android-36`) und Build-Tools 36 |
| Gradle | wird über den Wrapper (`gradlew`) automatisch geladen, 9.7.1 |

Das Projekt nutzt AGP 9.4, Kotlin 2.2 und Jetpack Compose. Außer AndroidX/Compose gibt es keine externen Bibliotheken.

## SDK einrichten

### Variante A: mit Android Studio

1. [Android Studio](https://developer.android.com/studio) installieren und starten. Der Assistent lädt SDK und JDK.
2. Projektordner öffnen (File > Open). Android Studio legt `local.properties` mit dem SDK-Pfad an.

### Variante B: nur Kommandozeile

1. JDK 17 installieren und `JAVA_HOME` darauf setzen.
2. Die [Command-line tools](https://developer.android.com/studio#command-line-tools-only) herunterladen und nach `<SDK>/cmdline-tools/latest/` entpacken.
3. Pakete installieren und Lizenzen akzeptieren:

   ```sh
   sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
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
./gradlew bundleRelease        # App Bundle für den Play Store
./gradlew testDebugUnitTest    # Unit-Tests der Kernlogik (core/)
./gradlew installDebug         # auf angeschlossenes Handy installieren
```

Die APKs liegen danach unter:

- `app/build/outputs/apk/debug/Beatreffer-<Version>-debug.apk`
- `app/build/outputs/apk/release/Beatreffer-<Version>-release.apk`

Der erste Build lädt Gradle und alle Abhängigkeiten herunter und dauert einige Minuten.

**Signierung:** Liegt eine `keystore.properties` im Projektordner, wird der Release-Build mit dem eigenen Upload-Schlüssel signiert, sonst mit dem Debug-Schlüssel. Wie man den Schlüssel anlegt und die App im Play Store veröffentlicht, steht in [RELEASE.md](RELEASE.md).

**Version:** `versionCode` und `versionName` stehen in `app/build.gradle.kts`.

## Aufs Handy bringen

- **Per USB:** Am Handy Entwickleroptionen und USB-Debugging aktivieren, anschließen, dann `./gradlew installDebug` oder `adb install -r <apk>`.
- **Ohne Kabel:** APK aufs Handy kopieren und öffnen. Android fragt einmal, ob Installationen aus dieser Quelle erlaubt sind.

Mindestens Android 8.0 (API 26).

## Projektaufbau

```text
app/src/main/java/de/thebaconing/beatreffer/
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
