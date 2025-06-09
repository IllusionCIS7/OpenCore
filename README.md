# OpenCore Plugin – FreibauSMP 2.0

Ein modulares Bukkit-Plugin für Paper 1.21.4, das demokratische Communitysteuerung im Minecraft-Server ermöglicht.

## 🔧 Module
- **Config-Modul:** Spieler schlagen Parameteränderungen vor, GPT mapped, Community stimmt ab
- **Reputationsmodul:** Chatverhalten wird GPT-basiert analysiert und in ein Punktesystem übersetzt
- **GPT-Modul:** zentrale asynchrone Queue, Intervallsteuerung, Logging, modulübergreifende Nutzung
- **Prompts:** Datenbanktabelle `gpt_prompts` hält Vorlagen für die verschiedenen GPT-Anfragen

## 📡 Schnittstellen
- GPT (OpenAI API)
- Ingame-Commands & Events
- Webhooks & Discord geplant

## ⚙️ Technologien
- Java 17, Paper 1.21.4
- Bukkit Scheduler (async)
- YAML- und JSON-Konfigurationen

## Datenbankeinrichtung
In der `database.yml` kann zwischen einer lokalen SQLite-Datei und einer MariaDB gewählt werden. Standard ist SQLite:

```yml
engine: sqlite # oder "mariadb"
file: opencore.db
host: localhost
port: 3306
database: opencore
username: root
password: password
```

OpenCore nutzt einen HikariCP-Pool mit zehn Verbindungen. Beim Start wird ein Ping ausgeführt und im Log ausgegeben.

## GPT-Konfiguration
Das GPT-Modul wird in `gpt.yml` eingestellt:

```yml
enabled: true
api-key: "REPLACE-ME"
interval-seconds: 600
model: "gpt-3.5-turbo"
temperature: 0.8
```

`model` bestimmt das zu verwendende OpenAI-Modell, `temperature` die Kreativität der Antworten.

## 🧠 Ziel
Ein Server, der durch Spieler gesteuert, durch GPT unterstützt und durch klare Regeln geschützt wird.

## 🛡 Berechtigungen
Jeder Befehl besitzt eine eigene Permission:

| Command | Permission |
|---------|------------|
| /opencore (alias /oc) | `opencore.command.opencore` |
| /suggest | `opencore.command.suggest` |
| /suggestions | `opencore.command.suggestions` |
| /vote | `opencore.command.vote` |
| /rules | `opencore.command.rules` |
| /rollbackconfig | `opencore.command.rollbackconfig` |
| /myrep | `opencore.command.myrep` |
| /gptlog | `opencore.command.gptlog` |
| /repinfo | `opencore.command.repinfo` |
| /repchange | `opencore.command.repchange` |
| /status | `opencore.command.status` |
| /configlist | `opencore.command.configlist` |
| /votestatus | `opencore.command.votestatus` |
| /editrule | `opencore.command.editrule` |
| /rulehistory | `opencore.command.rulehistory` |
| /chatflags | `opencore.command.chatflags` |
| /reload | `opencore.command.reload` |

Alle Befehle lassen sich auch als Unterbefehle von `/opencore` bzw. `/oc` ausführen. Die deutschen Aliase sind in `command-aliases.yml` definiert und können angepasst werden.
