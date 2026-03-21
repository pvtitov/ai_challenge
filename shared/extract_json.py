#!/usr/bin/env python3
import sys
import json

def extract_json(text):
    # Find all occurrences of '{'
    brace_indices = [i for i, char in enumerate(text) if char == '{']
    
    # Iterate backwards from the end of the string
    for i in range(len(text) - 1, -1, -1):
        if text[i] == '}':
            # For each '}', try to find a matching '{' from our list
            for start_index in brace_indices:
                if start_index > i:
                    continue
                
                substring = text[start_index : i+1]
                try:
                    # Try to parse the substring
                    parsed = json.loads(substring)
                    # If successful, we found our JSON
                    print(json.dumps(parsed))
                    return
                except json.JSONDecodeError:
                    # This substring was not valid JSON, try the next '{'
                    continue

if __name__ == "__main__":
    # Read the full input from stdin
    input_text = sys.stdin.read()
    extract_json(input_text)
