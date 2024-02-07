#!/bin/bash

if [ ! -f "Server.class" ]; then
    echo "Error: Server.class file not found. Please compile the code using compile.sh before running."
    exit 1
fi

java Server

if [ $? -eq 0 ]; then
    echo "Server started successfully."
else
    echo "Failed to start the server. Please check for errors."
fi
