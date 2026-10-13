# Launches a RuneLite dev client with the plugin loaded.
# Uses the portable JDK 17 in the scratch folder (your system Java is 8, which is too old),
# and the Windows certificate store so Gradle can download through your network's HTTPS inspection.
$env:JAVA_HOME = "$PSScriptRoot\..\jdk17\jdk-17.0.20.1+1"
$env:JAVA_TOOL_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
& "$PSScriptRoot\gradlew.bat" run
