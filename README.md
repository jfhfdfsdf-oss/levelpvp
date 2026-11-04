# LevelPvP – Paper 26.2

Ein Java-Paper-Plugin für Minecraft Java 26.2 mit Levelsystem, Herzen, PvP-Schaden, XP-Multiplikator, Statistik-GUI, Wiedergeburts-Kristall, Seelenbefreiung.

## Voraussetzungen

- Minecraft Java / Paper **26.2**
- **Java 25** (zum Starten des Servers und zum Bauen)
- Internetverbindung beim ersten Build (Gradle und Paper-API werden geladen)

## Build unter Windows

```bat
gradlew.bat clean build
```

## Build unter Linux

```bash
chmod +x ./gradlew
./gradlew clean build
```

Die fertige JAR liegt danach in `build/libs/`.

> Hinweis: Die mitgelieferten `gradlew`-Skripte sind schlanke Bootstrap-Skripte. Sie laden Gradle 9.2.1 beim ersten Aufruf herunter. Das Projekt enthält deshalb die Wrapper-Properties, aber nicht das übliche `gradle-wrapper.jar`.

## GitHub Actions

Das Projekt nach GitHub hochladen. Der Workflow `.github/workflows/build.yml` startet bei Push, Pull Request und manuell. Die JAR kann im jeweiligen Workflow-Lauf unter **Artifacts** heruntergeladen werden.

## Installation

1. Paper 26.2 mit Java 25 starten.
2. `LevelPvP-1.0.0.jar` aus `build/libs/` in den Serverordner `plugins/` legen.
3. Server neu starten.
4. Spielerdaten liegen in `plugins/LevelPvP/players.yml` und `global.yml`.

## Befehle

- `/stats`
- `/pvpmod reload`
- `/pvpmod setlevel <Spieler> <Level>`
- `/pvpmod addlevel <Spieler> <Anzahl>`
- `/pvpmod removelevel <Spieler> <Anzahl>`
- `/pvpmod stats <Spieler>`
- `/pvpmod unban <Spieler>`
- `/pvpmod reset <Spieler>`

Alle `/pvpmod`-Befehle benötigen `levelpvp.admin` (standardmäßig OP).

## Wichtige Berechnungen

- Maximalleben: Level 0 = 20 HP; positive Level addieren 1 HP je Level; negative Level ziehen 2 HP je Level ab; Level -10 wird auf 1 HP begrenzt, damit der Spieler nicht mit 0 Lebenspunkten existiert.
- Angriff: Multiplikator `1 + Level / 30.0`. Es gibt **kein künstliches Damage-Cap** im Plugin.
- XP: Multiplikator `1 + 4 * Level / 30.0`, bei negativen Leveln mindestens `settings.negative-xp-minimum`.
- Schadensreduzierung: `max(0, Level) / 30 * 0.05`, also höchstens 5 %.

## Hinweise / Grenzen

- Die angefragte Streitkolben-/Waffenverwaltung ist in dieser Version nicht enthalten.

- Die Seelenbefreiung listet die von diesem Plugin verwalteten Banns. Externe Banns, die nicht durch LevelPvP ausgelöst wurden, werden nicht automatisch in der Liste geführt.
- Der Plugin-Bann wird über die Bukkit-Namens-Banliste verwaltet; UUIDs werden zusätzlich für die Plugin-Daten gespeichert.
- Das Projekt wurde in dieser Umgebung nicht gegen eine echte Paper-26.2-Installation kompiliert oder im Spiel getestet. Bitte zuerst auf einem Testserver prüfen und vor dem Live-Einsatz ein Backup machen.
