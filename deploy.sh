#!/bin/bash

set -e
echo $VALTECH_KEYSTORE | base64 --decode -o valtech.keystore
set +e
./gradlew assembleRelease
rm -f valtech.keystore
set -e
cp app/build/apk/app-release.apk $CIRCLE_ARTIFACTS