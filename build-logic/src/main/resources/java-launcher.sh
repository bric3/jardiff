#!/bin/sh
# SPDX-License-Identifier: MPL-2.0

minimum_java_version=@MINIMUM_JAVA_VERSION@

# Discovery and shim execution must never install a missing runtime.
export MISE_AUTO_INSTALL=0 MISE_OFFLINE=1

try_java() {
    java_executable=$1
    shift
    [ -x "$java_executable" ] || return 1

    java_version=$("$java_executable" -version 2>&1) || return 1
    java_version=$(printf '%s\n' "$java_version" |
        sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)
    case "$java_version" in
        1.*) java_version=${java_version#1.} ;;
    esac
    java_major=${java_version%%[!0-9]*}
    case "$java_major" in
        ''|*[!0-9]*) return 1 ;;
    esac
    [ "$java_major" -ge "$minimum_java_version" ] || return 1

    case "$java_executable" in
        */bin/java)
            JAVA_HOME=${java_executable%/bin/java}
            export JAVA_HOME
            ;;
    esac
    # JAVA_OPTS intentionally supplies separate JVM arguments; application arguments stay quoted.
    # shellcheck disable=SC2086
    exec "$java_executable" ${JAVA_OPTS:-} -jar "$0" "$@"
}

try_java_home() {
    candidate_home=$1
    shift
    [ -n "$candidate_home" ] || return 1
    try_java "$candidate_home/bin/java" "$@"
    try_java "$candidate_home/Contents/Home/bin/java" "$@"
}

if command -v mise >/dev/null 2>&1; then
    try_java_home "$(mise where java 2>/dev/null)" "$@"
    try_java_home "$(mise where "java@$minimum_java_version" 2>/dev/null)" "$@"
    # Only installed versions are listed; resolve each path through mise.
    for installed_version in $(mise ls --installed --no-header java 2>/dev/null |
        sed -n 's/^java[[:space:]][[:space:]]*\([^[:space:]]*\).*/\1/p'); do
        try_java_home "$(mise where "java@$installed_version" 2>/dev/null)" "$@"
    done
fi

# SDKMAN! is a shell function; inspect its installed candidates without sourcing shell profiles.
sdkman_java=${SDKMAN_CANDIDATES_DIR:-${SDKMAN_DIR:-"$HOME/.sdkman"}/candidates}/java
try_java_home "$sdkman_java/current" "$@"
for candidate in "$sdkman_java"/*; do
    try_java_home "$candidate" "$@"
done

if command -v asdf >/dev/null 2>&1; then
    try_java_home "$(asdf where java 2>/dev/null)" "$@"
fi
for candidate in "${ASDF_DATA_DIR:-"$HOME/.asdf"}/installs/java"/*; do
    try_java_home "$candidate" "$@"
done

if command -v jenv >/dev/null 2>&1; then
    try_java_home "$(jenv prefix 2>/dev/null)" "$@"
fi
for candidate in "${JENV_ROOT:-"$HOME/.jenv"}/versions"/*; do
    try_java_home "$candidate" "$@"
done

for candidate in "${JABBA_HOME:-"$HOME/.jabba"}/jdk"/*; do
    try_java_home "$candidate" "$@"
done

try_java_home "${JAVA_HOME:-}" "$@"
try_java "$(command -v java 2>/dev/null)" "$@"

printf 'No Java %s+ installation found. Checked mise, SDKMAN!, asdf-vm, jenv, jabba, JAVA_HOME and PATH.\n' \
    "$minimum_java_version" >&2
exit 1
