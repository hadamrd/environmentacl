FROM python:3.11-slim

RUN pip install --no-cache-dir ansible ansible-core

# Add any additional tools you need
RUN apt-get update && apt-get install -y git openssh-client && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace