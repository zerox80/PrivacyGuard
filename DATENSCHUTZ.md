# Datenschutz bei SilentPort

Dies ist ein **Zero-Profit**- und **Zero-Data**-Projekt.

Wir sind der festen Überzeugung, dass Software, die dem Schutz der Privatsphäre dient, selbst ein Höchstmaß an Datenschutz bieten muss. Diese App wurde von Grund auf nach dem Prinzip "Was auf dem Gerät passiert, bleibt auf dem Gerät" entwickelt.

## Das Grundprinzip: 100% Lokale Verarbeitung

SilentPort sammelt, speichert, teilt oder überträgt **keinerlei** persönliche Daten an externe Server oder Dienste.

Alle Berechnungen, Analysen (welche App wann genutzt wurde) und Firewall-Aktionen finden ausschließlich und zu 100% auf Ihrem Gerät statt. Es gibt keinen Server, mit dem die App kommuniziert – nicht einmal für Fehlerberichterstattung oder Telemetrie.

## Erforderliche Berechtigungen und warum wir sie brauchen

SilentPort benötigt mehrere Berechtigungen, die sensibel erscheinen. Hier ist der genaue Grund, warum sie für die Kernfunktionalität unerlässlich sind – und wie wir sicherstellen, dass sie nicht missbraucht werden.

### 1. Lokale-Firewall (`BIND_VPN_SERVICE`)

Um den Netzwerkzugriff für andere Apps zu blockieren oder freizugeben, nutzt SilentPort die `VpnService`-API von Android.

**Dies ist KEIN echtes VPN – Ihre Daten bleiben geschützt:**

* Es wird **niemals** eine Verbindung zu einem externen Server hergestellt
* Ihr Netzwerkverkehr wird **nicht umgeleitet, nicht inspiziert und nicht protokolliert**
* Die App erstellt einen lokalen Filter auf Ihrem Gerät basierend auf den `addAllowedApplication()` und `addDisallowedApplication()`-Funktionen
* Für **blockierte Apps**: Der Netzwerkverkehr wird an einen lokalen "leeren" Tunnel gesendet und dort verworfen (technisch: `ParcelFileDescriptor.AutoCloseInputStream` mit `drainPackets()`-Implementierung)
* Für **freigegebene Apps**: Der Netzverkehr läuft normal ab – die App führt keine Inspektion durch
* **Keine Netzwerkbeobachtung**: Die Firewall sieht nur, welche App Netzwerk anfordert, nicht *was* sie sendet/empfängt

### 2. Nutzungsstatistiken (`PACKAGE_USAGE_STATS`)

Dies ist die absolute Kernfunktion der App.

* **Zweck**: SilentPort muss wissen, *wann* Sie eine App zuletzt verwendet haben, um festzustellen, ob sie "selten" oder "kürzlich" ist
* **Was wird gemessen**: Nur der Zeitstempel der letzten Foreground-Aktivität (wann Sie die App zuletzt wirklich geöffnet haben)
* **Implementierung**: Wir verwenden den `UsageStatsManager` (implementiert in `UsageAnalyzer.kt`), um ausschließlich `MOVE_TO_FOREGROUND` und `ACTIVITY_RESUMED` Ereignisse abzufragen – nicht Ihre App-Inhalte
* **Datenspeicherung**: Diese Informationen (nur App-Name und Zeitstempel) werden **nur lokal** in der Room-Datenbank (`AppDatabase`) auf Ihrem Gerät gespeichert
* **Keine Synchronisation**: Diese Daten werden nie mit Android Cloud Backup synchronisiert (siehe: `backup_rules.xml`)

### 3. App-Liste (`QUERY_ALL_PACKAGES`)

* **Zweck**: Erforderlich, um Ihnen eine vollständige Liste aller installierten Anwendungen anzuzeigen, die von der Firewall verwaltet werden können
* **Datenspeicherung**: Diese Liste wird nur zur Laufzeit und in der lokalen Datenbank verwendet – niemals exportiert
* **Keine Nebeneffekte**: Die Abfrage hat keinen Seiteneffekt auf die Funktionalität anderer Apps

### 4. Benachrichtigungen (`POST_NOTIFICATIONS`)

* **Zweck**: Damit Sie wichtige Firewall-Status-Updates und Warnungen erhalten können
* **Was wird gesendet**: Nur Benachrichtigungstexte, die Sie selbst in den Einstellungen konfigurieren
* **Keine Analyse**: Benachrichtigungen enthalten keine eindeutigen IDs oder Tracking-Informationen

### 5. Internetzugriff (`INTERNET`)

* **Aktuelles Verhalten**: Momentan **nicht aktiv genutzt**
* **Warum deklariert**: Reserviert für zukünftige Funktionen (z.B. optionales Fehlerberichtssystem)
* **Sicherheit**: Selbst wenn diese Funktion in Zukunft implementiert wird:
  - Sie werden **explizit gefragt**, bevor Sie aktivieren können
  - Es werden **niemals Nutzungsdaten** übertragen
  - Sie können diese Berechtigung jederzeit in den Systemeinstellungen widerrufen
  - Der Quellcode bleibt Open Source und überprüfbar

### 6. Vordergrunddienst (`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`)

* **Zweck**: Dies ist eine technische Anforderung von Android. Damit der `VpnService` (die Firewall) zuverlässig im Hintergrund laufen kann, muss er als Vordergrunddienst mit einer persistenten Benachrichtigung deklariert werden
* **Nutzen**: Sie sehen eine Benachrichtigung, wenn die Firewall aktiv ist – das ist gewünscht, damit Sie volle Kontrolle haben

## Hintergrund-Synchronisation

SilentPort führt eine **regelmäßige Aktualisierung** durch (alle 6 Stunden):

* **Was**: Die lokale Datenbank wird mit den aktuellen Nutzungsstatistiken aktualisiert
* **Wie**: Mit `WorkManager` und `UsageSyncWorker` implementiert
* **Wohin**: Nur in die lokale Datenbank – kein Netzwerk beteiligt
* **Datenlöschung**: Wenn Sie eine App deinstallieren, werden ihre Daten lokal gelöscht

## Unser "Zero Data"-Versprechen (Technische Beweise)

Wir behaupten nicht nur, keine Daten zu sammeln, wir haben es technisch sichergestellt:

### 1. Keine Tracker oder Werbe-SDKs

Die App enthält absolut keine Drittanbieter-Bibliotheken für:
* ❌ Tracking (Google Analytics, Mixpanel, etc.)
* ❌ Werbung (AdMob, etc.)
* ❌ Crash-Reporting (Crashlytics, etc.)
* ❌ Telemetrie (Facebook SDK, etc.)

Dies ist in `app/build.gradle.kts` ersichtlich – die Abhängigkeitsliste enthält nur:
- Android Framework & Jetpack (Compose, Room, WorkManager)
- Kotlin Standard Library
- Begleitende Icon-Bibliothek

**Beweis**: Der gesamte Source-Code ist öffentlich – jeder kann die Abhängigkeiten überprüfen.

### 2. Keine Cloud-Backups für Ihre Daten

* Wir haben die automatische Cloud-Sicherung von Android für die App-Daten **explizit deaktiviert** in `backup_rules.xml`:
  ```xml
  <cloud-backup>
    <exclude domain="sharedpref" />
    <exclude domain="database" />
  </cloud-backup>
  ```
* Selbst wenn Sie Google Backups nutzen, werden die Daten von SilentPort **nicht** in die Cloud hochgeladen
* Sie haben vollständige Kontrolle: In den Android-Einstellungen können Sie Backups pro App konfigurieren

### 3. Keine Netzwerk-Kommunikation der App-Logik

* Die App selbst (außer des optional deklarierten `INTERNET` für Zukunft) sendet keine Daten ins Netz
* Der `VpnService` arbeitet lokal – er agiert als Filter, nicht als Proxy zu externen Servern
* Netzwerk-Metriken (optional): Falls Sie die Netzwerk-Metriken aktivieren, werden diese **lokal berechnet** (nicht zu Google Play Services oder sonst wo übertragen)

### 4. Transparente Berechtigungen in den Systemeinstellungen

* Jede Berechtigung ist in `AndroidManifest.xml` explizit deklariert
* Sie können jederzeit in den Android-Einstellungen überprüfen, welche Berechtigungen aktiv sind
* Sie können Berechtigungen granular widerrufen

## Datensicherheit auf dem Gerät

* **Verschlüsselte Speicherung**: Room-Datenbank nutzt SQLCipher für Verschlüsselung (optional mit Android Keystore)
* **Keine Hardcoding von Geheimnissen**: Keine API-Keys oder Credentials im Code
* **Sicherer Speicher**: Daten werden im App-spezifischen Verzeichnis gespeichert (andere Apps können nicht zugreifen)

## Open Source und Transparenz

SilentPort ist vollständig **Open Source** unter der **GPL 3.0 Lizenz**:

* Sie können den **gesamten Quellcode** einsehen
* Sie können die App selbst kompilieren und überprüfen
* Sie können Modifikationen machen und verteilen
* Die Lizenz garantiert, dass Sie diese Freiheiten behalten

**Weitere Überprüfungsmöglichkeiten:**
1. Laden Sie die App aus dem Source herunter und kompilieren Sie sie selbst
2. Nutzen Sie Network-Monitoring-Tools (z.B. Wireshark) um zu überprüfen, dass kein Netzwerkverkehr stattfindet
3. Lesen Sie den Code in `VpnFirewallService.kt`, `UsageAnalyzer.kt` und `FirewallController.kt`

## Compliance und Standards

* **GDPR-konform**: Keine Daten von EU-Bürgern werden gesammelt oder verarbeitet
* **Keine Tracking**: Erfüllt die Definition von "Privacy by Design"
* **Minimale Berechtigungen**: Nur Berechtigungen, die für die Kernfunktionalität notwendig sind

## Unser Versprechen

**Dies ist ein nicht-kommerzielles Projekt:**
* Wir werden Sie **niemals tracken**
* Wir werden Ihre Daten **niemals verkaufen**
* Wir werden diese App **niemals monetarisieren** (keine In-App-Purchases, keine Ads, keine Premium-Version)
* Wir werden diese **Datenschutzerklärung immer aktuell halten**

Wenn wir in der Zukunft von diesem Versprechen abweichen, wird der Code weiterhin Open Source bleiben, und Sie können einen "Fork" machen oder zur Alternative wechseln.

---

## Zusammenfassung: Was wirklich passiert

| Aktion | Lokal? | Netzwerk? | Speicherung |
|--------|--------|----------|-------------|
| App-Nutzung tracken | ✅ Ja | ❌ Nein | Nur lokal in DB |
| Firewall-Regeln anwenden | ✅ Ja | ❌ Nein | Nur lokal in Prefs |
| Netzwerk-Metriken (optional) | ✅ Ja | ❌ Nein | Nur lokal in Memory |
| Benachrichtigungen | ✅ Ja | ❌ Nein | System-Benachrichtigungen |
| Cloud-Backup | ❌ Nein | ❌ Nein | Explizit deaktiviert |
| Telemetrie | ❌ Nein | ❌ Nein | Gar nicht implementiert |

## Fragen und Kontakt

Falls Sie Fragen zur Sicherheit oder zum Datenschutz haben:

1. **Lesen Sie den Quellcode** – er ist öffentlich und dokumentiert
2. **Nutzen Sie Network-Monitoring** – überprüfen Sie selbst, dass keine Daten übertragen werden
3. **Öffnen Sie ein Issue** auf GitHub (falls das Projekt auf GitHub gehostet wird)

**Unser Versprechen: Wir lesen nur die minimal notwendigen Daten (App-Liste, letzter Zeitstempel), um die Kernfunktion zu erfüllen. Alle diese Daten verlassen niemals Ihr Gerät.**
