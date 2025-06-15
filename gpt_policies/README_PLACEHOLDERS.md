# GPT Policy Placeholders

Die folgenden Platzhalter werden in den Policy-Dateien verwendet und beim Erstellen des GPT-Prompts ersetzt.

## chat_analysis
- `%message%` – der zu analysierende Chatlog
- `%rules%` – alle aktiven Serverregeln als Text
- `%flags%` – Beschreibung aller Reputation-Flags

## suggest_classify
- `%s%` – der komplette Vorschlagstext
- `%rules%` – aktuelle Serverregeln zur Einordnung

## rule_map
- `%s%` – der Vorschlagstext, der zu einer Regel werden soll
- `%rules%` – bestehende Regeln zum Abgleich auf Konflikte
