# Security

`Security` ist ein Minecraft-Plugin fuer Paper 1.20.x, das Community-Moderation und automatische Chat-Sicherheit miteinander kombiniert. Das Plugin soll Server entlasten, auf denen nicht immer ein Moderator online ist: Spieler koennen ueber gewichtete Abstimmungen gegen problematische Spieler vorgehen, waehrend das Plugin parallel Spam, Caps-Flood und beleidigende oder extremistische Begriffe im Chat erkennt und direkt sanktioniert.

Im Kern arbeitet das Plugin mit einem Karma-System. Dieses Karma basiert nicht nur auf manuellen Punkten, sondern vor allem auf echter Spielzeit. Wer regelmaessig auf dem Server spielt, hat mehr Gewicht bei Abstimmungen. Neue Accounts oder Spieler mit sehr wenig Spielzeit koennen zwar teilnehmen, ihr Stimmgewicht ist absichtlich stark reduziert. Dadurch wird Missbrauch durch Zweitaccounts deutlich erschwert.

## Was das Plugin macht

Das Plugin deckt drei Bereiche ab:

### 1. Community-Moderation ueber VoteKick und VoteBan

Spieler koennen gegen einen aktuell online befindlichen Spieler eine Abstimmung starten:

- `/votekick <spieler>` startet eine Community-Abstimmung fuer eine kurze temporaere Sanktion.
- `/voteban <spieler>` startet eine Community-Abstimmung fuer eine laengere temporaere Sperre.

Jede Abstimmung wird automatisch im Chat angekuendigt. Alle Online-Spieler erhalten klickbare `[JA]`- und `[NEIN]`-Buttons. Alternativ kann auch per Befehl abgestimmt werden. Das System arbeitet nicht mit einfachen Kopfzahlen, sondern mit gewichteten Stimmen:

- Das Stimmgewicht eines Spielers basiert auf seinem Karma.
- Karma entsteht automatisch ueber Spielzeit.
- Spieler mit sehr wenigen Joins oder sehr wenig Spielzeit haben nur einen sehr kleinen Bruchteil ihres normalen Stimmgewichts.
- Dadurch zaehlt die Stimme eines etablierten Stammspielers deutlich mehr als die eines frischen Accounts.

Eine Abstimmung ist nur erfolgreich, wenn mehrere Bedingungen gleichzeitig erfuellt sind:

- Es muessen genug `Ja`-Punkte erreicht werden.
- Es muessen genug verschiedene `Ja`-Spieler abgestimmt haben.
- Es muessen insgesamt genug Teilnehmer mitgemacht haben, damit ein Quorum erreicht wird.

Das benoetigte Quorum orientiert sich an den aktuell online befindlichen Spielern mit ausreichend Karma. So kann eine kleine Minderheit nicht allein ueber einen Spieler entscheiden.

### 2. Automatische Chat-Moderation

Das Plugin ueberwacht den Chat und reagiert automatisch auf problematisches Verhalten:

- Spam/Flood: Zu viele Nachrichten in kurzer Zeit fuehren zu einem temporaeren Mute.
- Wiederholte Nachrichten: Wenn dieselbe normalisierte Nachricht innerhalb eines kurzen Cooldowns erneut geschickt wird, gilt das ebenfalls als Spam.
- Caps-Abuse: Nachrichten mit uebermaessig vielen Grossbuchstaben werden blockiert.
- Beleidigende oder extremistische Begriffe: Das Plugin prueft den Chat gegen eine konfigurierbare Liste problematischer Woerter und Muster.

Der Wortfilter ist bewusst robuster als ein reiner String-Vergleich:

- Leetspeak wie `h1tl3r` wird normalisiert.
- Sonderzeichen und Trennzeichen werden entfernt oder vereinheitlicht.
- Wiederholte Buchstaben werden reduziert.
- Bei laengeren Begriffen sind auch leichte Schreibabweichungen erkennbar.

Wenn ein Spieler wegen Spam oder ungeeigneter Sprache sanktioniert wird, verliert er Karma. Das hat direkte Auswirkungen auf spaetere Abstimmungen und auf die Haerte zukuenftiger Sanktionen.

### 3. Persistente Spieler- und Sanktionsdaten

Das Plugin speichert dauerhaft:

- Spielzeit pro Spieler
- Join-Anzahl
- Karma-Modifikatoren
- letzte Karma-Aenderungen
- aktive Temp-Mutes
- aktive Temp-Bans

Die Daten liegen lokal in YAML-Dateien im Plugin-Datenordner. Dadurch uebersteht das System Neustarts ohne Datenverlust.

## Wie das Karma-System funktioniert

Das Karma-System ist die Grundlage des Plugins. Standardmaessig gilt:

- Pro 24 Stunden Spielzeit entsteht 1 Karma-Punkt.
- Zusaetzlich koennen Punkte durch Ereignisse steigen oder fallen.
- Die Teilnahme an einer Abstimmung bringt standardmaessig `+1 Karma`.
- Wer erfolgreich eine gueltige Sanktion startet, erhaelt ebenfalls `+1 Karma`.
- Spam, beleidigende Sprache, VoteKick und VoteBan fuehren zu Karma-Abzuegen.

Wichtig ist: Das Plugin unterscheidet zwischen sichtbarem Karma und effektivem Stimmgewicht.

- Das sichtbare Karma wird fuer `/securitystatus` und `/karmalist` verwendet.
- Das effektive Stimmgewicht wird bei Abstimmungen zusaetzlich durch Vertrauen reduziert, wenn ein Spieler kaum Joins oder kaum Spielzeit hat.

Standardverhalten fuer das Vertrauen:

- `<= 3` Joins: nur `1%` der normalen Vote-Power
- `<= 10` Joins: `5%`
- `<= 25` Joins: `20%`
- darueber: `100%`

Zusatzregel auf Basis der Spielzeit:

- unter 6 Stunden Spielzeit: maximal `2%`
- unter 24 Stunden Spielzeit: maximal `10%`

Das bedeutet praktisch: Neue Spieler koennen nicht mit wenigen Alt-Accounts sofort VoteBans forcieren.

## Negative Karma-Skalierung

Spieler mit stark negativem Karma werden haerter sanktioniert. Das Plugin verlaengert Mutes und Bans exponentiell:

- Formel: `2^(abs(karma)/step)`
- Standardwert fuer `step`: `10`
- Standard-Maximum: `32x`

Wenn ein Spieler also bereits mehrfach negativ aufgefallen ist und tief im negativen Karma liegt, dauern neue Mutes und Bans erheblich laenger.

## Commands und Nutzung

### `/votekick <spieler>`

Startet eine gewichtete Community-Abstimmung gegen einen online befindlichen Spieler.

Typischer Ablauf:

1. Ein Spieler fuehrt `/votekick Name` aus.
2. Das Plugin erstellt eine aktive Abstimmung.
3. Der Starter gibt automatisch seine erste `JA`-Stimme ab.
4. Alle Online-Spieler sehen klickbare Abstimmungs-Buttons.
5. Wenn genug Punkte, genug `JA`-Spieler und genug Teilnehmer vorhanden sind, wird eine temporaere Sanktion ausgefuehrt.

Standardmaessig fuehrt ein erfolgreicher VoteKick zu einem temp-Ban von `5 Minuten`.

### `/voteban <spieler>`

Funktioniert wie `/votekick`, aber mit einer laengeren Sanktion.

Standardmaessig fuehrt ein erfolgreicher VoteBan zu einem temp-Ban von `24 Stunden`.

### `/securityvote <yes|no> <spieler> [kick|ban]`

Mit diesem Befehl stimmt ein Spieler manuell ab. Das ist der Fallback, wenn jemand die Chat-Buttons nicht nutzen moechte oder mehrere Abstimmungen gegen denselben Spieler laufen.

Beispiele:

- `/securityvote yes Steve kick`
- `/securityvote no Alex ban`
- `/svote ja Steve kick`

Unterstuetzte Werte:

- `yes`, `ja`, `y`, `+`
- `no`, `nein`, `n`, `-`

Wenn gleichzeitig mehrere Abstimmungen gegen denselben Spieler aktiv sind, sollte der Typ `kick` oder `ban` mit angegeben werden.

### `/securitystatus [spieler]`

Zeigt:

- alle aktuell laufenden Abstimmungen
- Restlaufzeit
- bisherige `JA`- und `NEIN`-Punkte
- benoetigte Teilnehmer
- Karma und Spielzeit eines Spielers

Ohne Argument zeigt der Befehl den Status und das eigene Karma an. Mit einem Spielernamen kann ein bestimmter Spieler geprueft werden.

### `/karmalist [seite]`

Zeigt eine paginierte Topliste aller gespeicherten Spieler nach Karma. Pro Seite werden 10 Eintraege angezeigt.

## Was bei einer Sanktion passiert

### Temp-Mute

Ein Temp-Mute wird automatisch gesetzt bei:

- Spam/Flood
- problematischer Sprache

Ein gemuteter Spieler kann weiterhin online sein, aber keine Chat-Nachrichten senden. Beim Join bekommt er einen Hinweis, wie lange der Mute noch aktiv ist.

### Temp-Ban

Ein Temp-Ban wird gesetzt bei:

- erfolgreichem VoteKick
- erfolgreichem VoteBan

Ein gebannter Spieler wird beim Login abgefangen und sieht Grund und Restdauer. Ist der Spieler waehrend der Sanktion noch online, wird er unmittelbar vom Server getrennt.

## Installation

### Voraussetzungen

- Java 17
- Paper 1.20.x
- Maven zum Bauen, falls das Plugin aus dem Quellcode gebaut wird

### Plugin bauen

Im Projektordner:

```powershell
mvn package
```

Danach liegt das fertige JAR standardmaessig unter `target/`.

### Plugin installieren

1. Das gebaute JAR in den `plugins/`-Ordner des Paper-Servers kopieren.
2. Den Server starten oder neu laden.
3. Das Plugin erzeugt beim ersten Start seine Konfigurations- und Datendateien automatisch.

## Konfiguration

Die Hauptkonfiguration liegt in `src/main/resources/config.yml` und wird beim ersten Start in den Plugin-Datenordner kopiert.

Wichtige Bereiche:

### `vote`

- `duration-seconds`: Wie lange eine Abstimmung offen bleibt
- `min-voters`: Minimale Anzahl an `JA`-Spielern
- `quorum-percent`: Wie gross der benoetigte Teilnehmeranteil ist
- `quorum-min-karma`: Mindestkarma, um fuer das Quorum als relevanter Online-Spieler zu zaehlen
- `kick-ban-minutes`: Dauer einer erfolgreichen VoteKick-Sanktion
- `ban-duration-hours`: Dauer einer erfolgreichen VoteBan-Sanktion
- `initiator-cooldown-seconds`: Cooldown, bevor ein Spieler die naechste Abstimmung starten darf

### `karma`

- `base-hours-per-point`: Spielzeit pro Karma-Punkt
- `negative-scaling.step`: Schrittweite fuer die Strafmultiplikation bei negativem Karma
- `negative-scaling.max-multiplier`: Obergrenze fuer verlaengerte Sanktionen
- `rewards.vote-participation`: Karma-Belohnung pro erstmaliger Abstimmung
- `rewards.successful-vote-starter`: Belohnung fuer den Starter einer erfolgreichen Abstimmung
- `penalties.offensive-mute`: Karma-Abzug bei beleidigender Sprache
- `penalties.spam-mute`: Karma-Abzug bei Spam
- `penalties.vote-kick`: Karma-Abzug nach erfolgreichem VoteKick
- `penalties.vote-ban`: Karma-Abzug nach erfolgreichem VoteBan

### `chat`

- `mute-minutes-offensive`: Basisdauer fuer Mutes wegen problematischer Sprache
- `spam-window-seconds`: Zeitfenster fuer die Spam-Erkennung
- `spam-message-limit`: Anzahl erlaubter Nachrichten im Zeitfenster
- `duplicate-message-cooldown-seconds`: Zeitraum, in dem identische normalisierte Nachrichten als Spam zaehlen
- `spam-mute-minutes`: Basisdauer fuer Spam-Mutes
- `max-caps-ratio`: Ab welchem Verhaeltnis Grossbuchstaben als Caps-Abuse gelten
- `blocked-patterns`: Liste der geblockten Begriffe und Muster

## Datenablage

Das Plugin arbeitet ohne externe Datenbank. Es speichert lokal YAML-Dateien im Plugin-Datenordner:

- `player-stats.yml`: Spielzeit, Joins, Karma-Modifier und Verlauf der letzten Karma-Aenderung
- `sanctions.yml`: aktive Mutes und Bans

Das macht die Installation einfach, ist aber eher fuer kleine bis mittlere Server gedacht. Fuer sehr grosse Netzwerke waere spaeter eine Datenbankanbindung sinnvoll.

## Berechtigungen

Aktuell ist in `plugin.yml` eine allgemeine Permission hinterlegt:

- `securityplugin.use`

Sie steht standardmaessig auf `true`. Das bedeutet: Jeder Spieler darf die dokumentierten Commands verwenden, sofern keine weiteren Server-seitigen Einschraenkungen gesetzt werden.

## Typischer Einsatz auf einem Server

Das Plugin eignet sich besonders fuer Server, auf denen:

- nicht dauerhaft Moderatoren online sind
- die Community bei klaren Faellen selbst reagieren soll
- neue Troll-Accounts wenig Einfluss haben sollen
- Chatverstosse sofort geblockt werden sollen

Ein typischer Fall:

1. Ein Spieler spammt oder beleidigt andere.
2. Das Plugin mute't ihn automatisch oder die Community startet einen Vote.
3. Wiederholtes Fehlverhalten senkt das Karma.
4. Niedriges oder negatives Karma reduziert Vertrauen und verlaengert spaetere Strafen.
5. Langjaehrige, aktive Spieler behalten bei Entscheidungen das meiste Gewicht.

## Hinweise zur aktuellen Implementierung

- VoteKick und VoteBan setzen in der aktuellen Version beide einen temporaeren Ban, aber mit unterschiedlicher Dauer.
- Abstimmungen koennen nur gegen online befindliche Zielspieler gestartet werden.
- Die Moderation ist bewusst community-zentriert und nicht als vollstaendiger Ersatz fuer klassische Admin-Tools gedacht.
