# Privacy at SilentPort

This is a **Zero-Profit** and **Zero-Data** project.

We strongly believe that software designed to protect privacy must, itself, provide the highest level of data protection. This app was developed from the ground up based on the principle: "What happens on the device, stays on the device."

## The Core Principle: 100% Local Processing

SilentPort does not collect, store, share, or transmit **any** personal data.

All calculations, analyses (which app was used when), and firewall actions occur exclusively and 100% on your device. There is no server that the app communicates with.

## Required Permissions and Why We Need Them

SilentPort requires permissions that may seem sensitive. Here is the exact reason why they are essential for the core functionality—and how we ensure they are not misused.

### 1. Local Firewall (`BIND_VPN_SERVICE`)

To block network access for other apps, SilentPort uses Android's `VpnService` API.

**This is NOT a real VPN:**
* No connection to an external server is **ever** established.
* Your network traffic is **never** redirected, monitored, or logged.
* The app creates an "empty" local tunnel on your device. Network traffic from blocked apps is sent to this tunnel and **immediately discarded** (`drainPackets` implementation).

### 2. Usage Statistics (`PACKAGE_USAGE_STATS`)

This is the app's absolute core function.

* **Purpose:** SilentPort needs to know *when* you last used an app to determine if it is "rare."
* **Implementation:** We use the `UsageStatsManager` (implemented in `UsageAnalyzer.kt`) to *exclusively* query the timestamp of an app's last use.
* **Data Storage:** This information (app name, last timestamp) is stored **only locally** in the app's database (`AppDatabase`) on your device.

### 3. App List (`QUERY_ALL_PACKAGES`)

* **Purpose:** Required to show you a complete list of all installed applications that can be managed by the firewall.
* **Data Storage:** This list is only used at runtime and in the local database (see above).

### 4. Foreground Service (`FOREGROUND_SERVICE`)

* **Purpose:** This is a technical requirement by Android. For the `VpnService` (the firewall) to run reliably in the background, it must be declared as a foreground service with a persistent notification.

## Our "Zero Data" Promise (Technical Proof)

We don't just claim to collect no data; we have technically ensured it:

1.  **No Trackers or Ad SDKs:** The app contains absolutely no third-party libraries for tracking, advertising, or analytics. This is evident in the build file (`app/build.gradle.kts`).
2.  **No Cloud Backups:** We have **explicitly disabled** Android's automatic cloud backup for the app's data (which contains your usage patterns). Even if you use Google backups, SilentPort's data will not be uploaded to the cloud.
3.  **No Network Permission (except for VPN):** The app itself (aside from the VPN service) does not request internet permission to send data.

...
## Open Source and Transparency

SilentPort is fully open source under the GPL license. Anyone can review the source code to independently verify all claims made here.

**Our Pledge: This project is non-commercial. We will never track you, sell your data, or monetize this app. Ever.**

**In summary: We only read the minimum necessary data (app list, last timestamp) to fulfill the core function. All of this data never leaves your device.**
