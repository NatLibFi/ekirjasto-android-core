# Additional strings

This directory contains strings that should be localized,
but which are defined in modules outside of this repository.

For example, the e-book reader uses UI strings from here:
https://github.com/ThePalaceProject/android-r2/blob/main/org.librarysimplified.r2.views/src/main/res/values/sr2_strings.xml

... but we haven't forked that repository (at least not yet), so those strings are not localized.

Copying these "outside module" strings into this directory will make Transifex pick them up from here,
they'll be pushed for localization, and then they will appear in Transifex's txstrings.json files.

The strings will still be fetched through TxNative's localization functions (overrides of Android's getString(), etc),
so they will be localized as long as they appear in Transifex's txstrings.json files.

