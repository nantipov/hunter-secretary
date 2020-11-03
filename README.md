# hunter-secretary
Incoming messages organization and auto-responding tool

## Build
```shell script
./gradlew clean build
```

## Environment variables
| Name | Default | Description |
|:-----|:--------|:------------|
| CLIENT_SECRET_FILE | | Path to the google secret file |
| GMAIL_DRAFT_MODE | false | Boolean value (`true` / `false`) indicating if Gmail draft mode is enabled by default |
| SCANNER_PERIOD | PT10M | Expression on how often do the scanning routine | 
