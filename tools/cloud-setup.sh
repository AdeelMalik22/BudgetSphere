#!/usr/bin/env bash
set -euo pipefail
# Existing isolated checkout: no worktree, resets, source rewriting, or secret values.
_budget_repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
_budget_tools="${BUDGETSPHERE_TOOLCHAIN_DIR:-/workspace/toolchains}"
mkdir -p "$_budget_tools"
if [[ ! -x "$_budget_tools/jdk-21.0.8+9/bin/javac" ]]; then
    curl -fsSL 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz' -o "$_budget_tools/jdk.tar.gz"
    printf 'f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219  %s\n' "$_budget_tools/jdk.tar.gz" | sha256sum -c -
    tar -xzf "$_budget_tools/jdk.tar.gz" -C "$_budget_tools"
fi
mkdir -p "$_budget_tools/android-user" "$_budget_tools/gradle-home/init.d"
# Extend the JVM truststore with the machine's already-installed trusted CAs.
if [[ ! -f "$_budget_tools/cacerts" ]]; then
    cp "$_budget_tools/jdk-21.0.8+9/lib/security/cacerts" "$_budget_tools/cacerts"
    for _budget_cert in /usr/local/share/ca-certificates/*.crt; do
        [[ -f "$_budget_cert" ]] || continue
        "$_budget_tools/jdk-21.0.8+9/bin/keytool" -importcert -noprompt -trustcacerts -alias "budgetsphere-$(basename "$_budget_cert")" -file "$_budget_cert" -keystore "$_budget_tools/cacerts" -storepass changeit
    done
fi
source "$_budget_repo/tools/cloud-env.sh"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$_budget_tools/android.zip"
    printf '7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee  %s\n' "$_budget_tools/android.zip" | sha256sum -c -
    _budget_stage="$(mktemp -d "$_budget_tools/cmdline.XXXXXX")"
    unzip -q "$_budget_tools/android.zip" -d "$_budget_stage"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    mv "$_budget_stage/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rmdir "$_budget_stage"
fi
# These files live outside the repository; retain unrelated user Gradle properties.
python - <<'PY'
import os
from pathlib import Path
from urllib.parse import urlparse
root = Path(os.environ['GRADLE_USER_HOME']).parent
properties = Path(os.environ['GRADLE_USER_HOME']) / 'gradle.properties'
lines = properties.read_text().splitlines() if properties.exists() else []
updates = {'systemProp.javax.net.ssl.trustStore': str(root / 'cacerts'),
           'systemProp.javax.net.ssl.trustStorePassword': 'changeit'}
proxy = urlparse(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy') or '')
if proxy.hostname:
    for protocol in ('http', 'https'):
        updates[f'systemProp.{protocol}.proxyHost'] = proxy.hostname
        updates[f'systemProp.{protocol}.proxyPort'] = str(proxy.port or 80)
    updates['systemProp.http.nonProxyHosts'] = 'localhost|127.*'
kept = [line for line in lines if line.split('=', 1)[0] not in updates]
properties.write_text('\n'.join(kept + [f'{k}={v}' for k, v in updates.items()]) + '\n')
# Robolectric otherwise writes its download lock/cache into a read-only home.
init = Path(os.environ['GRADLE_USER_HOME']) / 'init.d' / 'budgetsphere-tests.gradle'
test_home = root / 'test-home'
test_home.mkdir(exist_ok=True)
args = {'user.home': str(test_home)}
if proxy.hostname:
    args.update({'https.proxyHost': proxy.hostname, 'https.proxyPort': str(proxy.port or 80),
                 'http.proxyHost': proxy.hostname, 'http.proxyPort': str(proxy.port or 80)})
def literal(s):
    return "'" + s.replace('\\', '\\\\').replace("'", "\\'") + "'"
init.write_text('allprojects { tasks.withType(Test).configureEach {\n' +
                '\n'.join('systemProperty ' + literal(k) + ', ' + literal(v) for k, v in args.items()) +
                '\n} }\n')
PY
_budget_proxy=()
read -r _budget_proxy_host _budget_proxy_port < <(python - <<'PY'
import os
from urllib.parse import urlparse
p=urlparse(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy') or '')
print(p.hostname or '-',p.port or 80)
PY
)
if [[ "$_budget_proxy_host" != '-' ]]; then
    _budget_proxy=(--proxy=http "--proxy_host=$_budget_proxy_host" "--proxy_port=$_budget_proxy_port")
fi
printf 'y\n%.0s' {1..100} > "$_budget_tools/licenses-input.txt"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" "${_budget_proxy[@]}" --licenses < "$_budget_tools/licenses-input.txt" > "$_budget_tools/sdk-licenses.log" 2>&1
rm "$_budget_tools/licenses-input.txt"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" "${_budget_proxy[@]}" 'platforms;android-36' 'build-tools;35.0.0' 'build-tools;36.0.0' 'platform-tools'
cd "$_budget_repo"
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
