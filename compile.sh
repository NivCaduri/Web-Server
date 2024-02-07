#!/bin/bash

if [ ! -f "config.ini" ]; then
    echo "Error: config.ini file not found. Please make sure it exists in the same directory as compile.sh."
    exit 1
fi

javac Server.java

if [ $? -eq 0 ]; then
    echo "Compilation successful."
else
    echo "Compilation failed. Please check for errors in your code."
fi
