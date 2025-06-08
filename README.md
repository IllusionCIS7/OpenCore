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

## 🧠 Ziel
Ein Server, der durch Spieler gesteuert, durch GPT unterstützt und durch klare Regeln geschützt wird.
