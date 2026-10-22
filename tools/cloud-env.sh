#!/usr/bin/env bash
# Source this file in each new cloud task. Never changes HOME or credentials.
_budget_env_tools="${BUDGETSPHERE_TOOLCHAIN_DIR:-/workspace/toolchains}"
export JAVA_HOME="$_budget_env_tools/jdk-21.0.8+9"
export ANDROID_HOME="$_budget_env_tools/android-sdk"
export ANDROID_USER_HOME="$_budget_env_tools/android-user"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
export GRADLE_USER_HOME="$_budget_env_tools/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
if [[ -f "$_budget_env_tools/cacerts" ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=$_budget_env_tools/cacerts -Djavax.net.ssl.trustStorePassword=changeit -Dandroid.user.home=$ANDROID_USER_HOME"
fi
unset _budget_env_tools
