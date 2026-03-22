#!/bin/bash

# This script trims leading and trailing text around the first '{' and last '}'
# to extract a JSON-like object from a text stream. It reads from standard input.

# Read all of stdin into a single variable.
input_text=$(cat -)

# In case the input does not contain '{', the following expansion will do nothing.
# We need to find the first '{', so we use the non-greedy '#'.
temp_text="${input_text#*\{}"

# If the expansion did nothing, it means '{' was not found.
if [ "$input_text" = "$temp_text" ]; then
  # No opening brace found, so we output nothing.
  exit 0
fi

# Re-add the brace at the beginning.
temp_text="{$temp_text"

# Now, find the last '}' and remove everything after it.
# We need to find the last '}', so we use the non-greedy '%'.
final_text="${temp_text%\}*}"

# If the expansion did nothing, it means '}' was not found after the first '{'.
if [ "$temp_text" = "$final_text" ]; then
  # No closing brace found, so we output nothing.
  exit 0
fi

# Re-add the brace at the end.
final_text="$final_text}"

echo "$final_text"
