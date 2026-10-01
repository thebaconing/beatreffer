# Veröffentlichung im Google Play Store

Google Play nimmt für neue Apps nur **App Bundles (.aab)** an, keine APKs. Die Release-APK eignet sich zum direkten Installieren und Weitergeben, für den Store wird `bundleRelease` gebaut. Beide werden mit demselben Schlüssel signiert.

## 1. Upload-Schlüssel

Der Schlüssel ist bereits angelegt:

- Keystore: `../beatreffer-upload.jks` (also `F:\GameDeveatreffer-upload.jks`, außerhalb des Repos), Alias `upload`, RSA 2048, gültig 10.000 Tage
- Passwort und Pfad: `keystore.properties` im Projektordner (per `.gitignore` vom Commit ausgeschlossen)
- SHA-256-Fingerprint: `41:C4:45:D2:AC:3A:5F:89:1A:59:E8:4C:4C:0A:0E:08:C4:17:54:99:32:48:02:D2:CF:E2:46:87:0B:BC:C5:ED`

**Keystore-Datei und `keystore.properties` sicher sichern** (z. B. Passwort-Manager plus Backup auf einem zweiten Datenträger). Mit Play App Signing verwaltet Google den eigentlichen App-Schlüssel. Geht der Upload-Schlüssel verloren, lässt er sich über die Play Console zurücksetzen, das dauert aber.

Auf einem anderen Rechner: beide Dateien dorthin kopieren oder nach `keystore.properties.example` neu anlegen. Ohne `keystore.properties` wird mit dem Debug-Schlüssel signiert, den Play ablehnt.

## 2. Bauen

```sh
./gradlew bundleRelease     # app/build/outputs/bundle/release/Beatreffer-<Version>-release.aab  (für Play)
./gradlew assembleRelease   # app/build/outputs/apk/release/Beatreffer-<Version>-release.apk    (direkt installieren)
```

Vor jedem Upload muss `versionCode` in `app/build.gradle.kts` höher sein als beim letzten Upload.

## 3. Play Console

1. Entwicklerkonto auf <https://play.google.com/console> anlegen (einmalig 25 US$). Neue private Konten müssen vor der Veröffentlichung einen **geschlossenen Test mit mindestens 12 Testern über 14 Tage** durchlaufen.
2. App erstellen: Name „Beatreffer“, Standardsprache Deutsch, App, kostenlos.
3. **Store-Eintrag** (Texte und Grafiken unter `store/`):

   | Feld | Datei |
   | --- | --- |
   | App-Name (max. 30) | `store/listing/<sprache>/title.txt` |
   | Kurzbeschreibung (max. 80) | `store/listing/<sprache>/short_description.txt` |
   | Vollständige Beschreibung (max. 4000) | `store/listing/<sprache>/full_description.txt` |
   | App-Symbol 512×512 | `store/graphics/icon-512.png` |
   | Feature-Grafik 1024×500 | `store/graphics/feature-graphic-1024x500.png` |
   | Screenshots (mind. 2, Handy) | selbst vom Gerät aufnehmen, z. B. Start, Übung, Ergebnis, Fortschritt |

4. **App-Inhalte** ausfüllen:
   - Datenschutzerklärung: URL zu `PRIVACY.md` im Repo, z. B. `https://github.com/thebaconing/beatreffer/blob/main/PRIVACY.md` (vorher `KONTAKT_EMAIL` ersetzen; das Repo muss öffentlich sein).
   - Datensicherheit: „Es werden keine Nutzerdaten erhoben oder geteilt“.
   - Werbung: keine. Zielgruppe: z. B. ab 13 Jahren. Einstufungsfragebogen: keine kritischen Inhalte. Kategorie: Musik & Audio oder Bildung.
5. Unter **Test > Geschlossener Test** die `.aab` hochladen, Tester einladen, nach der Testphase Produktionszugriff beantragen und veröffentlichen.
