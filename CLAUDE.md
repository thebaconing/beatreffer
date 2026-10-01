# Beatreffer

Android-App zum Üben von Timing: Der Nutzer tippt einen Takt auf einem großen Button nach, ein Klick gibt den Takt vor und kann stufenweise ausgeblendet werden. Die App bewertet jeden Tipp, speichert den Verlauf und zeigt Fortschritt, Bestwerte und Schwachstellen.

## Stack

- Kotlin, Jetpack Compose (Material 3), eine Activity, kein Navigation-Framework
- minSdk 26, targetSdk 35, AGP 8.7, Kotlin 2.1, Gradle 8.11
- Keine Datenbank: Einstellungen in SharedPreferences, Übungen als JSON-Zeilen in `filesDir/sessions.jsonl`, einzelne Tipps in `filesDir/taps/<id>.txt`
- Keine externen Bibliotheken außer AndroidX/Compose. Diagramme sind selbst gezeichnet (Canvas).

## Aufbau

```
app/src/main/java/de/simon/beatreffer/
  core/        Reine Kotlin-Logik ohne Android-Abhängigkeiten, voll per JUnit testbar
    Model.kt         PracticeConfig, TimeSignature, Enums (MuteMode, Tolerance, ...)
    Patterns.kt      Rhythmus-Vorlagen als Raster ("x" = tippen, "." = Pause)
    SessionPlan.kt   PlanBuilder: erzeugt aus der Config Klicks, erwartete Tipps und Takt-Infos
    Evaluator.kt     TapEvaluator: bewertet Tipps live und liefert am Ende SessionResult
    Progress.kt      Vergleich 7/30 Tage, Bestwerte, Tageswerte, Schwachstellen-Analyse, Stufen-Vorschläge
    Calibration.kt   Latenz-Messung (Median der Abweichungen)
    Csv.kt           CSV-Export (Semikolon, Dezimalkomma bei deutscher Sprache)
  audio/       ClickEngine (AudioTrack-Streaming, sample-genau), ClickSounds (synthetische Klicks)
  data/        Repository (Speichern/Laden/Export), JsonMapping
  ui/          MainViewModel (Zustand + Navigation über enum Screen) und alle Compose-Screens
  MainActivity.kt
app/src/test/  JUnit-Tests für core/
```

## Wichtige Konzepte

- **Zeitachse:** Zeit 0 = Beginn des ersten Vorzähltakts. Alles in `core` rechnet in Sekunden (Double).
- **Timing der Tipps:** `ClickEngine.timelineSec(nanoTime)` bildet einen Zeitpunkt über `AudioTrack.getTimestamp` auf die Klick-Zeitachse ab. Touch-Zeit kommt aus `PointerInputChange.uptimeMillis` (gleiche Uhr wie `System.nanoTime`). Davon wird die Kalibrierung abgezogen.
- **Einfacher Modus:** `PracticeConfig.effective()` setzt im einfachen Modus alle erweiterten Optionen auf Standard (4/4, alle Schläge, Ton an / vorzählen dann still / komplett still). Immer mit `effective()` arbeiten, nicht mit den Rohwerten.
- **Gleiche Einstellung:** `PracticeConfig.comparisonKey()` bestimmt, welche Übungen verglichen werden (ohne Dauer, Klang, Anzeige).
- **Punkte:** Treffer im Fenster bringen 0,5 bis 1 Punkt je nach Genauigkeit, verpasste 0. Bei "Fehltipps werten" zählen Extra-Tipps zum Nenner.
- **Toleranzfenster** wird auf knapp ein halbes Raster begrenzt, damit Nachbarschläge eindeutig bleiben.

## Befehle

```
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/Beatreffer-0.5-debug.apk
./gradlew testDebugUnitTest    # Unit-Tests für core/
./gradlew installDebug         # auf angeschlossenes Handy installieren
```

Unter Windows `gradlew.bat` statt `./gradlew`.

## Konventionen

- Code-Kommentare auf Deutsch, Bezeichner auf Englisch
- Alle Texte in `res/values/strings.xml` (Englisch, Standard) und `res/values-de/strings.xml` (Deutsch). Neue Strings immer in beiden Dateien anlegen.
- Logik, die sich ohne Android testen lässt, gehört nach `core/` und bekommt einen Test
- Texte in der UI nie komplett in Großbuchstaben
- Versionierung: Nur auf ausdrückliche Anweisung des Nutzers hochzählen (z. B. „neue Version“, „Version erhöhen“). Dann in `app/build.gradle.kts` `versionCode` um 1 erhöhen und `versionName` anpassen (Standard: +0.1, z. B. 0.5 → 0.6; bei Angabe die genannte Version), außerdem Versionsangaben in dieser Datei (APK-Name, „Stand“) aktualisieren. Ohne Anweisung die Version nie ändern.

## Stand

- Version 0.5, erstellt ohne Zugriff auf ein Android SDK. Die Kernlogik in `core/` ist getestet, der Android-/Compose-Teil wurde noch nie kompiliert. Beim ersten Build können kleinere Compile-Fehler auftauchen, die zu beheben sind.
- Noch offen / Ideen: Vibration als Feedback, eigene Muster im Editor, Rotation mit mehreren Zielschlägen gleichzeitig
