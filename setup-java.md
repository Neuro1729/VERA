# Setup: Java 21 + Maven (Windows)

This project needs **JDK 21** and **Maven 3.9+**. App libraries come from `pom.xml` via Maven — do not install Spring Boot by hand.

## 1. Install JDK 21 (Temurin)

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

Confirm the install folder (version suffix may differ):

```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium"
```

Set `JAVA_HOME` to that folder (example for `21.0.12.8`):

```powershell
[System.Environment]::SetEnvironmentVariable(
  "JAVA_HOME",
  "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot",
  "User"
)
```

Close and reopen PowerShell, then verify:

```powershell
echo $env:JAVA_HOME
& "$env:JAVA_HOME\bin\java.exe" -version
```

Expected: OpenJDK **21.x** (Temurin).

> Note: plain `java -version` may still show another JDK (e.g. Oracle 23) if it appears earlier on `PATH`. Maven uses `JAVA_HOME`, which is what this project needs.

## 2. Install Maven (manual — not on winget)

```powershell
$ver = "3.9.16"
$url = "https://dlcdn.apache.org/maven/maven-3/$ver/binaries/apache-maven-$ver-bin.zip"
$zip = "$env:TEMP\apache-maven-$ver-bin.zip"
$dest = "C:\Tools"

New-Item -ItemType Directory -Force -Path $dest | Out-Null
Invoke-WebRequest -Uri $url -OutFile $zip
Expand-Archive -Path $zip -DestinationPath $dest -Force

$mavenHome = "C:\Tools\apache-maven-$ver"
[System.Environment]::SetEnvironmentVariable("MAVEN_HOME", $mavenHome, "User")

$userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
if ($userPath -notlike "*$mavenHome\bin*") {
  [System.Environment]::SetEnvironmentVariable("Path", "$userPath;$mavenHome\bin", "User")
}
```

Close and reopen PowerShell, then verify:

```powershell
echo $env:MAVEN_HOME
mvn -version
```

Expected:

```text
Apache Maven 3.9.16
Java version: 21.0.12, vendor: Eclipse Adoptium
```

## 3. Build and run this project

```powershell
cd C:\Users\aarya\Java_Project\resource-entitlement-engine
mvn clean install
mvn spring-boot:run
```

## Verified working config

| Variable     | Value                                                          |
|--------------|----------------------------------------------------------------|
| `JAVA_HOME`  | `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`      |
| `MAVEN_HOME` | `C:\Tools\apache-maven-3.9.16`                                 |
| Java         | 21.0.12 (Eclipse Adoptium)                                     |
| Maven        | 3.9.16                                                         |
