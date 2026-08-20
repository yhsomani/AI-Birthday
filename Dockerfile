FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_VERSION=24.18.0
ENV JAVA_VERSION=21
ENV RUBY_VERSION=3.4.10

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    git \
    build-essential \
    openjdk-${JAVA_VERSION}-jdk \
    libssl-dev \
    zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js using n
RUN curl -fsSL https://deb.nodesource.com/setup_24.x | bash - \
    && apt-get install -y nodejs \
    && npm install -g n \
    && n ${NODE_VERSION}

# Install Ruby using rbenv
RUN git clone https://github.com/rbenv/rbenv.git ~/.rbenv \
    && echo 'export PATH="$HOME/.rbenv/bin:$PATH"' >> ~/.bashrc \
    && echo 'eval "$(rbenv init -)"' >> ~/.bashrc \
    && git clone https://github.com/rbenv/ruby-build.git ~/.rbenv/plugins/ruby-build \
    && ~/.rbenv/bin/rbenv install ${RUBY_VERSION} \
    && ~/.rbenv/bin/rbenv global ${RUBY_VERSION}

ENV PATH="/root/.rbenv/shims:/root/.rbenv/bin:${PATH}"

WORKDIR /app
COPY . .

RUN npm ci

CMD ["npm", "run", "check"]
