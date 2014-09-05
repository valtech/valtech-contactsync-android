#!/bin/bash

# This script installs the required SDK components for
# building on CircleCI.

export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$PATH"
DEPS="$ANDROID_HOME/installed-dependencies"

if [ ! -e $DEPS ]; then
  cp -r /usr/local/android-sdk-linux $ANDROID_HOME &&
  ln -s $ANDROID_HOME/build-tools/20.0.0/zipalign $ANDROID_HOME/tools/zipalign &&
  echo y | android update sdk -u -a -t android-20 &&
  echo y | android update sdk -u -a -t platform-tools &&
  echo y | android update sdk -u -a -t build-tools-20.0.0 &&
  ./gradlew tasks && # trigger gradle wrapper download so it is stored in cache
  touch $DEPS
fi
