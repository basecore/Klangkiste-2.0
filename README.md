# KlangKiste 2.0

[![APK herunterladen](https://img.shields.io/badge/APK-GitHub%20Releases-2ea44f)](https://github.com/basecore/Klangkiste-2.0/releases) [![Android Debug APK](https://github.com/basecore/Klangkiste-2.0/actions/workflows/android-debug.yml/badge.svg?branch=main)](https://github.com/basecore/Klangkiste-2.0/actions/workflows/android-debug.yml) ![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84) ![NFC optional](https://img.shields.io/badge/NFC-optional-blue) ![KI-generiert](https://img.shields.io/badge/Code-KI--generiert-purple)

**Aus einem alten Android-Smartphone wird eine Hörbox für Kinder.** Ähnlich wie bei der MuPiBox kann eine Karte oder ein NFC-Sticker ein Hörspiel starten. Hier übernimmt das Smartphone Anzeige, Akku, Lautsprecher und – sofern vorhanden – den NFC-Leser. Kein Raspberry Pi, kein externer NFC-Leser und kein zusätzlicher Lautsprecher nötig. Ohne NFC lässt sich die Bibliothek über den Bildschirm bedienen.

KlangKiste 2.0 ist eine native Android-Neuentwicklung auf Basis der Ideen, Gestaltung und Medien der [KlangKiste-PWA](https://github.com/basecore/klangkiste). Der Code ist KI-generiert und KI-gestützt weiterentwickelt. Die App ist weder ein PWA-Wrapper noch ein MuPiBox-Port und benötigt keine Google Play-Dienste.

## Voraussetzungen und Installation

- Android 8.0 (API 26) oder neuer; Zielversion Android 15 (API 35). Die Mindestversion ist eine Build-Angabe, keine Garantie für jedes Gerätemodell.
- Für NFC: ein Handy mit aktiviertem NFC und ein kompatibler Tag. NFC ist optional.
- Debug-APKs dienen zum Ausprobieren. Ein späterer Wechsel der Signatur kann eine Neuinstallation erfordern; vorher Daten und Mediendateien sichern.

### APK herunterladen

- [Veröffentlichte APK-Vorschauen und Release-Notizen](https://github.com/basecore/Klangkiste-2.0/releases)
- [APK der ersten Vorschau direkt herunterladen](https://github.com/basecore/Klangkiste-2.0/releases/download/v2.0.0-preview.1/KlangKiste-2.0.0-preview.1-debug.apk)

Öffne die heruntergeladene `.apk` auf deinem Android-Gerät. Erlaube die Installation aus unbekannten Quellen bei Bedarf nur für die verwendete vertrauenswürdige Installationsquelle. Die APK ist eine Debug-Vorschau, keine stabile Version. Die Funktionsübersicht unten beschreibt den aktuellen Entwicklungsstand; ältere APKs enthalten möglicherweise nicht alle Funktionen.

## Was schon drin ist

| Bereich | Funktionen |
| --- | --- |
| Kinderplayer | Große, optional bunte Bedienelemente, optionale Bibliothek und Würfel-Taste, wählbarer Hintergrund, Schlafansicht, Hintergrundwiedergabe und Wiederaufnahme des Hörfortschritts. |
| Kinderbibliothek | Hörbuchbereiche erscheinen als Sammlungskacheln mit bis zu vier verschiedenen Covern und sichtbarer Gesamtzahl der Hörspiele. Eigene Übercover bleiben erhalten. Ein Tipp öffnet die Sammlung; Titel ohne Bereich bleiben sichtbar. „Alle Titel einzeln“ öffnet die flache Ansicht. |
| NFC | Tags im Elternbereich einem Titel zuordnen und im Kindermodus zum Start ans Handy halten. Die Zuordnung liegt in der App; der Tag enthält nicht das Hörspiel. |
| Bibliothek | Einzeldateien und Ordner mit Vorschau importieren, nach Suche oder Alter filtern, mehrere Titel auswählen, lokale Dateien referenzieren oder in den App-Speicher kopieren. Wiedergabelisten und CUE-Sprungmarken werden unterstützt. |
| Hörregeln | Wiedergabezeitfenster, Ruhezeiten, Tageslimits je Wochentag (0 = unbegrenzt), heute gehörte und verbleibende Zeit, Abblenddauer, Schlaf-Timer nach Zeit oder Hörspielanzahl und Altersfilter für alle Hörspiele. Titel ohne Altersempfehlung bleiben erlaubt. |
| Elternbereich | Überarbeitete Einstellungen und Nutzungsstatistik mit Hörzeiten, Lieblingshörspielen, Tageszeit und Aktivitäten; außerdem Lautstärkebegrenzung und Testton, Prüfung auf eine deutsche Vorlesestimme und Android-App-Pinning. |
| Daten und Inhalte | JSON-Import und -Export, Migration bestehender PWA-Backups und erneutes Verknüpfen fehlender Audiodateien. OHRKA-Onlinekatalog für Metadaten mit separater Audiozuordnung sowie optionale Online-Beispiele. |

### Zufallswiedergabe im Kindermodus

Unter **Elternbereich → Einstellungen → Kindermodus & Bibliothek** lässt sich „Würfel-Taste anzeigen“ einschalten. Standardmäßig ist die Taste ausgeblendet. Beim Würfeln wählt der Kinderplayer einen sichtbaren Titel mit Audiodateien innerhalb der eingestellten Altersgrenze. Wenn es Alternativen gibt, wird der aktuelle Titel nicht erneut gewählt. Gibt es keinen passenden Titel, bleibt die Taste deaktiviert.

Lokale Audiodateien lassen sich offline abspielen. Verknüpfte Dateien müssen für die App erreichbar bleiben; App-interne Kopien werden bei einer Deinstallation entfernt. Ein JSON-Backup ersetzt keine Sicherung der Audiodateien. Hinweise zur [Datenmigration](docs/DATA-MIGRATION.md).

## Schnellstart

1. Im Elternbereich eigene Hörspiele oder Musik importieren.
2. Optional einen NFC-Tag scannen und einem Titel zuordnen.
3. Unter **Einstellungen → Kindermodus & Bibliothek** Bibliothek und Würfel-Taste nach Bedarf einstellen, den Altersfilter wählen und über „Hörregeln öffnen“ Zeiten und Limits festlegen.
4. In den Kindermodus wechseln und einen Titel wählen, würfeln oder einen zugeordneten Tag ans Handy halten.
5. Zum Elternbereich zurückkehren: fünfmal zügig oben rechts tippen. Die Geste ist keine PIN-Sperre. Android-App-Pinning ersetzt weder die geplante PIN noch einen vollständigen Kioskmodus.

## Ausblick

- Optionale, standardmäßig deaktivierte vierstellige PIN für den Rückweg aus Kinderplayer und Schlafansicht. Dazu eine Elternhilfe für die Fünf-Tipp-Geste.
- Hörregeln übersichtlicher gestalten: Zeit- und Wochentagsauswahl statt Eingabeformaten, klarere Gruppen und besser sichtbare Resthörzeit.
- Optionale Inhalte-Plugins prüfen: etwa Hörspiele oder Hörbücher aus der ARD Mediathek sowie Spotify ausschließlich online. Beide Integrationen sind noch nicht enthalten und hängen von geeigneten offiziellen Schnittstellen, Nutzungsbedingungen und gegebenenfalls einem Konto ab. Spotify-Downloads für die Offline-Bibliothek sind nicht geplant.

## Entwickeln und testen

Native App mit Kotlin, Jetpack Compose, Room und Media3. Android Studio mit JDK 17, Android SDK 35 und Build-Tools 35.0.0 verwenden. Der erste Gradle-Lauf benötigt Internet.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

[GitHub Actions](https://github.com/basecore/Klangkiste-2.0/actions/workflows/android-debug.yml) baut die Debug-APK, prüft die verpackten Originalmedien und lädt sie als Artefakt `KlangKiste-2.0-debug-apk` hoch. Ein erfolgreicher Build ersetzt keinen Gerätetest. Siehe [Funktionsmatrix](FEATURE-MATRIX.md) und [Fairphone-5-Testplan](docs/FAIRPHONE-5-TESTPLAN.md). Fehler bitte mit Modell, Android-Version, App-Version und Schritten zur Reproduktion unter [Issues](https://github.com/basecore/Klangkiste-2.0/issues) melden.
