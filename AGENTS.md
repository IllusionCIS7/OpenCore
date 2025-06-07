# OpenCore – GPT-gestütztes Entscheidungsframework für Minecraft

## Projektbeschreibung

OpenCore ist ein modulares Minecraft-Plugin für Paper-Server, das demokratische Entscheidungsprozesse in die Serverstruktur integriert. Es erlaubt der Community, Spielregeln und Konfigurationen vorzuschlagen, zu diskutieren und abzustimmen – mit Unterstützung durch GPT-gestützte Analysen, Reputationssysteme und vollständige Änderungsverläufe.

Ziel ist ein transparenter, partizipativer Serverbetrieb, bei dem Moderation, Regeln und technische Settings nicht durch Admins, sondern durch kollektive Intelligenz verwaltet werden.

## Zweck und Zielsetzung

* Spielerinnen und Spieler befähigen, Spielregeln und Konfigurationen aktiv mitzugestalten
* GPT nutzen, um Vorschläge einzuordnen, zu bewerten und Verhalten zu analysieren
* Reputation als dynamische Vertrauensmetrik einführen
* Alle Entscheidungen mit Historie, Rückverfolgbarkeit und Rollback absichern
* Transparenz schaffen über Werte, Prozesse und Auswertungen

## Funktionsumfang des Plugins

### Vorschläge und Abstimmung

* Spielerinnen und Spieler können Vorschläge einreichen (/suggest)
* GPT klassifiziert: Regel- oder Konfigurationsvorschlag
* Voting beginnt automatisch – Reputation gewichtet Stimmkraft
* Bei Zustimmung: Änderung wird automatisch übernommen (ConfigService, RuleService)

### GPT-Verarbeitung

* Eingereichte Texte (z. B. Chat, Vorschläge) werden durch GPT analysiert
* GPT liefert strukturierte JSON-Antworten zur Weiterverarbeitung
* GPT läuft getaktet (ein Request alle zehn Minuten) über eine FIFO-Queue

### Reputationssystem

* Reputation basiert auf GPT-Erkennung von Verhalten im Chat (positiv oder negativ)
* Spieler mit hoher Reputation haben mehr Stimmgewicht
* Alle Änderungen werden in Events geloggt
* Optional: Decay bei Inaktivität

### Chat-Monitoring

* Alle Chatzeilen werden gespeichert
* Regelmäßige GPT-Auswertung erkennt toxisches, hilfreiches oder beleidigendes Verhalten
* Negative Bewertungen führen zu Reputationsminderung inklusive Rückmeldung an den Spieler

### Regel- und Konfigurationsverwaltung

* Regeln sind textbasiert, beschreibbar, editierbar mit GPT-Unterstützung
* Konfigurationsparameter sind:

    * explizit als "editierbar" markiert
    * mit Minimal- und Maximalwerten
    * mit "Impact-Rating" zur Abstimmungsgewichtung
* Änderungsverlauf und Rollbackfunktion sind integriert

### Transparenz und Feedback

* Spieler können ihre Reputation, Vorschläge und GPT-Logs einsehen
* Admins haben Überblick über Änderungen, Queue, Datenbankstatus
* Discord-Webhooks möglich (zum Beispiel bei neuen Vorschlägen)

## Technische Architektur (vereinfacht)

\[Spieler] --\[Vorschlag/Chat/Command]--> \[OpenCore]
|
v
\[GptQueueManager] ---> \[OpenAI GPT]
|
v
\[VotingService] --+--> \[Suggestion DB]
|
\[ConfigService | RuleService]
|
v
\[ChangeHistory | Rollback]

## Rollen

* Spieler – reichen Vorschläge ein, stimmen ab, beeinflussen Regeln und Konfiguration
* GPT – dient als Vorschlags-Analyst, Chat-Evaluator, Hilfssystem
* Admins – schützen Infrastruktur, können eingreifen, aber nicht diktieren
* System – sorgt für Timing, Integrität, Rückverfolgbarkeit und Validierung

## Vision

OpenCore ist ein Werkzeugkasten für Community-getriebene Governance auf Minecraft-Servern. Es fördert Partizipation, Fairness und Transparenz, indem es Verantwortung mit digitalen Tools sinnvoll strukturiert. GPT ist dabei kein Entscheider, sondern ein Berater – die letzte Entscheidung trifft die Community.
