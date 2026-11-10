# Item Report

Android app for recording items with photos and exporting the list as a PDF or Excel report.

## What each item records

| Field | Notes |
| --- | --- |
| Unique ID | Generated in sequence per report (0001, 0002, ...). Each report can have its own prefix, e.g. `WH-0001`. |
| Photo | Camera or gallery. Optional date and time printed on the photo. |
| Item name | Required. |
| Short description | |
| Area it was used in | |
| Quantity | |
| Dimensions | Length x Width x Height, with unit. |
| Weight | With unit. |
| Storage location | Aisle, rack, shelf. |
| Condition | With the date and time it was recorded. |

## Export

Open a report and tap **Export**:

- **PDF, compact** - about three items per A4 page.
- **PDF, large photos** - one item per page.
- **Excel (.xlsx)** - one row per item with the photo in the row.

Photos are embedded at full resolution (the original JPEG bytes, not a re-compressed copy), so
zooming into the PDF or enlarging the picture in Excel shows full detail. The file can then be
shared, opened, or saved to the phone.

## Getting the APK

Every push to `main` builds the app with GitHub Actions and publishes it under **Releases**.
Download `ItemReport.apk` from the latest release on the phone and open it to install
(Android will ask to allow installs from the browser the first time).

All builds are signed with the key in `keystore/`, so a newer APK installs over an older one and
keeps the saved reports.

## Building locally

Open the folder in Android Studio, or run `./gradlew assembleRelease`.
Requires JDK 17. Minimum Android version: 8.0.
