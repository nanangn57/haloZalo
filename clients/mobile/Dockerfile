FROM node:22-bookworm

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${PATH}:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/cmdline-tools/latest/bin"

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk \
    curl unzip git bash \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install Android command-line tools
ARG CMDLINE_TOOLS_REVISION=15859902

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_REVISION}_latest.zip" \
      -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/android-tools \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools/latest" \
    && mv /tmp/android-tools/cmdline-tools/* \
      "${ANDROID_HOME}/cmdline-tools/latest/" \
    && rm -rf /tmp/android-tools /tmp/cmdline-tools.zip

ARG ANDROID_PLATFORM=36
ARG ANDROID_BUILD_TOOLS=36.0.0

RUN yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses \
    && sdkmanager --sdk_root="${ANDROID_HOME}" \
       "platform-tools" \
       "platforms;android-${ANDROID_PLATFORM}" \
       "build-tools;${ANDROID_BUILD_TOOLS}"

RUN mkdir -p \
    /workspace/node_modules \
    /home/node/.npm \
    /home/node/.gradle \
    && chown -R node:node \
       /workspace \
       /home/node/.npm \
       /home/node/.gradle \
       "${ANDROID_HOME}"

USER node

WORKDIR /workspace

CMD ["bash"]