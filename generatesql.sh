#!/bin/bash
# Path to your CSV file
CSV_FILE="20240203_155429_checked.csv"

# Path to the output file
outputFile="$(date +"%Y%m%d_%H%M%S")_output.sql"

# Ensure the output file is empty before starting to append data
> "$outputFile"

# Skip the header row and process each line
tail -n +2 "$CSV_FILE" | while IFS=, read -r social_network name
do
  # Generate a UUID for each row. Adjust based on your system's uuid generation command
  ID=$(uuidgen)

  # Capitalize the first letter of social_network using awk
  social_network=$(echo "$social_network" | awk '{print toupper(substr($0, 1, 1)) tolower(substr($0, 2))}')

  # Print the INSERT statement and append to the output file
  echo "INSERT INTO influencers (id, social_network, name) VALUES ('$ID', '$social_network', '$name');" >> "$outputFile"
done
